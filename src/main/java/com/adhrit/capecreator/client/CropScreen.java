package com.adhrit.capecreator.client;

import com.adhrit.capecreator.CapeCreatorClient;
import com.adhrit.capecreator.CapeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the player choose which part of an image becomes the cape. The crop box always has the
 * cape's 10:16 shape: drag it to move, scroll (or use the buttons) to resize. The animation plays
 * underneath, with a live preview of the result.
 */
public class CropScreen extends Screen {
    private static final Identifier TEXTURE_ID = Identifier.of(CapeCreatorClient.MOD_ID, "crop_source");

    private final Screen parent;
    private final SourceImage source;
    private final Consumer<Result> onDone;

    // crop box in source pixels; height is always width * 16 / 10
    private float cropX, cropY, cropW;
    private int viewX, viewY, viewW, viewH;
    private float viewScale;
    private int previewX, previewY, previewW, previewH;
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;

    private NativeImageBackedTexture texture;
    private int shownFrame = -1;

    public record Result(List<int[]> frames, int width, int height, int fps) {}

    public CropScreen(Screen parent, SourceImage source, Consumer<Result> onDone) {
        super(Text.literal("Crop your cape"));
        this.parent = parent;
        this.source = source;
        this.onDone = onDone;
        fit();
    }

    private float maxCropW() {
        return Math.min(source.width(), source.height() * CapeManager.BASE_W / (float) CapeManager.BASE_H);
    }

    private float cropH() {
        return cropW * CapeManager.BASE_H / CapeManager.BASE_W;
    }

    /** Largest box that fits, centred. */
    private void fit() {
        cropW = maxCropW();
        cropX = (source.width() - cropW) / 2;
        cropY = (source.height() - cropH()) / 2;
    }

    private void clampCrop() {
        float min = Math.min(maxCropW(), CapeManager.BASE_W);
        cropW = Math.max(min, Math.min(cropW, maxCropW()));
        cropX = Math.max(0, Math.min(cropX, source.width() - cropW));
        cropY = Math.max(0, Math.min(cropY, source.height() - cropH()));
    }

    private void resize(float factor) {
        float cx = cropX + cropW / 2, cy = cropY + cropH() / 2;
        cropW *= factor;
        clampCrop();
        cropX = cx - cropW / 2;
        cropY = cy - cropH() / 2;
        clampCrop();
    }

