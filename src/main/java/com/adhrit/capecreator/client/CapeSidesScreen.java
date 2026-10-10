package com.adhrit.capecreator.client;

import com.adhrit.capecreator.CapeManager;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pick the colours of the cape's inside and edges, each either Auto (the cape's main background
 * colour) or a custom colour, with a 3D preview angled to show them.
 */
public class CapeSidesScreen extends CompatScreen {
    private static final int SWATCH = 12;
    private static final int COLUMN_W = 8 * SWATCH;

    private final CapeEditorScreen parent;
    // starts angled from behind and to the side, so the edge colour is easy to see
    private final PlayerPreview preview = new PlayerPreview(50, 30);
    private final Side inside = new Side("Inside", CapeManager::insideColor, CapeManager::setInsideColor, CapeManager::effectiveInsideColor);
    private final Side edges = new Side("Edges", CapeManager::edgeColor, CapeManager::setEdgeColor, CapeManager::effectiveEdgeColor);

    public CapeSidesScreen(CapeEditorScreen parent) {
        super(Component.literal("Cape Sides"));
        this.parent = parent;
    }

    /** One colour setting: an Auto toggle, a palette and a hex box. */
    private final class Side {
        final String label;
        final Supplier<Integer> getter;
        final Consumer<Integer> setter;
        final Supplier<Integer> effective;
        int x, y;
        Button autoButton;
        EditBox hex;
        boolean updatingHex;

        Side(String label, Supplier<Integer> getter, Consumer<Integer> setter, Supplier<Integer> effective) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.effective = effective;
        }

        void init(int x, int y) {
            this.x = x;
            this.y = y;
            autoButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                apply(getter.get() == null ? effective.get() : null);
                refresh();
            }).bounds(x, y + 12, COLUMN_W, 20).build());
            autoButton.setTooltip(Tooltip.create(Component.literal("Auto uses the cape's main background colour")));
            hex = new EditBox(font, x + 22, paletteY() + 3 * SWATCH + 6, COLUMN_W - 22, 18, Component.literal(label + " hex"));
            hex.setMaxLength(7);
            hex.setResponder(text -> {
                if (updatingHex) return;
                String h = text.startsWith("#") ? text.substring(1) : text;
                if (h.length() == 6) {
                    try {
                        apply(Integer.parseInt(h, 16));
                        refreshButton();
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            addRenderableWidget(hex);
            refresh();
        }

        /** Sets the colour (null = Auto), with one undo step, only if it actually changes. */
        void apply(Integer color) {
            Integer value = color == null ? null : 0xFF000000 | color;
            if (java.util.Objects.equals(value, getter.get())) return;
            parent.pushUndo();
            setter.accept(value);
        }

        int paletteY() {
            return y + 38;
        }

        void refreshButton() {
            autoButton.setMessage(Component.literal(getter.get() == null ? "Auto: On" : "Auto: Off"));
        }

        void refresh() {
            refreshButton();
            updatingHex = true;
            hex.setValue(String.format("#%06X", effective.get() & 0xFFFFFF));
            updatingHex = false;
        }

        boolean click(double mx, double my) {
            int py = paletteY();
            if (mx >= x && mx < x + COLUMN_W && my >= py && my < py + 3 * SWATCH) {
                int i = (int) ((my - py) / SWATCH) * 8 + (int) ((mx - x) / SWATCH);
                apply(CapeEditorScreen.PALETTE[i]);
                refresh();
                return true;
            }
            return false;
        }

        void render(GuiGraphics context) {
            context.drawString(font, Component.literal(label), x, y, 0xFFFFFFFF);
            int py = paletteY();
            int current = effective.get();
            for (int i = 0; i < CapeEditorScreen.PALETTE.length; i++) {
                int sx = x + (i % 8) * SWATCH, sy = py + (i / 8) * SWATCH;
                context.fill(sx, sy, sx + SWATCH, sy + SWATCH, CapeEditorScreen.PALETTE[i]);
                if (getter.get() != null && CapeEditorScreen.PALETTE[i] == current) outline(context, sx, sy, SWATCH, SWATCH, 0xFFFFFFFF);
            }
            int sy = py + 3 * SWATCH + 6;
            outline(context, x, sy, 18, 18, 0xFFFFFFFF);
            context.fill(x + 1, sy + 1, x + 17, sy + 17, current);
        }
    }

    @Override
    protected void init() {
        int left = 16;
        inside.init(left, 30);
        edges.init(left + COLUMN_W + 12, 30);

        int previewX1 = left + 2 * COLUMN_W + 28;
        preview.setBounds(previewX1, 30, width - 12, height - 32);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left, height - 26, 2 * COLUMN_W + 12, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        inside.render(context);
        edges.render(context);
        context.drawString(font, Component.literal("Main background: "), 16, height - 44, 0xFFA0A0A0);
        int bx = 16 + font.width("Main background: ");
        outline(context, bx, height - 46, 12, 12, 0xFFFFFFFF);
        context.fill(bx + 1, height - 45, bx + 11, height - 35, CapeManager.backgroundColor());
        preview.render(context, font);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (superMouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && (inside.click(mouseX, mouseY) || edges.click(mouseX, mouseY))) return true;
        return preview.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (preview.mouseDragged(deltaX, deltaY)) return true;
        return superMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        preview.mouseReleased();
        return superMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return preview.mouseScrolled(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static void outline(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
