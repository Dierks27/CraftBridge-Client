package com.dierks.craftbridge.client.jei;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.ui.CraftCountScreen;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * How many crafts JEI's [+] asks for, chosen on the button itself.
 *
 * <p>JEI's API has no hook for the transfer button's mouse input, but it does tell a recipe
 * transfer error when the pointer is over the button: {@code IRecipeTransferError#showError} is
 * only ever called while the button is hovered. {@link StorageTransferHandler} answers a
 * craftable recipe with a cosmetic (non-blocking, invisible) error for exactly that reason, and
 * this class remembers which recipe's button was under the pointer and where. The screen's
 * own mouse events, which the loaders hand us before JEI's recipe screen sees them, then ask
 * {@link #click} and {@link #scroll} whether that is where the pointer is:
 *
 * <ul>
 *   <li><b>mouse wheel</b> over [+] sets a count (0, meaning JEI's usual one or shift-max, up
 *       to {@link #MAX}; shift steps by 8), shown in the button's tooltip, and the next
 *       left-click on it crafts that many;</li>
 *   <li><b>right-click</b> on [+] opens {@link CraftCountScreen} to type a number or pick 1, 8,
 *       16, 64, Max or All but one.</li>
 * </ul>
 */
public final class CraftCount {

    /** One grid slot holds at most a stack, so a craft count past 64 can never be filled. */
    public static final int MAX = 64;
    /** A hover older than this is not "the pointer is on the button now". */
    private static final long HOVER_NANOS = 300_000_000L;
    /** How far (in GUI pixels) the pointer may be from where the hover was drawn. */
    private static final double HOVER_SLOP = 2.0;

    /**
     * One [+] the pointer was over: which recipe, how to describe it to the server, and the
     * crafting screen the recipe screen was opened from.
     *
     * @param key      what a count is remembered by: the recipe id, or the display itself
     * @param recipeId the recipe's registered name, or "" for a display JEI made up
     * @param inputs   the recipe's input slots, turned into slot choices only when sent
     * @param parent   the crafting screen to return to once the grid has been filled
     */
    public record Target(String key, String recipeId, List<IRecipeSlotView> inputs,
                         AbstractContainerScreen<?> parent) {
    }

    private static Target hovered;
    private static long hoveredAt;
    private static double hoverX;
    private static double hoverY;
    private static Screen hoveredOn;

    private static String countKey;
    private static int count;

    private CraftCount() {
    }

    // ---- what the handler reports ------------------------------------------------------

    /** Called while JEI draws a hovered [+] for a recipe CraftBridge can fill. */
    static void hovering(Target target, int mouseX, int mouseY) {
        hovered = target;
        hoveredAt = System.nanoTime();
        hoverX = mouseX;
        hoverY = mouseY;
        hoveredOn = Minecraft.getInstance().gui.screen();
    }

    /** The count chosen for this recipe with the mouse wheel, or 0. */
    static int countFor(String key) {
        return key != null && key.equals(countKey) ? count : 0;
    }

    /** The count chosen for this recipe, which a transfer then uses up. */
    static int take(String key) {
        int chosen = countFor(key);
        if (chosen > 0) {
            countKey = null;
            count = 0;
        }
        return chosen;
    }

    // ---- mouse input -------------------------------------------------------------------

    /**
     * A right-click on a CraftBridge [+] opens the count prompt.
     *
     * @return true when the click was ours and the screen should not see it
     */
    public static boolean click(Screen screen, double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_RIGHT) {
            return false;
        }
        Target target = over(screen, mouseX, mouseY);
        if (target == null) {
            return false;
        }
        Minecraft.getInstance().gui.setScreen(new CraftCountScreen(screen, target, countFor(target.key())));
        return true;
    }

    /**
     * The mouse wheel over a CraftBridge [+] changes its craft count: up for more, down for
     * fewer, by 8 with shift held, from 0 (JEI's usual) to {@link #MAX}.
     *
     * @return true when the scroll was ours and the screen should not see it
     */
    public static boolean scroll(Screen screen, double mouseX, double mouseY, double amount) {
        Target target = over(screen, mouseX, mouseY);
        if (target == null || amount == 0) {
            return target != null;
        }
        int step = Minecraft.getInstance().hasShiftDown() ? 8 : 1;
        int current = countFor(target.key());
        int next = amount > 0 ? current + step : current - step;
        countKey = target.key();
        count = Math.max(0, Math.min(MAX, next));
        return true;
    }

    /** The [+] under the pointer right now, or null. */
    private static Target over(Screen screen, double mouseX, double mouseY) {
        Target target = hovered;
        if (target == null || screen != hoveredOn || !CraftBridgeClient.get().sessionLive()) {
            return null;
        }
        if (System.nanoTime() - hoveredAt > HOVER_NANOS
                || Math.abs(mouseX - hoverX) > HOVER_SLOP || Math.abs(mouseY - hoverY) > HOVER_SLOP) {
            return null;
        }
        return target;
    }

    /** Forget everything: the connection or the session went away. */
    public static void forget() {
        hovered = null;
        hoveredOn = null;
        countKey = null;
        count = 0;
    }
}
