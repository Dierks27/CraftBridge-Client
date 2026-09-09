package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.LinkProtocol;
import com.dierks.craftbridge.link.SnapshotTracker;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the mod knows about the server it is talking to. One instance, driven from the
 * client thread by whichever loader is in charge of the networking.
 *
 * <p>The mod is dormant until a CraftBridge server answers its hello: on a vanilla, Spigot or
 * any other server, nothing here ever becomes live, {@link #sessionLive()} stays false and JEI
 * behaves exactly as it does without the mod installed.
 */
public final class CraftBridgeClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long to wait for the server's verdict on a transfer before telling JEI it failed. */
    private static final int RESULT_TIMEOUT_TICKS = 200;
    /** The hello waits a moment: a Paper server declares its channels shortly after we join. */
    private static final int FIRST_HELLO_TICKS = 20;
    private static final int HELLO_RETRY_TICKS = 60;
    private static final int HELLO_ATTEMPTS = 3;

    private static final CraftBridgeClient INSTANCE = new CraftBridgeClient();

    public static CraftBridgeClient get() {
        return INSTANCE;
    }

    /** Told when the server has ruled on a transfer request, or when it never answered. */
    @FunctionalInterface
    public interface TransferOutcome {
        void completed(boolean ok, String message);
    }

    private record Pending(TransferOutcome outcome, int deadline) {
    }

    private final SnapshotTracker tracker = new SnapshotTracker();
    private final StorageView storage = new StorageView();
    private final Map<Integer, Pending> pending = new LinkedHashMap<>();

    private LinkSender sender;
    private String modVersion = "dev";
    private String pluginVersion;
    private boolean phantomSlotsOff;
    private boolean sessionLive;
    private int nextRequestId = 1;
    private int tick;
    private int helloAttemptsLeft;
    private int nextHelloTick;
    private List<LinkProtocol.CatalogEntry> catalog = List.of();

    private CraftBridgeClient() {
    }

    // ---- connection ------------------------------------------------------------------

    /**
     * Start saying hello. A server that does not answer simply leaves the mod dormant, which
     * is every server without CraftBridge on it.
     *
     * <p>The first hello waits a moment rather than going out on the join tick: a Bukkit server
     * announces the channels its plugins listen on just after the player joins, and a message
     * sent before that announcement can be dropped by the loader as unknown. It is retried a
     * couple of times, and then the mod stops asking.
     */
    public void connected(LinkSender sender, String modVersion) {
        forget();
        this.sender = sender;
        this.modVersion = modVersion;
        this.helloAttemptsLeft = HELLO_ATTEMPTS;
        this.nextHelloTick = tick + FIRST_HELLO_TICKS;
    }

    public void disconnected() {
        forget();
    }

    private void forget() {
        sender = null;
        pluginVersion = null;
        phantomSlotsOff = false;
        sessionLive = false;
        storage.clear();
        catalog = List.of();
        helloAttemptsLeft = 0;
        failAllPending("disconnected");
    }

    public boolean serverHasCraftBridge() {
        return pluginVersion != null;
    }

    /** True while a Linked Workbench session is open and the storage view can be trusted. */
    public boolean sessionLive() {
        return sessionLive && !tracker.needsSnapshot();
    }

    public boolean phantomSlotsOff() {
        return phantomSlotsOff;
    }

    public StorageView storage() {
        return storage;
    }

    public List<LinkProtocol.CatalogEntry> catalog() {
        return catalog;
    }

    // ---- incoming --------------------------------------------------------------------

    /**
     * One entry point for every channel, so a payload this version cannot read — an older or
     * newer plugin, a truncated message — puts the mod back to sleep instead of throwing into
     * the client's packet handling.
     */
    public void receive(String channel, byte[] payload) {
        try {
            switch (channel) {
                case LinkProtocol.CHANNEL_HELLO -> onServerHello(LinkProtocol.decodeServerHello(payload));
                case LinkProtocol.CHANNEL_STORAGE -> onStorage(LinkProtocol.decodeStorage(payload));
                case LinkProtocol.CHANNEL_TRANSFER_RESULT ->
                        onTransferResult(LinkProtocol.decodeTransferResult(payload));
                case LinkProtocol.CHANNEL_SESSION_END -> onSessionEnd(LinkProtocol.decodeSessionEnd(payload));
                case LinkProtocol.CHANNEL_ITEM_CATALOG -> onItemCatalog(payload);
                default -> LOGGER.debug("CraftBridge: ignoring unknown channel {}", channel);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("CraftBridge: could not read a {} payload, going dormant: {}", channel, e.toString());
            forget();
        }
    }

    private void onServerHello(LinkProtocol.ServerHello hello) {
        pluginVersion = hello.pluginVersion();
        phantomSlotsOff = hello.phantomSlotsOff();
        LOGGER.info("CraftBridge: server plugin {} (mod {}); phantom slots {}",
                pluginVersion, modVersion, phantomSlotsOff ? "off for us" : "on");
    }

    private void onStorage(LinkProtocol.Storage message) {
        if (!tracker.apply(message)) {
            // A gap in the deltas. Applying it would leave a view that lies about counts and
            // makes JEI offer a craft the server will refuse, so ask for the whole thing.
            LOGGER.debug("CraftBridge: storage delta {} does not follow {}, resyncing",
                    message.sequence(), tracker.sequence());
            requestResync();
            return;
        }
        RegistryAccess registries = ClientRegistries.current();
        if (registries == null) {
            return; // between worlds: the next snapshot will rebuild it
        }
        storage.rebuild(tracker.counts(), registries);
        sessionLive = true;
    }

    private void onSessionEnd(LinkProtocol.SessionEnd end) {
        sessionLive = false;
        storage.clear();
        failAllPending(end.reason());
    }

    private void onTransferResult(LinkProtocol.TransferResult result) {
        Pending waiting = pending.remove(result.requestId());
        if (waiting != null) {
            waiting.outcome().completed(result.ok(), result.message());
        }
    }

    private void onItemCatalog(byte[] payload) {
        catalog = LinkProtocol.decodeItemCatalog(payload).entries();
        CatalogCache.store(payload, catalog.size());
    }

    // ---- outgoing --------------------------------------------------------------------

    public void requestResync() {
        send(LinkProtocol.CHANNEL_RESYNC, LinkProtocol.encode(new LinkProtocol.Resync(tracker.sequence())));
    }

    /**
     * Ask the server to fill the crafting grid. The client says only which recipe it wants and
     * what the slots would accept: it never says what it has or how much, because the server
     * does not trust it and works that out itself.
     *
     * @return false when there is no server to ask
     */
    public boolean requestTransfer(String recipeId, List<LinkProtocol.SlotChoices> slots,
                                   boolean maxTransfer, TransferOutcome outcome) {
        if (sender == null) {
            return false;
        }
        int requestId = nextRequestId++;
        pending.put(requestId, new Pending(outcome, tick + RESULT_TIMEOUT_TICKS));
        send(LinkProtocol.CHANNEL_TRANSFER_REQUEST, LinkProtocol.encode(new LinkProtocol.TransferRequest(
                requestId, tracker.sequence(), maxTransfer, true, recipeId == null ? "" : recipeId, slots)));
        return true;
    }

    private void send(String channel, byte[] payload) {
        LinkSender to = sender;
        if (to == null) {
            return;
        }
        try {
            to.send(channel, payload);
        } catch (RuntimeException e) {
            // A server that never declared the channel, or a connection on its way down.
            LOGGER.debug("CraftBridge: could not send on {}: {}", channel, e.toString());
        }
    }

    // ---- ticking ---------------------------------------------------------------------

    /** Called every client tick, so a request the server never answers cannot hang JEI. */
    public void clientTick() {
        tick++;
        if (sender != null && pluginVersion == null && helloAttemptsLeft > 0 && tick - nextHelloTick >= 0) {
            helloAttemptsLeft--;
            nextHelloTick = tick + HELLO_RETRY_TICKS;
            send(LinkProtocol.CHANNEL_HELLO, LinkProtocol.encode(new LinkProtocol.ClientHello(modVersion)));
        }
        if (pending.isEmpty()) {
            return;
        }
        List<Pending> expired = new ArrayList<>();
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Pending waiting = it.next().getValue();
            if (tick - waiting.deadline() >= 0) {
                expired.add(waiting);
                it.remove();
            }
        }
        for (Pending waiting : expired) {
            waiting.outcome().completed(false, "The server did not answer.");
        }
    }

    private void failAllPending(String reason) {
        List<Pending> waiting = List.copyOf(pending.values());
        pending.clear();
        for (Pending one : waiting) {
            one.outcome().completed(false, reason);
        }
    }
}
