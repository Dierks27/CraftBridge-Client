package com.dierks.craftbridge.client.neoforge;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.LinkPayload;
import com.dierks.craftbridge.client.ui.StoragePanel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (String channel : LinkPayload.TO_CLIENT) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            if (LinkPayload.TO_SERVER.contains(channel)) {
                registrar.playBidirectional(type, LinkPayload.codec(type), (payload, context) ->
                        context.enqueueWork(() ->
                                CraftBridgeClient.get().receive(payload.channel(), payload.data())));
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

    private static String modVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("dev");
    }
}
