package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.LinkProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A CraftBridge message as the game's own plugin-message packet: the channel it belongs to,
 * and the bytes {@link LinkProtocol} framed. The framing is the shared contract with the
 * plugin, so nothing here interprets the payload — it is carried whole in both directions.
 */
public record LinkPayload(CustomPacketPayload.Type<LinkPayload> type, byte[] data) implements CustomPacketPayload {

    /** Channels the server sends us. */
    public static final List<String> TO_CLIENT = List.of(
            LinkProtocol.CHANNEL_HELLO,
            LinkProtocol.CHANNEL_STORAGE,
            LinkProtocol.CHANNEL_TRANSFER_RESULT,
            LinkProtocol.CHANNEL_SESSION_END,
            LinkProtocol.CHANNEL_ITEM_CATALOG);

    /** Channels we send the server. */
    public static final List<String> TO_SERVER = List.of(
            LinkProtocol.CHANNEL_HELLO,
            LinkProtocol.CHANNEL_RESYNC,
            LinkProtocol.CHANNEL_STORAGE_ACK,
            LinkProtocol.CHANNEL_TRANSFER_REQUEST);

    private static final Map<String, CustomPacketPayload.Type<LinkPayload>> TYPES = types();

    private static Map<String, CustomPacketPayload.Type<LinkPayload>> types() {
        Map<String, CustomPacketPayload.Type<LinkPayload>> types = new LinkedHashMap<>();
        for (String channel : TO_CLIENT) {
            types.put(channel, new CustomPacketPayload.Type<>(Identifier.parse(channel)));
        }
        for (String channel : TO_SERVER) {
            // craftbridge:hello travels both ways and keeps one type either way.
            types.computeIfAbsent(channel, id -> new CustomPacketPayload.Type<>(Identifier.parse(id)));
        }
        return Map.copyOf(types);
    }

    public static CustomPacketPayload.Type<LinkPayload> typeOf(String channel) {
        CustomPacketPayload.Type<LinkPayload> type = TYPES.get(channel);
        if (type == null) {
            throw new IllegalArgumentException("not a CraftBridge channel: " + channel);
        }
        return type;
    }

    public static LinkPayload of(String channel, byte[] data) {
        return new LinkPayload(typeOf(channel), data);
    }

    public String channel() {
        return type.id().toString();
    }

    public static StreamCodec<FriendlyByteBuf, LinkPayload> codec(CustomPacketPayload.Type<LinkPayload> type) {
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new LinkPayload(type, data);
                });
    }
}
