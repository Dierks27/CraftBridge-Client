package com.dierks.craftbridge.client.jei;

import com.dierks.craftbridge.client.StorageView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Whether a recipe could be filled from what the player is carrying plus what the server says
 * is in nearby storage.
 *
 * <p>This only decides what the [+] button looks like and which slots it paints red. The server
 * does the real check when the request arrives and is free to disagree: it has the authoritative
 * inventory and this view is at best one tick old, so a wrong answer here costs a refusal
 * message, never an item. That asymmetry settles the judgement call below — when this cannot
 * tell what a slot accepts, it says yes and lets the server rule, because a false red is a dead
 * end for the player while a false green is a message.
 */
final class Craftability {

    /**
     * @param missing slots nothing available can satisfy
     * @param detail  what was compared, for the log: a red button has no other way to be read
     */
    record Report(List<IRecipeSlotView> missing, String detail) {
        boolean ok() {
            return missing.isEmpty();
        }
    }

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

    static Report check(List<IRecipeSlotView> inputs, Player player, StorageView storage) {
        List<Available> pool = new ArrayList<>();
        // The 36 main slots only, hotbar included: what the server fills from. Worn armour and
        // the offhand are part of the same container but are not the player's to spend.
        Container inventory = player.getInventory();
        int mainSlots = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        for (int slot = 0; slot < mainSlots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                pool.add(new Available(stack, stack.getCount()));
            }
        }
        int carried = pool.size();
        for (StorageView.Held held : storage.all()) {
            pool.add(new Available(held.stack(), held.count()));
        }

        List<IRecipeSlotView> missing = new ArrayList<>();
        StringJoiner detail = new StringJoiner("; ");
        detail.add(carried + " carried stack(s), " + (pool.size() - carried) + " type(s) in range");
        for (IRecipeSlotView input : inputs) {
            if (input.isEmpty()) {
                continue;
            }
            List<ItemStack> choices = input.getItemStacks().toList();
            if (choices.isEmpty()) {
                // JEI listed no concrete stacks for this slot — a tag it did not expand here, or
                // an ingredient type this does not understand. Refusing would grey out a button
                // for a recipe the server may well be able to fill, so let the server rule.
                detail.add("a slot lists no items; leaving that one to the server");
                continue;
            }
            if (!take(pool, choices)) {
                missing.add(input);
                if (missing.size() == 1) {
                    detail.add("first unsatisfied slot accepts " + describe(choices)
                            + ", none of which is carried or in range");
                }
            }
        }
        return new Report(missing, detail.toString());
    }

    /**
     * Spend one item on this slot. First acceptable choice wins: a slot that takes any plank
     * takes whichever plank is nearest to hand, the same way the server's own transfer does.
     */
    private static boolean take(List<Available> pool, List<ItemStack> choices) {
        for (ItemStack choice : choices) {
            for (Available available : pool) {
                if (available.amount > 0 && accepts(choice, available.stack)) {
                    available.amount--;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a slot that lists {@code choice} takes {@code have}, judged the way the server
     * judges it rather than by exact components.
     *
     * <p>A plain choice — a vanilla or tag ingredient, which JEI lists as bare stacks with no
     * components — takes any copy of the item: damaged, enchanted or renamed, the recipe only
     * cares what it is. Except one of the server's own custom items, which the server will not
     * spend on an ingredient that does not ask for it (a "Sweet Berry Soup" is not a beetroot
     * soup to a vanilla recipe). A choice that is itself dressed up, a custom item ingredient,
     * takes only that custom item.
     */
    static boolean accepts(ItemStack choice, ItemStack have) {
        if (ItemStack.isSameItemSameComponents(have, choice)) {
            return true;
        }
        if (!ItemStack.isSameItem(have, choice)) {
            return false;
        }
        if (DataComponentPatch.EMPTY.equals(choice.getComponentsPatch())) {
            return !CustomItems.isCustom(have);
        }
        // The same custom item, told apart the way JEI tells it apart: by name, not by every
        // component (a head's profile, for one, need not match between two copies).
        return CustomItems.isCustom(choice)
                && CustomItems.keyOf(choice).equals(CustomItems.keyOf(have));
    }

    /** The first few accepted items by registry name, so a red button can be read in the log. */
    private static String describe(List<ItemStack> choices) {
        StringJoiner names = new StringJoiner(", ", "[", choices.size() > 6 ? ", ...]" : "]");
        for (int i = 0; i < choices.size() && i < 6; i++) {
            names.add(String.valueOf(BuiltInRegistries.ITEM.getKey(choices.get(i).getItem())));
        }
        return choices.size() + " item(s) " + names;
    }
}
