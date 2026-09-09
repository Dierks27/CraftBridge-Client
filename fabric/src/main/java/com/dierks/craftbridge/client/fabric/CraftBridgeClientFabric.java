package com.dierks.craftbridge.client.fabric;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.LinkPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Fabric half: register the payload types, and pump the connection's events into the
 * shared client. Nothing about what the mod does lives here.
 */
public final class CraftBridgeClientFabric implements ClientModInitializer {

    private static final String MOD_ID = "craftbridge_client";

    @Override
    public void onInitializeClient() {
        for (String channel : LinkPayload.TO_CLIENT) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            PayloadTypeRegistry.playS2C().register(type, LinkPayload.codec(type));
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    context.client().execute(() ->
                            CraftBridgeClient.get().receive(payload.channel(), payload.data())));
        }
        for (String channel : LinkPayload.TO_SERVER) {
            CustomPacketPayload.Type<LinkPayload> type = LinkPayload.typeOf(channel);
            PayloadTypeRegistry.playC2S().register(type, LinkPayload.codec(type));
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                CraftBridgeClient.get().connected(
                        (channel, data) -> ClientPlayNetworking.send(LinkPayload.of(channel, data)),
                        modVersion()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CraftBridgeClient.get().disconnected());
        ClientTickEvents.END_CLIENT_TICK.register(client -> CraftBridgeClient.get().clientTick());
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
