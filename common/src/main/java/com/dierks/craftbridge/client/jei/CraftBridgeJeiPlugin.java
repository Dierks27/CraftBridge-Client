package com.dierks.craftbridge.client.jei;

import com.dierks.craftbridge.client.CatalogCache;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where the mod meets JEI. Everything else it does is networking. */
@JeiPlugin
public class CraftBridgeJeiPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath("craftbridge_client", "craftbridge");

    /** JEI's own crafting-table handler, reproduced exactly: grid slots 1 to 9, inventory 10 to 45. */
    private static final int RECIPE_SLOT_START = 1;
    private static final int RECIPE_SLOT_COUNT = 9;
    private static final int INVENTORY_SLOT_START = 10;
    private static final int INVENTORY_SLOT_COUNT = 36;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /**
     * Give the server's custom items their own tiles in the item list. A renamed vanilla item
     * carrying plugin data is not a registry entry of its own, so without this JEI has nothing
     * to show and nothing to look a recipe up from, which is why those recipes looked missing.
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        Set<Item> seen = new HashSet<>();
        for (ItemStack stack : CatalogCache.stacks()) {
            if (seen.add(stack.getItem())) {
                registration.registerSubtypeInterpreter(stack.getItem(), CatalogSubtypes.INSTANCE);
            }
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        List<ItemStack> custom = CatalogCache.stacks();
        if (!custom.isEmpty()) {
            registration.addExtraItemStacks(custom);
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        // JEI keeps one handler per (menu, recipe type), so registering ours replaces the stock
        // one. Keep a stock one to hand back to whenever there is no CraftBridge session.
        IRecipeTransferInfo<CraftingMenu, RecipeHolder<CraftingRecipe>> stock =
                helper.createBasicRecipeTransferInfo(CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING,
                        RECIPE_SLOT_START, RECIPE_SLOT_COUNT, INVENTORY_SLOT_START, INVENTORY_SLOT_COUNT);
        registration.addRecipeTransferHandler(
                new StorageTransferHandler(helper, helper.createUnregisteredRecipeTransferHandler(stock)),
                RecipeTypes.CRAFTING);
    }
}
