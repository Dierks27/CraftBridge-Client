package com.dierks.craftbridge.client;

import com.dierks.craftbridge.link.SnapshotTracker;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The storage the server says is in range, decoded into item stacks once per update.
 *
 * <p>This is the whole point of the mod. A server on its own can only show JEI what fits in
 * the 36 slots of the open menu, because JEI works out what is craftable by scanning those
 * slots; here the counts arrive directly, so the list is as long as the base is big.
 */
public final class StorageView {

    /** One item type and how many of it are in range. */
    public record Held(ItemStack stack, int count) {
    }

    private List<Held> held = List.of();

    /** Replace the view from the tracker's current counts. Called after every applied update. */
    void rebuild(Map<SnapshotTracker.ItemKey, Integer> counts, RegistryAccess registries) {
        List<Held> rebuilt = new ArrayList<>(counts.size());
        for (Map.Entry<SnapshotTracker.ItemKey, Integer> entry : counts.entrySet()) {
            ItemStack stack = ItemBlobs.decode(entry.getKey().bytes(), registries);
            if (!stack.isEmpty() && entry.getValue() > 0) {
                rebuilt.add(new Held(stack, entry.getValue()));
            }
        }
        held = List.copyOf(rebuilt);
    }

    void clear() {
        held = List.of();
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
