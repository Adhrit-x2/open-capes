package com.adhrit.capecreator.client;

import com.adhrit.capecreator.Compat;
import com.adhrit.capecreator.CapeCreatorClient;
import com.adhrit.capecreator.CapeManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.platform.InputConstants;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pixel editor for the cape, with a live preview of the player wearing it. */
public class CapeEditorScreen extends CompatScreen {
    static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFC0C0C0, 0xFF808080, 0xFF404040, 0xFF000000, 0xFF5A3A22, 0xFF8B5A2B, 0xFFD2A679,
            0xFFFF0000, 0xFFB01818, 0xFFFF7F00, 0xFFFFD800, 0xFFF2C94C, 0xFF7FFF00, 0xFF00A000, 0xFF0A5C2A,
            0xFF00FFFF, 0xFF0094FF, 0xFF2B4C9B, 0xFF0026FF, 0xFF7F00FF, 0xFFB200FF, 0xFFFF00DC, 0xFFFF7FB6,
    };
    private static final int SWATCH = 14;
    private static final int PANEL_W = 128;
    private static final int MAX_UNDO = 64;
    private static final long MAX_UNDO_PIXELS = 24_000_000;   // ~96 MB of undo history

    private enum Tool { PENCIL, FILL }

    private final CapeManager.State original = CapeManager.snapshot();
    private final boolean originalEnabled = CapeManager.isEnabled();
    private final Deque<CapeManager.State> undo = new ArrayDeque<>();

    private int color = 0xFFFFFFFF;
    private Tool tool = Tool.PENCIL;
    private boolean mirror = false;
    private boolean cancelled = false;
    private boolean painting = false;
    // 3D preview camera: yaw/pitch in degrees (0/-10 shows the cape from slightly above), plus zoom
    private final PlayerPreview preview = new PlayerPreview(0, -10);

    private float cell;   // screen pixels per design pixel; below 1 for big HD designs
    private int canvasX, canvasY, canvasW, canvasH, panelX, previewX1, previewX2;
    private EditBox hexField;
    private Button toolButton, mirrorButton, scaleButton, enabledButton, fpsButton;
    private boolean updatingHex = false;

    public CapeEditorScreen() {
        super(Component.translatable("screen.capecreator.title"));
    }

    @Override
    protected void init() {
        layout();

        int bw = (PANEL_W - 4) / 2;
        int y = canvasY;
        toolButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            tool = tool == Tool.PENCIL ? Tool.FILL : Tool.PENCIL;
            refreshLabels();
        }).bounds(panelX, y, bw, 20).build());
        mirrorButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            mirror = !mirror;
            refreshLabels();
        }).bounds(panelX + bw + 4, y, bw, 20).build());
        y += 22;
        scaleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleScale())
                .bounds(panelX, y, bw, 20).build());
        scaleButton.setTooltip(Tooltip.create(Component.literal("Cape resolution: " + CapeManager.width() + "x" + CapeManager.height()
                + " pixels. Higher sizes look sharper but are harder to paint by hand.")));
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CapeManager.setEnabled(!CapeManager.isEnabled());
            refreshLabels();
        }).bounds(panelX + bw + 4, y, bw, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Fill All"), b -> fillAll())
                .bounds(panelX, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
                .bounds(panelX + bw + 4, y, bw, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Saved"), b -> {
            CapeManager.save();
            minecraft.setScreen(new SavedCapesScreen());
        }).bounds(panelX, y, bw, 20).build()).setTooltip(Tooltip.create(Component.literal(
                "Your saved capes: switch between them, or save this one. In game, [ and ] cycle through them.")));
        addRenderableWidget(Button.builder(Component.literal("Link"), b -> openLinkImport(null))
                .bounds(panelX + bw + 4, y, bw, 20).build()).setTooltip(Tooltip.create(Component.literal(
                "Import a GIF or image from a link. You can also paste a link with Ctrl/Cmd+V, or drop a file on this window.")));
        y += 22;
        fpsButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleFps())
                .bounds(panelX, y, bw, 20).build());
        fpsButton.setTooltip(Tooltip.create(Component.literal("Animation speed (frames per second)")));
        addRenderableWidget(Button.builder(Component.literal("Sides"), b -> minecraft.setScreen(new CapeSidesScreen(this)))
                .bounds(panelX + bw + 4, y, bw, 20).build()).setTooltip(Tooltip.create(Component.literal(
                "Choose the colors of the inside and edges of the cape")));
        y += 22;

        // Animation frames: [<] 1/3 [>] [+] [-]
        addRenderableWidget(Button.builder(Component.literal("<"), b -> CapeManager.selectFrame(CapeManager.editFrame() - 1))
                .bounds(panelX, y, 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> CapeManager.selectFrame(CapeManager.editFrame() + 1))
                .bounds(panelX + PANEL_W - 58, y, 18, 20).build());
        Button add = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            pushUndo();
            CapeManager.duplicateFrame();
        }).bounds(panelX + PANEL_W - 38, y, 18, 20).build());
        add.setTooltip(Tooltip.create(Component.literal("Add frame (copy of this one)")));
        Button remove = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            pushUndo();
            CapeManager.deleteFrame();
        }).bounds(panelX + PANEL_W - 18, y, 18, 20).build());
        remove.setTooltip(Tooltip.create(Component.literal("Delete this frame")));

        hexField = new EditBox(font, panelX + 22, paletteY() + 3 * SWATCH + 6, PANEL_W - 22, 18, Component.literal("Hex"));
        hexField.setMaxLength(7);
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);

        int bottom = height - 26;
        int pw = (previewX2 - previewX1 - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> onClose())
                .bounds(previewX1, bottom, pw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            cancelled = true;
            onClose();
        }).bounds(previewX1 + pw + 4, bottom, pw, 20).build());

        setColor(color);
        refreshLabels();
    }

    /** Sizes the canvas to the screen; called on init and whenever the resolution changes. */
    private void layout() {
        int gw = CapeManager.width(), gh = CapeManager.height();
        int maxCanvasH = height - 50;
        int maxCanvasW = Math.max(60, width - PANEL_W - 190);
        cell = Math.min((float) maxCanvasH / gh, (float) maxCanvasW / gw);
        if (cell >= 1) cell = (float) Math.floor(cell);
        canvasW = Math.round(gw * cell);
        canvasH = Math.round(gh * cell);
        canvasX = 16;
        canvasY = 30;
        panelX = canvasX + canvasW + 16;
        previewX1 = panelX + PANEL_W + 8;
        previewX2 = width - 8;
    }

    private int paletteY() {
        return canvasY + 6 * 22 + 4;
    }

    private void refreshLabels() {
        toolButton.setMessage(Component.literal(tool == Tool.PENCIL ? "Tool: Pencil" : "Tool: Bucket"));
        mirrorButton.setMessage(Component.literal("Mirror: " + (mirror ? "On" : "Off")));
        scaleButton.setMessage(Component.literal("Size: " + CapeManager.scale() + "x"));
        enabledButton.setMessage(Component.literal("Cape: " + (CapeManager.isEnabled() ? "On" : "Off")));
        fpsButton.setMessage(Component.literal("FPS: " + CapeManager.fps()));
    }

    private void setColor(int argb) {
        color = 0xFF000000 | argb;
        if (hexField != null) {
            updatingHex = true;
            hexField.setValue(String.format("#%06X", color & 0xFFFFFF));
            updatingHex = false;
        }
    }

    private void onHexChanged(String text) {
        if (updatingHex) return;
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 6) {
            try {
                color = 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ---- editing ----

    void pushUndo() {
        undo.push(CapeManager.snapshot());
        long perState = (long) CapeManager.width() * CapeManager.height() * CapeManager.frameCount();
        long limit = Math.max(4, Math.min(MAX_UNDO, MAX_UNDO_PIXELS / Math.max(1, perState)));
        while (undo.size() > limit) undo.removeLast();
    }

    private void undo() {
        CapeManager.State state = undo.poll();
        if (state == null) return;
        boolean scaleChanged = state.scale() != CapeManager.scale();
        CapeManager.restore(state);
        if (scaleChanged) rebuildWidgets();
        else refreshLabels();
    }

    private void cycleFps() {
        int[] options = CapeManager.FPS_OPTIONS;
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == CapeManager.fps()) next = options[(i + 1) % options.length];
        }
        CapeManager.setFps(next);
        refreshLabels();
    }

    private void cycleScale() {
        pushUndo();
        int[] scales = CapeManager.SCALES;
        int next = scales[0];
        for (int i = 0; i < scales.length; i++) {
            if (scales[i] == CapeManager.scale()) next = scales[(i + 1) % scales.length];
        }
        CapeManager.setScale(next);
        rebuildWidgets();
    }

    private void fillAll() {
        pushUndo();
        for (int y = 0; y < CapeManager.height(); y++) {
            for (int x = 0; x < CapeManager.width(); x++) {
                CapeManager.set(x, y, color);
            }
        }
    }

    private void paint(int x, int y) {
        CapeManager.set(x, y, color);
        if (mirror) CapeManager.set(CapeManager.width() - 1 - x, y, color);
    }

    private void floodFill(int sx, int sy) {
        int target = CapeManager.get(sx, sy);
        if (target == color) return;
        int w = CapeManager.width(), h = CapeManager.height();
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= w || y >= h || CapeManager.get(x, y) != target) continue;
            CapeManager.set(x, y, color);
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
    }

    /** Returns {x, y} of the canvas cell under the mouse, or null if outside the canvas. */
    private int[] cellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor((mouseX - canvasX) / cell);
        int y = (int) Math.floor((mouseY - canvasY) / cell);
        if (mouseX < canvasX || mouseY < canvasY || mouseX >= canvasX + canvasW || mouseY >= canvasY + canvasH) return null;
        x = Math.min(x, CapeManager.width() - 1);
        y = Math.min(y, CapeManager.height() - 1);
        return new int[]{x, y};
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int[] c = cellAt(mouseX, mouseY);
        if (c != null) {
            setFocused(null);
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                setColor(CapeManager.get(c[0], c[1]));
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                pushUndo();
                if (tool == Tool.FILL) {
                    floodFill(c[0], c[1]);
                } else {
                    painting = true;
                    paint(c[0], c[1]);
                }
            }
            return true;
        }

        int py = paletteY();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && mouseX >= panelX && mouseX < panelX + 8 * SWATCH
                && mouseY >= py && mouseY < py + 3 * SWATCH) {
            int i = (int) ((mouseY - py) / SWATCH) * 8 + (int) ((mouseX - panelX) / SWATCH);
            setColor(PALETTE[i]);
            return true;
        }

        if (superMouseClicked(mouseX, mouseY, button)) return true;

        return preview.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (preview.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (painting) {
            int[] c = cellAt(mouseX, mouseY);
            if (c != null) paint(c[0], c[1]);
            return true;
        }
        if (preview.mouseDragged(deltaX, deltaY)) return true;
        return superMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        preview.mouseReleased();
        return superMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & (InputConstants.MOD_CONTROL | Compat.MOD_SUPER)) != 0;
        if (ctrl && keyCode == InputConstants.KEY_Z) {
            undo();
            return true;
        }
        if (getFocused() != hexField && isPasteKey(keyCode, scanCode, modifiers)) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (UrlImportScreen.looksLikeUrl(clip)) {
                openLinkImport(clip.trim());
                return true;
            }
        }
        if (getFocused() != hexField && (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT)) {
            CapeManager.selectFrame(CapeManager.editFrame() + (keyCode == InputConstants.KEY_LEFT ? -1 : 1));
            return true;
        }
        return superKeyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Drag an image onto the window to import it. A cape texture from a cape site (64x32 layout,
     * frames stacked vertically for animated ones) has its back panel cut out automatically.
     * Anything else (GIF, PNG, JPG) opens the crop screen.
     */
    @Override
    public void onFilesDrop(List<Path> paths) {
        for (Path path : paths) {
            try {
                byte[] data = Files.readAllBytes(path);
                SourceImage source = SourceImage.decode(data);
                List<int[]> panels = new ArrayList<>();
                for (int[] frame : source.frames()) {
                    List<int[]> panel = CapeManager.extractCapeFrames(frame, source.width(), source.height());
                    if (panel == null) {
                        panels = null;
                        break;
                    }
                    panels.addAll(panel);
                }
                if (panels != null && !panels.isEmpty()) {
                    int s = CapeManager.layoutScale(source.width());
                    boolean animatedStrip = source.frames().size() == 1 && panels.size() > 1;
                    pushUndo();
                    CapeManager.importFrames(panels, 10 * s, 16 * s, animatedStrip ? 10 : source.fps());
                    rebuildWidgets();
                } else {
                    minecraft.setScreen(new CropScreen(this, source, this::onCropped));
                }
                return;
            } catch (Exception e) {
                CapeCreatorClient.LOGGER.warn("Could not import {} as a cape", path, e);
            }
        }
    }

    private void openLinkImport(String url) {
        String clip = url;
        if (clip == null) {
            String c = minecraft.keyboardHandler.getClipboard();
            clip = UrlImportScreen.looksLikeUrl(c) ? c.trim() : "";
        }
        minecraft.setScreen(new UrlImportScreen(this, clip, this::onCropped));
    }

    /** Called by the crop screen with the chosen part of the image. */
    private void onCropped(CropScreen.Result result) {
        pushUndo();
        CapeManager.importFrames(result.frames(), result.width(), result.height(), result.fps());
        minecraft.setScreen(this);
    }

    @Override
    public void onClose() {
        if (cancelled) {
            CapeManager.restore(original);
            CapeManager.setEnabled(originalEnabled);
        } else {
            CapeManager.save();
            if (CapeManager.currentName() != null) {
                try {
                    com.adhrit.capecreator.CapeLibrary.updateSideColors(CapeManager.currentName());
                } catch (Exception e) {
                    CapeCreatorClient.LOGGER.warn("Could not save side colours for {}", CapeManager.currentName(), e);
                }
            }
        }
        super.onClose();
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);

        renderCanvas(context, mouseX, mouseY);
        renderPalette(context);
        preview.setBounds(previewX1, canvasY, previewX2, height - 32);
        preview.draw(context, font);


        int frameRowY = canvasY + 5 * 22;
        context.drawCenteredString(font,
                Component.literal((CapeManager.editFrame() + 1) + "/" + CapeManager.frameCount()),
                panelX + 20 + (PANEL_W - 80) / 2, frameRowY + 6, 0xFFFFFFFF);
    }

    private void renderCanvas(GuiGraphics context, int mouseX, int mouseY) {
        int w = CapeManager.width(), h = CapeManager.height();
        outline(context, canvasX - 1, canvasY - 1, canvasW + 2, canvasH + 2, 0xFFFFFFFF);
        if (cell >= 1) {
            int c = (int) cell;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = canvasX + x * c, py = canvasY + y * c;
                    context.fill(px, py, px + c, py + c, CapeManager.get(x, y));
                }
            }
        } else {
            // Design is bigger than the canvas: draw one rectangle per run of same-coloured screen pixels.
            for (int sy = 0; sy < canvasH; sy++) {
                int dy = Math.min(h - 1, (int) (sy / cell));
                int runStart = 0, runColor = CapeManager.get(0, dy);
                for (int sx = 1; sx <= canvasW; sx++) {
                    int color = sx == canvasW ? ~runColor : CapeManager.get(Math.min(w - 1, (int) (sx / cell)), dy);
                    if (color != runColor) {
                        context.fill(canvasX + runStart, canvasY + sy, canvasX + sx, canvasY + sy + 1, runColor);
                        runStart = sx;
                        runColor = color;
                    }
                }
            }
        }
        if (cell >= 6) {
            int c = (int) cell;
            for (int x = 1; x < w; x++) {
                context.fill(canvasX + x * c, canvasY, canvasX + x * c + 1, canvasY + canvasH, 0x30000000);
            }
            for (int y = 1; y < h; y++) {
                context.fill(canvasX, canvasY + y * c, canvasX + canvasW, canvasY + y * c + 1, 0x30000000);
            }
        }
        if (mirror) {
            int mx = canvasX + canvasW / 2;
            context.fill(mx, canvasY, mx + 1, canvasY + canvasH, 0x80FF00FF);
        }
        int[] hover = cellAt(mouseX, mouseY);
        if (hover != null && cell >= 2) {
            int c = (int) cell;
            outline(context, canvasX + hover[0] * c, canvasY + hover[1] * c, c, c, 0xFFFFFFFF);
        }
    }

    private void renderPalette(GuiGraphics context) {
        int py = paletteY();
        for (int i = 0; i < PALETTE.length; i++) {
            int x = panelX + (i % 8) * SWATCH, y = py + (i / 8) * SWATCH;
            context.fill(x, y, x + SWATCH, y + SWATCH, PALETTE[i]);
            if (PALETTE[i] == color) outline(context, x, y, SWATCH, SWATCH, 0xFFFFFFFF);
        }
        int sy = py + 3 * SWATCH + 6;
        outline(context, panelX, sy, 18, 18, 0xFFFFFFFF);
        context.fill(panelX + 1, sy + 1, panelX + 17, sy + 17, color);
    }

    private static void outline(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
