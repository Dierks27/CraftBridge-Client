package com.dierks.craftbridge.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Item stacks travel over the CraftBridge link as opaque blobs, written by whichever side is
 * sending with the game's own network codec for a slot's contents. That is the same encoding
 * vanilla uses for every packet that carries an item, so components — a custom name, the
 * plugin's persistent data, anything a later version adds — survive the round trip without
 * either side having to know what they mean.
 */
public final class ItemBlobs {

    private ItemBlobs() {
    }

    public static ItemStack decode(byte[] bytes, RegistryAccess registries) {
        ByteBuf raw = Unpooled.wrappedBuffer(bytes);
        try {
            return ItemStack.OPTIONAL_STREAM_CODEC.decode(new RegistryFriendlyByteBuf(raw, registries));
        } finally {
            raw.release();
        }
    }

    public static byte[] encode(ItemStack stack, RegistryAccess registries) {
        ByteBuf raw = Unpooled.buffer();
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(new RegistryFriendlyByteBuf(raw, registries), stack);
            byte[] out = new byte[raw.readableBytes()];
            raw.readBytes(out);
            return out;
        } finally {
            raw.release();
        }
    }
}
