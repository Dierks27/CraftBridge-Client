package com.dierks.craftbridge.client.jei;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;

/**
 * What makes two stacks of the same item different, as far as the server's custom items are
 * concerned: everything the server changed about them.
 *
 * <p>This is the stack's component patch — the difference between it and a plain one of its
 * item — and not a hand-picked component or two. The server's custom items are ordinary items
 * wearing whatever the plugin dressed them in, and which component carries the difference is
 * the plugin's business, not ours. A textured player head differs only in {@code profile}; a
 * place-item differs in {@code custom_data}; a renamed ingot differs in {@code custom_name}.
 * Reading two of those three and calling it the subtype meant every head the plugin sent
 * collapsed into one JEI entry, cycling through four items that JEI thought were the same one.
 *
 * <p>An empty patch means a plain item: JEI is told there is no subtype, which is the truth.
 */
final class CatalogSubtypes implements ISubtypeInterpreter<ItemStack> {

    static final CatalogSubtypes INSTANCE = new CatalogSubtypes();

    private CatalogSubtypes() {
    }

    @Override
    public Object getSubtypeData(ItemStack stack, UidContext context) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return DataComponentPatch.EMPTY.equals(patch) ? null : patch;
    }
}
