package com.dierks.craftbridge.client.ui;

import com.dierks.craftbridge.client.jei.CraftCount;
import com.dierks.craftbridge.client.jei.StorageTransferHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "How many?" for one recipe, opened by right-clicking JEI's [+] at a Linked Workbench.
 *
 * <p>Type a number and press Craft, or pick one of 1, 8, 16, 64, Max (JEI's shift-click: as
 * many as the ingredients and stack sizes allow) or All but one (as many as possible while
 * every container slot an ingredient comes from keeps one). The request goes to the server
 * exactly as a [+] would, with the count attached, and the crafting screen comes back; the
 * server fills the grid for at most that many. Escape goes back to JEI's recipe screen.
 */
public final class CraftCountScreen extends Screen {

    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    private static final int ROW = 24;

    private final Screen previous;
    private final CraftCount.Target target;
    private final int initial;
    private EditBox number;
    private String problem = "";

    public CraftCountScreen(Screen previous, CraftCount.Target target, int initial) {
        super(Component.literal("How many?"));
        this.previous = previous;
        this.target = target;
        this.initial = initial;
    }

    @Override
    protected void init() {
        super.init();
        int centre = this.width / 2;
        int top = this.height / 2 - ROW * 2;

        number = new EditBox(this.font, centre - 50, top, 100, 20, Component.literal("Crafts"));
        number.setMaxLength(3);
        number.setValue(initial > 0 ? Integer.toString(initial) : "");
        addRenderableWidget(number);

        int y = top + ROW;
        int[] presets = {1, 8, 16, 64};
        int x = centre - (presets.length * 44 - 4) / 2;
        for (int preset : presets) {
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(preset)), b -> send(preset, false, false))
                    .bounds(x, y, 40, 20).build());
            x += 44;
        }

        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Max"), b -> send(0, true, false))
                .bounds(centre - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All but one"), b -> send(0, true, true))
                .bounds(centre + 2, y, 100, 20).build());

        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Craft"), b -> sendTyped())
                .bounds(centre - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centre + 2, y, 100, 20).build());
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(number);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        int top = this.height / 2 - ROW * 2;
        centred(graphics, "How many crafts? (1-" + CraftCount.MAX + ")", top - 24, TEXT);
        centred(graphics, problem.isEmpty()
                ? "Type a number and press Craft, or pick one" : problem, top - 12, HINT);
    }

    private void centred(GuiGraphicsExtractor graphics, String text, int y, int colour) {
        graphics.text(this.font, Component.literal(text), (this.width - this.font.width(text)) / 2, y, colour);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Escape or Cancel: back to JEI's recipe screen, nothing sent. */
    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(previous);
    }

    private void sendTyped() {
        int crafts;
        try {
            crafts = Integer.parseInt(number.getValue().trim());
        } catch (NumberFormatException e) {
            problem = "That is not a number.";
            return;
        }
        if (crafts < 1) {
            problem = "At least one.";
            return;
        }
        send(Math.min(crafts, CraftCount.MAX), false, false);
    }

    /**
     * Ask the server, then go back to the crafting screen to watch the grid fill. What the
     * server says on a refusal arrives in chat, as for a plain [+].
     */
    private void send(int crafts, boolean max, boolean leaveOne) {
        if (!StorageTransferHandler.requestCount(target, crafts, max, leaveOne)) {
            problem = "The workbench session is gone.";
            return;
        }
        this.minecraft.gui.setScreen(target.parent());
    }
}
