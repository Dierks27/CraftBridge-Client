package com.dierks.craftbridge.client.jei;

import com.dierks.craftbridge.client.StorageView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a recipe could be filled from what the player is carrying plus what the server says
 * is in nearby storage.
 *
 * <p>This only decides what the [+] button looks like and which slots it paints red. The server
 * does the real check when the request arrives and is free to disagree: it has the authoritative
 * inventory and this view is at best one tick old, so a wrong answer here costs a refusal
 * message, never an item.
 */
final class Craftability {

    private static final class Available {
        private final ItemStack stack;
        private int amount;

        private Available(ItemStack stack, int amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private Craftability() {
    }

    /** The input slots that nothing available can satisfy. Empty means one set can be made. */
    static List<IRecipeSlotView> missing(List<IRecipeSlotView> inputs, Player player, StorageView storage) {
        List<Available> pool = new ArrayList<>();
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                pool.add(new Available(stack, stack.getCount()));
            }
        }
        for (StorageView.Held held : storage.all()) {
            pool.add(new Available(held.stack(), held.count()));
        }

        List<IRecipeSlotView> missing = new ArrayList<>();
        for (IRecipeSlotView input : inputs) {
            if (input.isEmpty()) {
                continue;
            }
            if (!take(pool, input)) {
                missing.add(input);
            }
        }
        return missing;
    }

    /**
     * Spend one item on this slot. First acceptable choice wins: a slot that takes any plank
     * takes whichever plank is nearest to hand, the same way the server's own transfer does.
     */
    private static boolean take(List<Available> pool, IRecipeSlotView input) {
        List<ItemStack> choices = input.getItemStacks().toList();
        for (ItemStack choice : choices) {
            for (Available available : pool) {
                if (available.amount > 0 && ItemStack.isSameItemSameComponents(available.stack, choice)) {
                    available.amount--;
                    return true;
                }
            }
        }
        return false;
    }
}
