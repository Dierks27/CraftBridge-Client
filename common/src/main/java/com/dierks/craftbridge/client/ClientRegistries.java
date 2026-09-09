package com.dierks.craftbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;

/** The registries item blobs are decoded against: the ones the current connection sent us. */
// Returns null when there is no connection (no world joined yet).
public final class ClientRegistries {

    private ClientRegistries() {
    }

    public static RegistryAccess current() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.registryAccess();
    }
}
