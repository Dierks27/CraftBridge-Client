package com.dierks.craftbridge.client.ui;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.link.LinkProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Middle-click to sort, the client half.
 *
 * <p>A vanilla client sends nothing for a middle-click in survival, so the server cannot see
 * one on its own. This catches it in a container screen and asks the server to sort: the
 * container when the pointer is over the container's slots, the player's own rows when it is
 * over theirs. The server decides everything else by {@code /sort}'s rules, and only says it
 * will sort ({@link LinkProtocol#FLAG_SORT}) when it really will, so on any other server, or
 * for a player who turned it off, middle-click is left exactly as it was.
 *
 * <p>"Middle-click" is the player's own pick-block binding, matched by vanilla's
 * {@code KeyMapping.matchesMouse}, so no button number appears here (26.3 renumbered them).
 */
public final class ChestSort {

    private static Function<AbstractContainerScreen<?>, Slot> hoveredSlot = screen -> null;

    /** Buttons whose press was taken as a sort, so their release is kept from the screen too. */
    private static final Set<Integer> pressed = new HashSet<>();
    private static Screen pressedOn;

    private ChestSort() {
    }

    /**
     * How this loader reads the slot under the pointer: NeoForge has a public getter, Fabric
     * reads the protected field. Until one is given, no slot is ever found and nothing sorts.
     */
    public static void setHoveredSlotLookup(Function<AbstractContainerScreen<?>, Slot> lookup) {
        hoveredSlot = lookup == null ? screen -> null : lookup;
    }

    /** @return true when the click became a sort request and the screen should not see it */
    public static boolean click(Screen screen, MouseButtonEvent event) {
        CraftBridgeClient link = CraftBridgeClient.get();
        if (!link.sortAllowed()
                || !(screen instanceof AbstractContainerScreen<?> container)
                || screen instanceof CreativeModeInventoryScreen) {
            return false;
        }
        // A Linked Workbench or a Combo Chest with the storage panel: nothing there is
        // sortable, and the server would refuse; leave the click alone.
        if (link.sessionLive()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // Creative keeps vanilla's middle-click clone; spectators cannot sort.
        if (player == null || player.isSpectator() || player.hasInfiniteMaterials()) {
            return false;
        }
        if (!minecraft.options.keyPickItem.matchesMouse(event)) {
            return false;
        }
        AbstractContainerMenu menu = container.getMenu();
        if (!menu.getCarried().isEmpty()) {
            return false; // with a stack on the cursor vanilla has its own use for the click
        }
        Slot slot;
        try {
            slot = hoveredSlot.apply(container);
        } catch (RuntimeException e) {
            return false;
        }
        if (slot == null || !menu.slots.contains(slot)) {
            return false;
        }
        String target = slot.container instanceof Inventory
                ? LinkProtocol.SORT_TARGET_PLAYER : LinkProtocol.SORT_TARGET_CONTAINER;
        boolean sent = link.requestSort(menu.containerId, target, (ok, message) -> {
            if (message != null && !message.isEmpty() && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(message));
            }
        });
        // Taken even when a sort is already in flight: a second middle-click must not fall
        // through to the screen as something else.
        boolean taken = sent || link.sortInFlight();
        if (taken) {
            track(screen).add(event.button());
        }
        return taken;
    }

    /** @return true when this release belongs to a press that was taken as a sort */
    public static boolean release(Screen screen, int button) {
        return track(screen).remove(button);
    }

    private static Set<Integer> track(Screen screen) {
        if (screen != pressedOn) {
            pressed.clear();
            pressedOn = screen;
        }
        return pressed;
    }
}
