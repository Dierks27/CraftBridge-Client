package com.dierks.craftbridge.client.jei;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The server's custom items as JEI was last given them, each identified the way JEI tells them
 * apart: by base item and {@link CatalogSubtypes} key.
 *
 * <p>Kept so the rest of the mod can ask "is this stack one of the server's custom items?"
 * with the same answer JEI's item list gives — a renamed or damaged vanilla item is not, a
 * "Sweet Berry Soup" on a beetroot soup base is.
 */
final class CustomItems {

    /** One custom item: its base item and what sets it apart from a plain one. */
    record Key(Item item, Object subtype) {
    }

    private static volatile Set<Key> registered = Set.of();

    private CustomItems() {
    }

    /** The key JEI files this stack under, whether or not it is a custom item. */
    static Key keyOf(ItemStack stack) {
        return new Key(stack.getItem(), CatalogSubtypes.key(stack));
    }

    /** Record what JEI was just given, replacing what it had before. */
    static void registered(Collection<ItemStack> stacks) {
        Set<Key> keys = new HashSet<>();
        for (ItemStack stack : stacks) {
            Key key = keyOf(stack);
            if (key.subtype() != null) {
                keys.add(key);
            }
        }
        registered = Set.copyOf(keys);
    }

    /** Every custom item JEI was last given. */
    static Set<Key> registered() {
        return registered;
    }

    /** Whether this stack is one of the server's custom items JEI knows about. */
    static boolean isCustom(ItemStack stack) {
        Key key = keyOf(stack);
        return key.subtype() != null && registered.contains(key);
    }
}
