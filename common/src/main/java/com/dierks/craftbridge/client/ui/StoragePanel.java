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
 * What is in nearby storage, drawn beside the crafting screen and clickable.
 *
 * <p>Without this the mod had no user-visible surface at all: it knew what was in range and
 * used it for JEI's [+], but the player could see none of it — strictly worse than the phantom
 * slots it replaced, which at least showed 36 types and could be clicked.
 *
 * <p>Clicks mean what they mean on a phantom slot, because the server runs them through the
 * same code: <b>left</b> takes a stack to the cursor, <b>right</b> takes half a stack to the
 * cursor, <b>shift</b> takes as many as fit into the inventory. The panel never moves an item
 * itself; it names the item and the click, and the server decides the rest.
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

    /** Where the panel is and what it is showing, so drawing and clicking cannot disagree. */
    private record Layout(int left, int gridTop, int width, int height, List<StorageView.Held> shown) {
    }

    private StoragePanel() {
    }

    public static void render(Screen screen, GuiGraphicsExtractor graphics) {
        Layout layout = layout(screen);
        if (layout == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int top = layout.gridTop() - PADDING - font.lineHeight - PADDING;

        graphics.fill(layout.left(), top, layout.left() + layout.width(), top + layout.height(), BACKGROUND);
        graphics.fill(layout.left(), top, layout.left() + layout.width(), top + 1, BORDER);
        graphics.fill(layout.left(), top + layout.height() - 1,
                layout.left() + layout.width(), top + layout.height(), BORDER);
        graphics.fill(layout.left(), top, layout.left() + 1, top + layout.height(), BORDER);
        graphics.fill(layout.left() + layout.width() - 1, top,
                layout.left() + layout.width(), top + layout.height(), BORDER);

        int total = CraftBridgeClient.get().storage().all().size();
        String header = layout.shown().size() == total
                ? "In range: " + total
                : "In range: " + layout.shown().size() + " of " + total;
        graphics.text(font, Component.literal(header), layout.left() + PADDING, top + PADDING, TEXT);

        for (int i = 0; i < layout.shown().size(); i++) {
            StorageView.Held entry = layout.shown().get(i);
            int x = cellX(layout, i);
            int y = cellY(layout, i);
            ItemStack stack = entry.stack();
            graphics.fakeItem(stack, x, y);
            String count = compact(entry.count());
            graphics.text(font, Component.literal(count),
                    x + CELL - 1 - font.width(count), y + CELL - 1 - font.lineHeight, COUNT_TEXT);
        }

        // Only now, with a frame actually on the screen, does the server hear that it may take
        // this player's phantom slots away.
        CraftBridgeClient.get().panelDrew();
    }

    /**
     * @return true when the click was the panel's, and the screen underneath should not see it
     */
    public static boolean click(Screen screen, double mouseX, double mouseY, int button) {
        Layout layout = layout(screen);
        if (layout == null) {
            return false;
        }
        int index = hit(layout, mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        // hasShiftDown moved from Screen to Minecraft in 26.2; called on the instance so it
        // compiles whichever it is.
        String mode = Minecraft.getInstance().hasShiftDown() ? "ALL" : (button == 1 ? "HALF" : "ONE");
        StorageView.Held entry = layout.shown().get(index);
        CraftBridgeClient.get().requestPull(entry.key(), mode, (ok, message) -> {
            if (!ok && message != null && !message.isEmpty()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(message));
                }
            }
        });
        return true;
    }

    /** Which entry is under the pointer, or -1. */
    private static int hit(Layout layout, double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - (layout.left() + PADDING)) / CELL);
        int row = (int) Math.floor((mouseY - layout.gridTop()) / CELL);
        if (column < 0 || column >= COLUMNS || row < 0) {
            return -1;
        }
        int index = row * COLUMNS + column;
        return index < layout.shown().size() ? index : -1;
    }

    private static int cellX(Layout layout, int index) {
        return layout.left() + PADDING + (index % COLUMNS) * CELL;
    }

    private static int cellY(Layout layout, int index) {
        return layout.gridTop() + (index / COLUMNS) * CELL;
    }

    /** Null when this screen should not have a panel on it. */
    private static Layout layout(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>) || screen.width < MIN_SCREEN_WIDTH) {
            return null;
        }
        CraftBridgeClient link = CraftBridgeClient.get();
        if (!link.sessionLive()) {
            return null;
        }
        List<StorageView.Held> held = sorted(link.storage());
        if (held.isEmpty()) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        int headerHeight = font.lineHeight + PADDING;
        int available = screen.height - MARGIN * 2 - headerHeight - PADDING * 2;
        int rows = Math.max(1, available / CELL);
        int count = Math.min(held.size(), rows * COLUMNS);
        rows = (count + COLUMNS - 1) / COLUMNS;

        int width = COLUMNS * CELL + PADDING * 2;
        int height = headerHeight + rows * CELL + PADDING * 2;
        int top = Math.max(MARGIN, (screen.height - height) / 2);
        return new Layout(MARGIN, top + PADDING + headerHeight, width, height, held.subList(0, count));
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
