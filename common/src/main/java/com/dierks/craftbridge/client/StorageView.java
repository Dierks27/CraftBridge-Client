package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.SnapshotTracker;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The storage the server says is in range, decoded into item stacks once per update.
 *
 * <p>This is the whole point of the mod. A server on its own can only show JEI what fits in
 * the 36 slots of the open menu, because JEI works out what is craftable by scanning those
 * slots; here the counts arrive directly, so the list is as long as the base is big.
 */
public final class StorageView {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * One item type and how many of it are in range. {@code key} is the item exactly as the
     * server encoded it, kept so a click can name it back with the same bytes rather than a
     * re-encoding that might not match.
     */
    public record Held(byte[] key, ItemStack stack, int count) {
    }

    private List<Held> held = List.of();
    /** Entries this client could not decode, each reported once rather than on every update. */
    private final Set<SnapshotTracker.ItemKey> undecodable = new HashSet<>();

    /**
     * Replace the view from the tracker's current counts. Called after every applied update.
     *
     * <p>One item this client cannot decode — a component it does not know, data another mod
     * wrote — costs that one entry, not the view: it is left out, said once in the log, and
     * everything else in range still shows.
     */
    void rebuild(Map<SnapshotTracker.ItemKey, Integer> counts, RegistryAccess registries) {
        List<Held> rebuilt = new ArrayList<>(counts.size());
        for (Map.Entry<SnapshotTracker.ItemKey, Integer> entry : counts.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            ItemStack stack;
            try {
                stack = ItemBlobs.decode(entry.getKey().bytes(), registries);
            } catch (RuntimeException e) {
                if (undecodable.add(entry.getKey())) {
                    LOGGER.warn("CraftBridge: leaving out an item in range this client cannot read "
                            + "({} bytes): {}", entry.getKey().bytes().length, e.toString());
                }
                continue;
            }
            if (!stack.isEmpty()) {
                rebuilt.add(new Held(entry.getKey().bytes(), stack, entry.getValue()));
            }
        }
        held = List.copyOf(rebuilt);
    }

    void clear() {
        held = List.of();
        undecodable.clear();
    }

    public List<Held> all() {
        return held;
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    /** How many of this exact item (same components) are in range. */
    public int available(ItemStack want) {
        int total = 0;
        for (Held entry : held) {
            if (ItemStack.isSameItemSameComponents(entry.stack(), want)) {
                total += entry.count();
            }
        }
        return total;
    }
}
