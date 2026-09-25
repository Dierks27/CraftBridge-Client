package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.LinkProtocol;
import com.dierks.craftbridge.link.SnapshotTracker;
import com.dierks.craftbridge.link.VarInts;
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
    /**
     * Storage messages in a row this client may fail to read, each answered with a request for
     * a fresh snapshot, before it stops asking for this session rather than loop on a message
     * it will never be able to read.
     */
    private static final int STORAGE_FAILURES_BEFORE_GIVING_UP = 3;

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
    /** Set once the panel has drawn and the server has been told; reset when the view goes away. */
    private boolean drawnAcked;
    private int storageFailures;
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

    /**
     * Stop talking to this server for the rest of the connection: it speaks a protocol version
     * this client does not. If the server had been told the panel is showing, it is told first
     * that it is not, so it gives the player back the phantom slots instead of leaving them with
     * neither.
     */
    private void goDormant() {
        panelHidden();
        forget();
    }

    private void forget() {
        sender = null;
        pluginVersion = null;
        phantomSlotsOff = false;
        sessionLive = false;
        drawnAcked = false;
        storageFailures = 0;
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
     * One entry point for every channel, so a payload this version cannot read never throws
     * into the client's packet handling.
     *
     * <p>Only a plugin that speaks another protocol version puts the mod to sleep: nothing it
     * sends can be trusted to mean what this side thinks. Any other message that fails to read
     * — one odd item, a truncated payload — costs that message and nothing more. A storage
     * message is then asked for again as a fresh snapshot, because the view has missed an
     * update, and the link stays up.
     */
    public void receive(String channel, byte[] payload) {
        int version = versionOf(payload);
        if (version >= 0 && version != LinkProtocol.VERSION) {
            LOGGER.warn("CraftBridge: the server speaks link protocol version {} on {}, this client {};"
                    + " going dormant. Update whichever is older.", version, channel, LinkProtocol.VERSION);
            goDormant();
            return;
        }
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
            LOGGER.warn("CraftBridge: could not read a {} payload, dropping it: {}", channel, e.toString());
            if (LinkProtocol.CHANNEL_STORAGE.equals(channel)) {
                storageUnreadable();
            }
        }
    }

    /** The protocol version a payload starts with, or -1 when there is not even that. */
    private static int versionOf(byte[] payload) {
        try {
            return new VarInts.Reader(payload).readVarInt();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * A storage message could not be read, so the view has missed an update. Ask for the whole
     * thing again — a few times. A server that keeps sending something this client cannot read
     * would otherwise be asked forever; after that the panel is put away for this session and
     * the server is told, so the player falls back to the phantom slots.
     */
    private void storageUnreadable() {
        storageFailures++;
        if (storageFailures <= STORAGE_FAILURES_BEFORE_GIVING_UP) {
            requestResync();
            return;
        }
        LOGGER.warn("CraftBridge: {} storage messages in a row could not be read; not showing storage"
                + " for this session", storageFailures);
        panelHidden();
        sessionLive = false;
        storage.clear();
    }

    private void onServerHello(LinkProtocol.ServerHello hello) {
        pluginVersion = hello.pluginVersion();
        phantomSlotsOff = hello.phantomSlotsOff();
        LOGGER.info("CraftBridge: server plugin {} (mod {}); phantom slots {}",
                pluginVersion, modVersion, phantomSlotsOff ? "off for us" : "on");
    }

    private void onStorage(LinkProtocol.Storage message) {
        LOGGER.info("CraftBridge: storage {} #{} with {} entr{}",
                message.full() ? "snapshot" : "delta", message.sequence(), message.entries().size(),
                message.entries().size() == 1 ? "y" : "ies");
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
        storageFailures = 0;
        LOGGER.info("CraftBridge: {} item type(s) in range", storage.all().size());
    }

    /**
     * Called by the storage panel once it has actually drawn a frame.
     *
     * <p>This, and not the arrival of a snapshot, is what tells the server it may take the
     * player's phantom slots away. Having the data is not the same as showing it: the first
     * release acknowledged on receipt, had nothing to draw, and left the player with nothing
     * at all. So the acknowledgement waits for pixels.
     */
    public void panelDrew() {
        if (drawnAcked || !sessionLive) {
            return;
        }
        drawnAcked = true;
        send(LinkProtocol.CHANNEL_STORAGE_ACK,
                LinkProtocol.encode(new LinkProtocol.StorageAck(tracker.sequence(), true)));
        LOGGER.info("CraftBridge: storage panel is drawing; told the server it can drop the phantom slots");
    }

    /**
     * Called by the storage panel when it has been drawing and now cannot: the screen became
     * too narrow, or something else took the room it needs.
     *
     * <p>The server took the phantom slots away because the panel was showing. If it is not
     * showing any more, the server hears that too and gives them back, so the player is never
     * left seeing neither. The panel says it is drawing again whenever it next draws a frame.
     */
    public void panelHidden() {
        if (!drawnAcked) {
            return;
        }
        drawnAcked = false;
        send(LinkProtocol.CHANNEL_STORAGE_ACK,
                LinkProtocol.encode(new LinkProtocol.StorageAck(tracker.sequence(), false)));
        LOGGER.info("CraftBridge: storage panel can no longer be shown; told the server to keep the phantom slots");
    }

    private void onSessionEnd(LinkProtocol.SessionEnd end) {
        LOGGER.info("CraftBridge: storage view closed ({})", end.reason());
        sessionLive = false;
        drawnAcked = false;
        storageFailures = 0;
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
        LOGGER.info("CraftBridge: item catalog, {} bytes, {} custom item(s): {}",
                payload.length, catalog.size(), describe(catalog));
        CatalogCache.store(payload, catalog.size());
    }

    /** The first few names in a catalog, so the log says what arrived and not only how much. */
    private static String describe(List<LinkProtocol.CatalogEntry> entries) {
        StringBuilder names = new StringBuilder("[");
        for (int i = 0; i < entries.size() && i < 20; i++) {
            names.append(i == 0 ? "" : ", ").append(entries.get(i).displayName());
        }
        return names.append(entries.size() > 20 ? ", ...]" : "]").toString();
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

    /**
     * The player clicked an item in the panel. The click is named, not the amount: the server
     * decides how much from what is in range and how much room the player has, exactly as it
     * does for a phantom slot.
     *
     * @param mode {@code ONE}, {@code HALF} or {@code ALL}
     */
    public boolean requestPull(byte[] item, String mode, TransferOutcome outcome) {
        if (sender == null || !sessionLive()) {
            return false;
        }
        int requestId = nextRequestId++;
        pending.put(requestId, new Pending(outcome, tick + RESULT_TIMEOUT_TICKS));
        send(LinkProtocol.CHANNEL_PULL_REQUEST,
                LinkProtocol.encode(new LinkProtocol.PullRequest(requestId, item, mode)));
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
