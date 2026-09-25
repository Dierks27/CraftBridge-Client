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
 * <p>JEI decides what its item list and its subtypes are when it loads its plugins, and on
 * joining a server it does so before the server has answered our hello, so before the catalog
 * has arrived. The catalog is therefore written down as it arrives and read back whenever JEI
 * loads its plugins, per server.
 *
 * <p>That makes the file one catalog behind: JEI's starts on joining read what the previous
 * session's hello stored. An item created after that hello reaches JEI only at a JEI start
 * after the next join's hello, so in practice after two rejoins, or one rejoin and anything that
 * restarts JEI. A resource reload (F3+T) does not: it rebuilds JEI's ingredient list without
 * re-running its plugins, so nothing here is re-read.
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

    /**
     * The stored catalog's items, decoded, or an empty list when there is none we can read.
     * Every way of coming back empty says so in the log: an empty list here is otherwise
     * indistinguishable from a server with no custom items.
     */
    public static List<ItemStack> stacks() {
        RegistryAccess registries = ClientRegistries.current();
        if (registries == null) {
            LOGGER.info("CraftBridge: not reading the item catalog: no connection to decode it against");
            return List.of();
        }
        byte[] payload;
        Path path;
        try {
            path = file();
            if (!Files.isRegularFile(path)) {
                LOGGER.info("CraftBridge: no stored item catalog for this server yet ({})", path);
                return List.of();
            }
            payload = Files.readAllBytes(path);
        } catch (IOException e) {
            LOGGER.warn("CraftBridge: could not read the item catalog: {}", e.toString());
            return List.of();
        }
        List<LinkProtocol.CatalogEntry> entries;
        try {
            entries = LinkProtocol.decodeItemCatalog(payload).entries();
        } catch (RuntimeException e) {
            // Written by another version of the protocol: ignore it, the server will resend.
            LOGGER.info("CraftBridge: ignoring a stored item catalog this version cannot read ({})", e.toString());
            return List.of();
        }
        List<ItemStack> stacks = decode(entries, registries);
        LOGGER.info("CraftBridge: read {} of {} stored custom item(s) from {}", stacks.size(), entries.size(), path);
        return stacks;
    }

    /**
     * Decode a catalog's items. An entry this client cannot decode costs that entry only, not
     * the whole catalog: it is named in the log and left out.
     */
    public static List<ItemStack> decode(List<LinkProtocol.CatalogEntry> entries, RegistryAccess registries) {
        List<ItemStack> stacks = new ArrayList<>(entries.size());
        for (LinkProtocol.CatalogEntry entry : entries) {
            try {
                ItemStack stack = ItemBlobs.decode(entry.item(), registries);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("CraftBridge: leaving out custom item \"{}\", which this client cannot read: {}",
                        entry.displayName(), e.toString());
            }
        }
        return List.copyOf(stacks);
    }
}