    @Override
    protected void init() {
        int areaW = width - 150, areaH = height - 70;
        viewScale = Math.min(areaW / (float) source.width(), areaH / (float) source.height());
        viewW = Math.max(1, Math.round(source.width() * viewScale));
        viewH = Math.max(1, Math.round(source.height() * viewScale));
        viewX = 16 + (areaW - viewW) / 2;
        viewY = 30 + (areaH - viewH) / 2;

        int px = width - 122;
        previewW = 64;
        previewH = previewW * CapeManager.BASE_H / CapeManager.BASE_W;
        previewX = px + (106 - previewW) / 2;
        previewY = 30;

        int y = previewY + previewH + 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> resize(1.15f)).dimensions(px, y, 34, 20).build())
                .setTooltip(Tooltip.of(Text.literal("Zoom out (bigger box, shows more of the image)")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Fit"), b -> fit()).dimensions(px + 36, y, 34, 20).build())
                .setTooltip(Tooltip.of(Text.literal("Biggest box that fits, centred")));
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> resize(1 / 1.15f)).dimensions(px + 72, y, 34, 20).build())
                .setTooltip(Tooltip.of(Text.literal("Zoom in (smaller box, more detail)")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Use this crop"), b -> finish())
                .dimensions(px, height - 50, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(px, height - 26, 106, 20).build());

        if (texture == null) {
            texture = new NativeImageBackedTexture(() -> "capecreator crop source", source.width(), source.height(), false);
            texture.setFilter(true, false);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
            shownFrame = -1;
        }
    }

    private int outputScale() {
        int scale = CapeManager.SCALES[0];
        for (int s : CapeManager.SCALES) {
            if (s * CapeManager.BASE_W <= Math.round(cropW)) scale = s;
        }
        return scale;
    }

    private void finish() {
        int x = Math.round(cropX), y = Math.round(cropY);
        int w = Math.max(1, Math.min(Math.round(cropW), source.width() - x));
        int h = Math.max(1, Math.min(Math.round(cropH()), source.height() - y));
        onDone.accept(new Result(source.crop(x, y, w, h), w, h, source.fps()));
    }

    private void updateTexture() {
        int frame = source.frames().size() == 1 ? 0
                : (int) ((Util.getMeasuringTimeMs() * source.fps() / 1000) % source.frames().size());
        if (frame == shownFrame) return;
        shownFrame = frame;
        NativeImage img = texture.getImage();
        int[] px = source.frames().get(frame);
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                img.setColorArgb(x, y, 0xFF000000 | px[y * source.width() + x]);
            }
        }
        texture.upload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        updateTexture();
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);

        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE_ID, viewX, viewY, 0, 0, viewW, viewH,
                source.width(), source.height(), source.width(), source.height());

        // dim everything outside the crop box, then outline it
        int bx1 = viewX + Math.round(cropX * viewScale), by1 = viewY + Math.round(cropY * viewScale);
        int bx2 = viewX + Math.round((cropX + cropW) * viewScale), by2 = viewY + Math.round((cropY + cropH()) * viewScale);
        int dim = 0xA0000000;
        context.fill(viewX, viewY, viewX + viewW, by1, dim);
        context.fill(viewX, by2, viewX + viewW, viewY + viewH, dim);
        context.fill(viewX, by1, bx1, by2, dim);
        context.fill(bx2, by1, viewX + viewW, by2, dim);
        outline(context, bx1 - 1, by1 - 1, bx2 - bx1 + 2, by2 - by1 + 2, 0xFFFFFFFF);

        // live preview of the cape
        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), previewX, previewY - 11, 0xFFA0A0A0);
        outline(context, previewX - 1, previewY - 1, previewW + 2, previewH + 2, 0xFFFFFFFF);
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE_ID, previewX, previewY, cropX, cropY,
                previewW, previewH, Math.round(cropW), Math.round(cropH()), source.width(), source.height());

        int s = outputScale();
        context.drawTextWithShadow(textRenderer, Text.literal("Quality: " + s + "x"), width - 122, previewY + previewH + 8, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal(source.frames().size() + " frame" + (source.frames().size() == 1 ? "" : "s")),
                width - 122, previewY + previewH + 48, 0xFFA0A0A0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Drag the box to move it, scroll to resize"),
                viewX + viewW / 2, height - 20, 0xFFA0A0A0);
    }

    private boolean insideBox(double mx, double my) {
        double sx = (mx - viewX) / viewScale, sy = (my - viewY) / viewScale;
        return sx >= cropX && sx <= cropX + cropW && sy >= cropY && sy <= cropY + cropH();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && insideBox(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (mouseX - viewX) / viewScale - cropX;
            dragOffsetY = (mouseY - viewY) / viewScale - cropY;
            return true;
        }
        if (button == 0 && mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH) {
            // click outside the box: centre it there
            cropX = (float) ((mouseX - viewX) / viewScale - cropW / 2);
            cropY = (float) ((mouseY - viewY) / viewScale - cropH() / 2);
            clampCrop();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            cropX = (float) ((mouseX - viewX) / viewScale - dragOffsetX);
            cropY = (float) ((mouseY - viewY) / viewScale - dragOffsetY);
            clampCrop();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            resize(verticalAmount > 0 ? 1 / 1.08f : 1.08f);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        if (texture != null) {
            client.getTextureManager().destroyTexture(TEXTURE_ID);
            texture = null;
        }
    }

    private static void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
