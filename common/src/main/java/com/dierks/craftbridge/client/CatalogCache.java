package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.LinkProtocol;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The server's list of custom items, kept on disk between sessions.
 *
 * <p>JEI decides what its item list and its subtypes are when it loads its plugins, which can
 * happen before the server has told us anything. Rather than have the catalog miss that window
 * and silently do nothing, it is written down as it arrives and read back at plugin load, per
 * server. The practical effect is that a brand new custom item shows up in JEI's list from the
 * next JEI reload (or the next launch) rather than the instant the server sends it.
 */
public final class CatalogCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CatalogCache() {
    }

    private static Path file() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        String name = server == null ? "local" : server.ip;
        StringBuilder safe = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            safe.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("craftbridge-client").resolve(safe + ".catalog");
    }

    /** Keep the payload exactly as it arrived: it carries its own protocol version. */
    static void store(byte[] payload, int entries) {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.write(path, payload);
            LOGGER.info("CraftBridge: stored {} custom item(s) for JEI at {}", entries, path);
        } catch (IOException e) {
            LOGGER.warn("CraftBridge: could not store the item catalog: {}", e.toString());
        }
    }

    /** The catalog's items, decoded, or an empty list when there is none we can read. */
    public static List<ItemStack> stacks() {
        RegistryAccess registries = ClientRegistries.current();
        if (registries == null) {
            return List.of();
        }
        byte[] payload;
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) {
                return List.of();
            }
            payload = Files.readAllBytes(path);
        } catch (IOException e) {
            LOGGER.warn("CraftBridge: could not read the item catalog: {}", e.toString());
            return List.of();
        }
        try {
            List<ItemStack> stacks = new ArrayList<>();
            for (LinkProtocol.CatalogEntry entry : LinkProtocol.decodeItemCatalog(payload).entries()) {
                ItemStack stack = ItemBlobs.decode(entry.item(), registries);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            return List.copyOf(stacks);
        } catch (RuntimeException e) {
            // Written by another version of the protocol: ignore it, the server will resend.
            LOGGER.info("CraftBridge: ignoring a stored item catalog this version cannot read ({})", e.toString());
            return List.of();
        }
    }
}
