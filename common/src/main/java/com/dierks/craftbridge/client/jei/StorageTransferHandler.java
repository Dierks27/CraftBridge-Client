package com.dierks.craftbridge.client.jei;

import com.dierks.craftbridge.client.ClientRegistries;
import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.ItemBlobs;
import com.dierks.craftbridge.link.LinkProtocol;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.constants.RecipeTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * JEI's [+] at a Linked Workbench, sourced from everything in range rather than from the 36
 * slots of the open menu.
 *
 * <p>Two things matter here. The first is that this handler <b>replaces</b> JEI's own crafting
 * handler — JEI keeps one handler per (menu, recipe type) — so when there is no CraftBridge
 * session it hands the transfer straight back to a stock JEI handler and behaves exactly as if
 * the mod were not installed. The second is that it moves no items itself: it asks the server
 * for the recipe by name and lets the server decide what it is allowed to take, from where. A
 * client that lies gets nothing, because it is never the one holding the items.
 */
public final class StorageTransferHandler implements IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Enough choices to describe a slot that takes a tag, without sending a novel. */
    private static final int MAX_CHOICES_PER_SLOT = 32;

    /**
     * The last thing said about a recipe, so the log carries one line per change rather than one
     * per frame: JEI asks this question continuously while the button is on screen.
     */
    private String lastSaid = "";

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> withoutCraftBridge;

    public StorageTransferHandler(IRecipeTransferHandlerHelper helper,
                                  IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> withoutCraftBridge) {
        this.helper = helper;
        this.withoutCraftBridge = withoutCraftBridge;
    }

    @Override
    public Class<? extends CraftingMenu> getContainerClass() {
        return CraftingMenu.class;
    }

    @Override
    public Optional<MenuType<CraftingMenu>> getMenuType() {
        return Optional.of(MenuType.CRAFTING);
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Deprecated
    @SuppressWarnings("removal") // JEI still declares this overload abstract, so it must be here
    @Override
    public IRecipeTransferError transferRecipe(CraftingMenu container, RecipeHolder<CraftingRecipe> recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        // JEI only calls this on versions older than the context API; there is no transfer id
        // to report back on, so the request goes out unwatched.
        CraftBridgeClient link = CraftBridgeClient.get();
        if (!link.sessionLive()) {
            return withoutCraftBridge.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        IRecipeTransferError error = check(link, inputs, player, recipeId(recipe));
        if (error != null || !doTransfer) {
            return error;
        }
        link.requestTransfer(recipeId(recipe), slotChoices(inputs), maxTransfer, (ok, message) -> report(ok, message));
        return null;
    }

    @Override
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, CraftingMenu> context,
                                               boolean doTransfer) {
        CraftBridgeClient link = CraftBridgeClient.get();
        String recipe = recipeId(context.getRecipe());
        if (!link.sessionLive()) {
            say(recipe, "stock JEI handler — no live CraftBridge session, so only the player's own"
                    + " inventory counts");
            return withoutCraftBridge.transferRecipe(context, doTransfer);
        }

        List<IRecipeSlotView> inputs = context.getRecipeSlots().getSlotViews(RecipeIngredientRole.INPUT);
        IRecipeTransferError error = check(link, inputs, context.getPlayer(), recipe);
        if (error != null || !doTransfer) {
            return error;
        }

        boolean sent = link.requestTransfer(recipeId(context.getRecipe()), slotChoices(inputs), context.isMaxTransfer(),
                (ok, message) -> {
                    report(ok, message);
                    context.completeRecipeTransfer(ok ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
                });
        if (!sent) {
            return helper.createInternalError();
        }
        // The transfer is now the server's to finish; JEI is told when the answer arrives.
        return null;
    }

    /** Can this be made from what the player carries plus what is in range? */
    private IRecipeTransferError check(CraftBridgeClient link, List<IRecipeSlotView> inputs, Player player,
                                       String recipe) {
        if (link.storage().isEmpty()) {
            // Live session, nothing in range: fall back to JEI's own message.
            say(recipe, "CraftBridge handler, but the storage view is empty");
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.missing"));
        }
        Craftability.Report report = Craftability.check(inputs, player, link.storage());
        say(recipe, "CraftBridge handler: " + (report.ok() ? "can be made"
                : report.missing().size() + " slot(s) unsatisfied") + " — " + report.detail());
        if (report.ok()) {
            return null;
        }
        return helper.createUserErrorForMissingSlots(
                Component.translatable("jei.tooltip.error.recipe.transfer.missing"), report.missing());
    }

    /** One line per change of answer, not one per frame. */
    private void say(String recipe, String what) {
        String line = (recipe == null || recipe.isEmpty() ? "(unnamed recipe)" : recipe) + ": " + what;
        if (!line.equals(lastSaid)) {
            lastSaid = line;
            LOGGER.info("CraftBridge [+] {}", line);
        }
    }

    /**
     * The recipe's name, when it has one. Anything JEI shows that is not a real registered
     * recipe (a display JEI synthesised) has none, and is described by its slots instead.
     */
    private static String recipeId(RecipeHolder<CraftingRecipe> recipe) {
        try {
            return recipe.id().identifier().toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * What each grid slot would accept. The index of an input slot view is its position in the
     * crafting grid — the same assumption JEI's own crafting transfer makes.
     */
    private static List<LinkProtocol.SlotChoices> slotChoices(List<IRecipeSlotView> inputs) {
        RegistryAccess registries = ClientRegistries.current();
        if (registries == null) {
            return List.of();
        }
        List<LinkProtocol.SlotChoices> slots = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            IRecipeSlotView input = inputs.get(index);
            if (input.isEmpty()) {
                continue;
            }
            List<byte[]> choices = new ArrayList<>();
            for (ItemStack stack : input.getItemStacks().limit(MAX_CHOICES_PER_SLOT).toList()) {
                choices.add(ItemBlobs.encode(stack, registries));
            }
            if (!choices.isEmpty()) {
                slots.add(new LinkProtocol.SlotChoices(index, choices));
            }
        }
        return slots;
    }

    /** The server's own words on a refusal, in chat, rather than a silent no-op. */
    private static void report(boolean ok, String message) {
        if (ok || message == null || message.isEmpty()) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
