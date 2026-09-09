package com.dierks.craftbridge.client.ui;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.StorageView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What is in nearby storage, drawn beside the crafting screen.
 *
 * <p>Without this the mod had no user-visible surface at all: it knew what was in range and
 * used it for JEI's [+], but the player could see none of it — strictly worse than the
 * phantom slots it replaced, which at least showed 36 types. The server now keeps those
 * phantoms until this panel confirms it is drawing.
 *
 * <p>Deliberately plain: item, count, most numerous first. It reads the same view the
 * craftability check reads, so what is shown and what [+] can use cannot disagree.
 */
public final class StoragePanel {

    private static final int CELL = 18;
    private static final int COLUMNS = 6;
    private static final int PADDING = 4;
    private static final int MARGIN = 6;
    /** Below this the screen is too cramped to give the panel room without covering the GUI. */
    private static final int MIN_SCREEN_WIDTH = 420;

    private static final int BACKGROUND = 0xC0101010;
    private static final int BORDER = 0xFF3F3F4A;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int COUNT_TEXT = 0xFFFFFFFF;

    private StoragePanel() {
    }

    /** Draw the panel if this screen is a crafting menu and a CraftBridge session is live. */
    public static void render(Screen screen, GuiGraphicsExtractor graphics) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        CraftBridgeClient link = CraftBridgeClient.get();
        if (!link.sessionLive()) {
            return;
        }
        List<StorageView.Held> held = sorted(link.storage());
        if (held.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (screen.width < MIN_SCREEN_WIDTH) {
            return;
        }

        int gridWidth = COLUMNS * CELL;
        int panelWidth = gridWidth + PADDING * 2;
        int headerHeight = font.lineHeight + PADDING;
        // As many rows as fit between the top and bottom margins, and never more than we have.
        int available = screen.height - MARGIN * 2 - headerHeight - PADDING * 2;
        int rows = Math.max(1, available / CELL);
        int shown = Math.min(held.size(), rows * COLUMNS);
        rows = (shown + COLUMNS - 1) / COLUMNS;

        int panelHeight = headerHeight + rows * CELL + PADDING * 2;
        int left = MARGIN;
        int top = Math.max(MARGIN, (screen.height - panelHeight) / 2);

        graphics.fill(left, top, left + panelWidth, top + panelHeight, BACKGROUND);
        graphics.fill(left, top, left + panelWidth, top + 1, BORDER);
        graphics.fill(left, top + panelHeight - 1, left + panelWidth, top + panelHeight, BORDER);
        graphics.fill(left, top, left + 1, top + panelHeight, BORDER);
        graphics.fill(left + panelWidth - 1, top, left + panelWidth, top + panelHeight, BORDER);

        String header = held.size() == shown
                ? "In range: " + held.size()
                : "In range: " + shown + " of " + held.size();
        graphics.text(font, Component.literal(header), left + PADDING, top + PADDING, TEXT);

        int gridTop = top + PADDING + headerHeight;
        for (int i = 0; i < shown; i++) {
            StorageView.Held entry = held.get(i);
            int x = left + PADDING + (i % COLUMNS) * CELL;
            int y = gridTop + (i / COLUMNS) * CELL;
            ItemStack stack = entry.stack();
            graphics.fakeItem(stack, x, y);
            String count = compact(entry.count());
            graphics.text(font, Component.literal(count),
                    x + CELL - 1 - font.width(count), y + CELL - 1 - font.lineHeight, COUNT_TEXT);
        }
    }

    /** Most numerous first: the panel is a glance, not an index. */
    private static List<StorageView.Held> sorted(StorageView storage) {
        List<StorageView.Held> held = new ArrayList<>(storage.all());
        held.sort(Comparator.comparingInt((StorageView.Held h) -> -h.count())
                .thenComparing(h -> h.stack().getItem().toString()));
        return held;
    }

    /**
     * A count that fits under a 16-pixel icon: 64, 1.2k, 15k. Vanilla's own stack-size text
     * would say "3200" and run off the tile.
     */
    static String compact(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        if (count < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0).replace(".0k", "k");
        }
        if (count < 1_000_000) {
            return (count / 1000) + "k";
        }
        return (count / 1_000_000) + "m";
    }
}
