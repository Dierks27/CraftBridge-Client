package com.dierks.craftbridge.client.neoforge;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.LinkPayload;
import com.dierks.craftbridge.client.jei.CraftCount;
import com.dierks.craftbridge.client.ui.ChestSort;
import com.dierks.craftbridge.client.ui.StoragePanel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The NeoForge half: register the payload types, and pump the connection's events into the
 * shared client. Nothing about what the mod does lives here.
 *
 * <p>The channels are registered as optional, because the server on the other end is a Paper
 * server rather than a NeoForge one: it declares the channels its plugins listen on the vanilla
 * way, and a required payload would refuse the connection outright.
 */
@Mod(value = CraftBridgeClientNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class CraftBridgeClientNeoForge {

    public static final String MOD_ID = "craftbridge_client";

    public CraftBridgeClientNeoForge(IEventBus modBus) {
        modBus.addListener(CraftBridgeClientNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onClientTick);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onScreenRender);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onScreenClick);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onScreenRelease);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onScreenDrag);
        NeoForge.EVENT_BUS.addListener(CraftBridgeClientNeoForge::onScreenScroll);
        // Middle-click sorting at LOW: JEI's own listener (NORMAL) and the storage panel's get
        // the click first, and a click either of them cancelled never reaches this one.
        ChestSort.setHoveredSlotLookup(screen -> screen.getHoveredSlot());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ScreenEvent.MouseButtonPressed.Pre.class,
                CraftBridgeClientNeoForge::onScreenSortClick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (String channel : LinkPayload.TO_CLIENT) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            if (LinkPayload.TO_SERVER.contains(channel)) {
                // Both handlers, not one: the three-argument playBidirectional registers the
                // handler for the SERVERBOUND direction only and leaves the clientbound side
                // to RegisterClientPayloadHandlersEvent, which fails startup validation with
                // "clientbound payloads are missing client-side handlers".
                //
                // The serverbound side does fire, though, whenever this client hosts the world:
                // in singleplayer or on a LAN world the integrated server receives our own
                // hello. It must not reach the client code — that would run it on the server
                // thread and read our hello as a server's reply — so the handler only acts on
                // what arrives over the client's own connection to a server. One handler that
                // checks, in both places, so the answer does not depend on argument order.
                IPayloadHandler<LinkPayload> handler = (payload, context) -> {
                    Object listener = context.listener();
                    if (!(listener instanceof ClientPacketListener)) {
                        return; // received by the integrated server: not ours to handle
                    }
                    context.enqueueWork(() ->
                            CraftBridgeClient.get().receive(payload.channel(), payload.data()));
                };
                registrar.playBidirectional(type, LinkPayload.codec(type), handler, handler);
            } else {
                registrar.playToClient(type, LinkPayload.codec(type), (payload, context) ->
                        context.enqueueWork(() ->
                                CraftBridgeClient.get().receive(payload.channel(), payload.data())));
            }
        }
        for (String channel : LinkPayload.TO_SERVER) {
            if (LinkPayload.TO_CLIENT.contains(channel)) {
                continue; // already registered above, both ways
            }
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            registrar.playToServer(type, LinkPayload.codec(type), (payload, context) -> {
                // We only ever send on these; a server that sends one back is ignored.
            });
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CraftBridgeClient.get().connected(
                (channel, data) -> ClientPacketDistributor.sendToServer(LinkPayload.of(channel, data)),
                modVersion());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftBridgeClient.get().disconnected();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        CraftBridgeClient.get().clientTick();
    }

    /** The panel decides for itself whether this screen is a crafting menu with a live session. */
    private static void onScreenRender(ScreenEvent.Render.Post event) {
        StoragePanel.render(event.getScreen(), event.getGuiGraphics());
    }

    /**
     * Cancelling stops the screen underneath from also seeing a click the panel took, or a
     * right-click on JEI's [+] that opened the craft-count prompt.
     */
    private static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (StoragePanel.click(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())
                || CraftCount.click(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    /** A middle-click over a slot, when the server said it will sort: a sort request instead. */
    private static void onScreenSortClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ChestSort.click(event.getScreen(), event.getMouseButtonEvent())) {
            event.setCanceled(true);
        }
    }

    /**
     * And the release of that click: a container screen treats a release outside its window as
     * "drop what is on the cursor", and the panel is outside its window. A sort's release is
     * kept from the screen the same way.
     */
    private static void onScreenRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (StoragePanel.release(event.getScreen(), event.getButton())
                || ChestSort.release(event.getScreen(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onScreenDrag(ScreenEvent.MouseDragged.Pre event) {
        if (StoragePanel.dragging(event.getScreen(), event.getMouseButton())) {
            event.setCanceled(true);
        }
    }

    /**
     * The mouse wheel over the panel scrolls the panel, and over JEI's [+] sets the craft count,
     * rather than the screen underneath scrolling.
     */
    private static void onScreenScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (StoragePanel.scroll(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())
                || CraftCount.scroll(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static String modVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("dev");
    }
}
