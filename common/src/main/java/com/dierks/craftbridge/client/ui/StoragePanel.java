package com.dierks.craftbridge.client.ui;

import com.dierks.craftbridge.client.CraftBridgeClient;
import com.dierks.craftbridge.client.StorageView;
import com.dierks.craftbridge.client.jei.JeiScreens;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 *
 * <p>The mouse wheel over the panel scrolls it, so every item type in range can be reached
 * however many there are. The panel keeps to the space left of the crafting window and of
 * anything JEI knows is drawn there — an open recipe book above all — growing narrower to fit,
 * and stepping aside entirely when even a narrow panel would cover them.
 */
public final class StoragePanel {

    private static final int CELL = 18;
    private static final int COLUMNS = 6;
    /** Narrower than this and the panel is not worth drawing; it steps aside instead. */
    private static final int MIN_COLUMNS = 3;
    private static final int PADDING = 4;
    private static final int MARGIN = 6;
    /**
     * When JEI cannot say where the window is, the screen needs this much width to give a
     * full panel room without covering the GUI (a 176-pixel window centred on it).
     */
    private static final int MIN_SCREEN_WIDTH = 420;
    private static final int SCROLLBAR = 0xFFA0A0A8;

    private static final int BACKGROUND = 0xC0101010;
    private static final int BORDER = 0xFF3F3F4A;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int COUNT_TEXT = 0xFFFFFFFF;

    /** Where the panel is and what it is showing, so drawing and clicking cannot disagree. */
    private record Layout(int left, int top, int gridTop, int width, int height, int columns,
                          int firstRow, int visibleRows, int totalRows, List<StorageView.Held> shown) {
        boolean contains(double x, double y) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }

