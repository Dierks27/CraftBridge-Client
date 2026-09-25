package com.dierks.craftbridge.client.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IScreenHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.Iterator;
import java.util.Optional;

/**
 * What JEI knows about the layout of the open screen, for the storage panel.
 *
 * <p>JEI already tracks where a container screen's window is and what sits beside it — the
 * recipe book most of all, which pushes the window to the right and takes the space on its
 * left, exactly where the panel goes. Both loaders expose the window's position only through
 * their own access wideners or patches, and the recipe book only through a protected field, so
 * asking JEI is the one way the shared code can know without reaching into either.
 */
public final class JeiScreens {

    private static volatile IJeiRuntime runtime;

    private JeiScreens() {
    }

    static void runtimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    static void runtimeUnavailable() {
        runtime = null;
    }

    /**
     * How many pixels from the screen's left edge are free: up to the window, or up to anything
     * JEI knows is drawn to the left of it (an open recipe book and its tabs).
     *
     * @return the free width, or -1 when JEI is not running and cannot say
     */
    public static int freeLeftOf(Screen screen) {
        IJeiRuntime current = runtime;
        if (current == null) {
            return -1;
        }
        try {
            IScreenHelper helper = current.getScreenHelper();
            Optional<IGuiProperties> properties = helper.getGuiProperties(screen);
            if (properties.isEmpty()) {
                return -1;
            }
            int free = properties.get().guiLeft();
            Iterator<Rect2i> areas = helper.getGuiExclusionAreas(screen).iterator();
            while (areas.hasNext()) {
                Rect2i area = areas.next();
                if (area.getWidth() > 0 && area.getHeight() > 0 && area.getX() < free) {
                    free = area.getX();
                }
            }
            return Math.max(0, free);
        } catch (RuntimeException e) {
            // Another mod's handler threw inside JEI. Not knowing is not a reason to crash a frame.
            return -1;
        }
    }
}
