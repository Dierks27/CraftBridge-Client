package com.dierks.craftbridge.client.jei;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * What makes two stacks of the same item different, as far as the server's custom items are
 * concerned: the plugin's own data (a Bukkit persistent-data container lives inside the
 * vanilla custom-data component) and the display name. Two otherwise identical iron ingots,
 * one of them the plugin's "Reinforced Ingot", become two entries in JEI's list rather than one.
 */
final class CatalogSubtypes implements ISubtypeInterpreter<ItemStack> {

    static final CatalogSubtypes INSTANCE = new CatalogSubtypes();

    private CatalogSubtypes() {
    }

    @Override
    public Object getSubtypeData(ItemStack stack, UidContext context) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (data == null && name == null) {
            return null; // a plain one: JEI treats it as the ordinary item, which it is
        }
        return (data == null ? "" : data.toString()) + " " + (name == null ? "" : name.getString());
    }
}
