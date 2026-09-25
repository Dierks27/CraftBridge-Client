package com.dierks.craftbridge.client.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Restarts JEI when the server sends a catalog with custom items JEI was not given, so they get
 * their tiles in this session rather than after a rejoin.
 *
 * <p>JEI reads the catalog only when it loads its plugins, and on joining that happens before
 * the hello brings the catalog. Without this a new custom item waited for the next join (and,
 * since that join's JEI starts also come before its hello, often the one after).
 *
 * <p>JEI's API has no way to ask for a restart, so this calls JEI's own internal
 * {@code mezz.jei.common.Internal.restartJei()} by reflection — the same hook JEI uses when the
 * server's recipes change — and treats any failure as "rejoin to see them". It compares what
 * the items are, not the catalog's bytes: a textured head can carry a fresh random profile id
 * on every send, and must not restart JEI on every join.
 */
public final class JeiRestart {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** A moment's grace: a restart the server itself triggers soon after may make this moot. */
    private static final int DELAY_TICKS = 40;
    private static final String JEI_INTERNAL = "mezz.jei.common.Internal";

    /** The custom items of the catalog that asked for a restart; null when none is pending. */
    private static Set<CustomItems.Key> pending;
    private static int ticksLeft;

    private JeiRestart() {
    }

    /** A catalog arrived. If it lists anything JEI was not given, restart JEI shortly. */
    public static void catalogArrived(List<ItemStack> stacks) {
        Set<CustomItems.Key> keys = new HashSet<>();
        for (ItemStack stack : stacks) {
            CustomItems.Key key = CustomItems.keyOf(stack);
            if (key.subtype() != null) {
                keys.add(key);
            }
        }
        Set<CustomItems.Key> missing = new HashSet<>(keys);
        missing.removeAll(CustomItems.registered());
        if (missing.isEmpty()) {
            pending = null;
            return;
        }
        pending = keys;
        ticksLeft = DELAY_TICKS;
        LOGGER.info("CraftBridge: the catalog lists {} custom item(s) JEI does not have yet; restarting JEI shortly",
                missing.size());
    }

    /** The connection is gone; a restart for it would be for nothing. */
    public static void forget() {
        pending = null;
    }

    /** Called every client tick. */
    public static void tick() {
        if (pending == null) {
            return;
        }
        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }
        if (CustomItems.registered().containsAll(pending)) {
            // JEI restarted on its own since (the server resent its recipes) and read the new
            // catalog then. Nothing left to do.
            pending = null;
            return;
        }
        if (Minecraft.getInstance().gui.screen() instanceof IRecipesGui) {
            return; // a restart closes JEI's recipe screen under the player; wait until they do
        }
        pending = null;
        restart();
    }

    private static void restart() {
        try {
            Class<?> internal = Class.forName(JEI_INTERNAL, true, JeiRestart.class.getClassLoader());
            internal.getMethod("restartJei").invoke(null);
            LOGGER.info("CraftBridge: restarted JEI to show the server's new custom items");
        } catch (Throwable e) {
            // Not there, not accessible, or JEI refused. Nothing is lost but the tiles.
            LOGGER.info("CraftBridge: could not restart JEI ({}); rejoin to see the server's new custom items",
                    e.toString());
        }
    }
}