        boolean scrolls() {
            return totalRows > visibleRows;
        }
    }

    /** The first row shown, and the screen it belongs to; a new screen starts at the top. */
    private static int firstRow;
    private static Screen scrolledOn;

    /**
     * Mouse buttons whose press the panel took, so their release (and any drag in between) can
     * be kept from the screen as well. A container screen that sees a release outside its own
     * window with an item on the cursor throws that item on the ground — and to the screen, the
     * panel is outside its window. Swallowing only the press left the release to do exactly that.
     */
    private static final Set<Integer> pressed = new HashSet<>();
    /** The screen those presses belong to; a different screen starts with a clean slate. */
    private static Screen pressedOn;

    private StoragePanel() {
    }

    public static void render(Screen screen, GuiGraphicsExtractor graphics) {
        Layout layout = layout(screen);
        if (layout == null) {
            if (wanted(screen)) {
                // There is something to show and nowhere to show it. If the server was told the
                // panel is up, it now hears otherwise and puts the phantom slots back.
                CraftBridgeClient.get().panelHidden();
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int top = layout.top();

        graphics.fill(layout.left(), top, layout.left() + layout.width(), top + layout.height(), BACKGROUND);
        graphics.fill(layout.left(), top, layout.left() + layout.width(), top + 1, BORDER);
        graphics.fill(layout.left(), top + layout.height() - 1,
                layout.left() + layout.width(), top + layout.height(), BORDER);
        graphics.fill(layout.left(), top, layout.left() + 1, top + layout.height(), BORDER);
        graphics.fill(layout.left() + layout.width() - 1, top,
                layout.left() + layout.width(), top + layout.height(), BORDER);

        int total = CraftBridgeClient.get().storage().all().size();
        String header = "In range: " + total;
        if (font.width(header) > layout.width() - PADDING * 2) {
            header = compact(total); // a narrow panel beside an open recipe book
        }
        graphics.text(font, Component.literal(header), layout.left() + PADDING, top + PADDING, TEXT);

        if (layout.scrolls()) {
            // A thumb in the right-hand padding: where in the list this is, and that there is more.
            int trackTop = layout.gridTop();
            int trackHeight = layout.visibleRows() * CELL;
            int thumbHeight = Math.max(4, trackHeight * layout.visibleRows() / layout.totalRows());
            int maxFirst = layout.totalRows() - layout.visibleRows();
            int thumbTop = trackTop + (trackHeight - thumbHeight) * layout.firstRow() / maxFirst;
            int x = layout.left() + PADDING + layout.columns() * CELL + 1;
            graphics.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, SCROLLBAR);
        }

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
        // Left and right mean something here; middle and the side buttons do not, and pass
        // through to the screen rather than taking a full stack as a left-click would. Named,
        // not numbered: 26.3 moved input from GLFW to SDL3, which numbers buttons differently.
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
            return false;
        }
        Layout layout = layout(screen);
        if (layout == null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        // Anywhere on the panel is the panel's, header and padding included: to the player it
        // looks like part of the GUI, and to the screen underneath it is outside its window.
        track(screen).add(button);
        int index = hit(layout, mouseX, mouseY);
        if (index < 0) {
            return true;
        }
        // hasShiftDown moved from Screen to Minecraft in 26.2; called on the instance so it
        // compiles whichever it is.
        String mode = Minecraft.getInstance().hasShiftDown() ? "ALL"
                : (button == InputConstants.MOUSE_BUTTON_RIGHT ? "HALF" : "ONE");
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

    /**
     * The mouse wheel over the panel scrolls it a row per notch.
     *
     * @return true when the scroll was over the panel, and the screen underneath should not see it
     */
    public static boolean scroll(Screen screen, double mouseX, double mouseY, double amount) {
        Layout layout = layout(screen);
        if (layout == null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (amount != 0 && layout.scrolls()) {
            int rows = (int) Math.round(amount);
            if (rows == 0) {
                rows = amount > 0 ? 1 : -1;
            }
            // Wheel up (positive) goes back towards the top of the list.
            firstRow = Math.max(0, Math.min(layout.totalRows() - layout.visibleRows(), layout.firstRow() - rows));
        }
        return true;
    }

    /**
     * The release of a button whose press the panel took.
     *
     * @return true when the screen underneath should not see this release
     */
    public static boolean release(Screen screen, int button) {
        return track(screen).remove(button);
    }

    /**
     * A drag with a button whose press the panel took.
     *
     * @return true when the screen underneath should not see this drag
     */
    public static boolean dragging(Screen screen, int button) {
        return track(screen).contains(button);
    }

    private static Set<Integer> track(Screen screen) {
        if (screen != pressedOn) {
            pressed.clear();
            pressedOn = screen;
        }
        return pressed;
    }

    /** Which of the shown entries is under the pointer, or -1. */
    private static int hit(Layout layout, double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - (layout.left() + PADDING)) / CELL);
        int row = (int) Math.floor((mouseY - layout.gridTop()) / CELL);
        if (column < 0 || column >= layout.columns() || row < 0 || row >= layout.visibleRows()) {
            return -1;
        }
        int index = row * layout.columns() + column;
        return index < layout.shown().size() ? index : -1;
    }

    private static int cellX(Layout layout, int index) {
        return layout.left() + PADDING + (index % layout.columns()) * CELL;
    }

    private static int cellY(Layout layout, int index) {
        return layout.gridTop() + (index / layout.columns()) * CELL;
    }

    /** Whether this screen would have a panel on it, given the room. */
    private static boolean wanted(Screen screen) {
        CraftBridgeClient link = CraftBridgeClient.get();
        return screen instanceof AbstractContainerScreen<?> && link.sessionLive() && !link.storage().isEmpty();
    }

    /** Null when this screen should not have a panel on it, or has no room for one. */
    private static Layout layout(Screen screen) {
        if (!wanted(screen)) {
            return null;
        }
        int columns = columns(screen);
        if (columns < MIN_COLUMNS) {
            return null;
        }
        if (screen != scrolledOn) {
            scrolledOn = screen;
            firstRow = 0;
        }
        List<StorageView.Held> held = sorted(CraftBridgeClient.get().storage());
        Font font = Minecraft.getInstance().font;
        int headerHeight = font.lineHeight + PADDING;
        int available = screen.height - MARGIN * 2 - headerHeight - PADDING * 2;
        int totalRows = (held.size() + columns - 1) / columns;
        int visibleRows = Math.min(totalRows, Math.max(1, available / CELL));
        // The list can shrink under a scrolled panel; never scroll past its end.
        firstRow = Math.max(0, Math.min(firstRow, totalRows - visibleRows));
        int from = firstRow * columns;
        int to = Math.min(held.size(), (firstRow + visibleRows) * columns);

        int width = columns * CELL + PADDING * 2;
        int height = headerHeight + visibleRows * CELL + PADDING * 2;
        int top = Math.max(MARGIN, (screen.height - height) / 2);
        return new Layout(MARGIN, top, top + PADDING + headerHeight, width, height, columns,
                firstRow, visibleRows, totalRows, held.subList(from, to));
    }

    /**
     * How many columns fit left of the window and of anything beside it, such as an open recipe
     * book, up to the full six. Without JEI to ask, the old rule: six on a wide enough screen,
     * none otherwise.
     */
    private static int columns(Screen screen) {
        int free = JeiScreens.freeLeftOf(screen);
        if (free < 0) {
            return screen.width < MIN_SCREEN_WIDTH ? 0 : COLUMNS;
        }
        return Math.min(COLUMNS, (free - MARGIN - PADDING * 2) / CELL);
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
