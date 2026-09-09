package com.dierks.craftbridge.client.jei;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What makes two stacks of the same item different, as far as the server's custom items are
 * concerned.
 *
 * <p>The name, when there is one. A server's custom item is a vanilla item the plugin dressed
 * up, and the name is the part of that costume which is both stable and the thing the player
 * is actually looking for: a "Personal Computer" is a Personal Computer whether it came out of
 * a crafting grid, a chest or a recipe the server sent us.
 *
 * <p>The whole component patch was tried and is wrong, because not every component is part of
 * an item's identity. A textured head carries {@code minecraft:profile}, and a profile holds a
 * UUID and a resolution state that need not match between the copy a recipe produces and the
 * copy in the player's inventory. Keying on all of it split one item into two: JEI knew a
 * recipe for the one in its list and none for the one in the hand.
 *
 * <p>Only when there is no name at all does the patch decide, so that items told apart by
 * nothing but a texture are still told apart. Two custom items sharing an item and a name
 * become one entry here — they are the same item to anyone looking at them.
 */
final class CatalogSubtypes implements ISubtypeInterpreter<ItemStack> {

    static final CatalogSubtypes INSTANCE = new CatalogSubtypes();

    private CatalogSubtypes() {
    }

    @Override
    public Object getSubtypeData(ItemStack stack, UidContext context) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            return name.getString();
        }
        DataComponentPatch patch = stack.getComponentsPatch();
        return DataComponentPatch.EMPTY.equals(patch) ? null : patch;
    }
}
