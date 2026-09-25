package com.dierks.craftbridge.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The server's custom items as they appear in the recipes it synced to this client.
 *
 * <p>The catalog only arrives with the hello, after JEI has already started on joining, and is
 * only read when JEI starts. The recipes, though, reach a Fabric client before the vanilla
 * packet that starts JEI, every time the server sends them — including when an admin saves a
 * new recipe mid-session. A CraftBridge recipe's result carries the custom item exactly, name
 * and plugin data included, so it is as good a source for "which items need their own tile"
 * as the catalog, and it is current.
 *
 * <p>Only a loader that has the synced recipes can supply them: Fabric does, through Fabric
 * API; NeoForge never receives a Paper server's recipes at all, and leaves this empty.
 */
public final class RecipeResults {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The plugin's own recipes; every other namespace is left to the catalog. */
    private static final String CRAFTBRIDGE_NAMESPACE = "craftbridge";

    private static volatile Supplier<Collection<RecipeHolder<?>>> source = List::of;

    private RecipeResults() {
    }

    /** Where this loader keeps the recipes the server synced, read fresh each time. */
    public static void setSource(Supplier<Collection<RecipeHolder<?>>> recipes) {
        source = recipes;
    }

    /**
     * The dressed-up results — a custom name or plugin data — of the CraftBridge recipes the
     * server synced, one of each, or nothing at all unless this is a CraftBridge server.
     *
     * <p>That last condition is what keeps the mod inert everywhere else: in singleplayer or on
     * any other server with JEI, a datapack recipe whose result has a name must not get this
     * mod's subtypes. Before the server has answered the hello this returns nothing, and the
     * JEI restart that follows a new catalog picks the recipes up.
     */
    public static List<ItemStack> dressedUp() {
        if (!CraftBridgeClient.get().serverHasCraftBridge()) {
            return List.of();
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        List<ItemStack> results = new ArrayList<>();
        try {
            ContextMap context = SlotDisplayContext.fromLevel(level);
            for (RecipeHolder<?> holder : source.get()) {
                try {
                    if (!CRAFTBRIDGE_NAMESPACE.equals(holder.id().identifier().getNamespace())
                            || !(holder.value() instanceof CraftingRecipe recipe)) {
                        continue;
                    }
                    for (RecipeDisplay display : recipe.display()) {
                        for (ItemStack stack : display.result().resolveForStacks(context)) {
                            if (!stack.isEmpty() && (stack.has(DataComponents.CUSTOM_NAME)
                                    || stack.has(DataComponents.CUSTOM_DATA))) {
                                results.add(stack.copyWithCount(1));
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("CraftBridge: could not read the result of recipe {}: {}", holder.id(), e.toString());
                }
            }
        } catch (RuntimeException e) {
            // Whatever the loader's recipe store did, JEI's plugin loading must not fail over it.
            LOGGER.warn("CraftBridge: could not read the server's synced recipes: {}", e.toString());
        }
        return List.copyOf(results);
    }
}
