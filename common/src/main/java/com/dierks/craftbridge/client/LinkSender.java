package com.dierks.craftbridge.client;

/**
 * How this loader sends a CraftBridge payload to the server. Fabric and NeoForge register
 * their own payload types and hand one of these to {@link CraftBridgeClient} on join, which
 * is the only loader-specific thing the shared half of the mod needs.
 */
@FunctionalInterface
public interface LinkSender {
    void send(String channel, byte[] payload);
}
