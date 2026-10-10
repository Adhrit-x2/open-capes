package com.adhrit.capecreator.client;

import com.adhrit.capecreator.CapeCreatorClient;
import com.adhrit.capecreator.CapeLibrary;
import com.adhrit.capecreator.CapeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Grid of saved capes: click one to wear it, save the current cape, or delete one. */
public class SavedCapesScreen extends Screen {
    private static final int THUMB_W = 40, THUMB_H = 64, TILE_W = 48, TILE_H = 72;

    private List<CapeLibrary.Entry> entries = List.of();
    private final List<Identifier> thumbs = new ArrayList<>();
    private int gridX, gridY, columns, visibleRows, scrollRow;
    private TextFieldWidget nameField;
    private ButtonWidget deleteButton;
    private Text status = Text.empty();

    public SavedCapesScreen() {
        super(Text.literal("Saved Capes"));
    }

    @Override
    protected void init() {
        reload();
        columns = Math.max(1, (width - 32) / TILE_W);
        visibleRows = Math.max(1, (height - 30 - 64) / TILE_H);
        gridX = (width - columns * TILE_W) / 2;
        gridY = 28;

        int by = height - 50;
        nameField = new TextFieldWidget(textRenderer, width / 2 - 154, by, 150, 20, Text.literal("Name"));
        nameField.setMaxLength(40);
        nameField.setPlaceholder(Text.literal("Name for this cape"));
        addDrawableChild(nameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save current cape"), b -> saveCurrent())
                .dimensions(width / 2 - 2, by, 156, 20).build());

        by += 24;
        deleteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> confirmDelete())
                .dimensions(width / 2 - 154, by, 100, 20).build());
        deleteButton.setTooltip(Tooltip.of(Text.literal("Delete the cape you're wearing from your saved capes")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Folder"), b -> {
            try {
                Files.createDirectories(CapeLibrary.directory());
            } catch (Exception ignored) {
            }
            Util.getOperatingSystem().open(CapeLibrary.directory());
        }).dimensions(width / 2 - 50, by, 100, 20).build()).setTooltip(Tooltip.of(Text.literal("You can also drop GIFs into this folder")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 54, by, 100, 20).build());
        updateButtons();
    }

    private void reload() {
        freeThumbs();
        entries = CapeLibrary.list();
        for (int i = 0; i < entries.size(); i++) {
            Identifier id = Identifier.of(CapeCreatorClient.MOD_ID, "saved_thumb_" + i);
            try {
                NativeImage img = thumbnail(entries.get(i));
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "capecreator thumb", img));
                thumbs.add(id);
            } catch (Exception e) {
                CapeCreatorClient.LOGGER.warn("Could not read saved cape {}", entries.get(i).file(), e);
                thumbs.add(null);
            }
        }
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
    }

    /** First frame of a saved cape, cropped to the cape shape and shrunk to 40x64. */
    private static NativeImage thumbnail(CapeLibrary.Entry entry) throws Exception {
        int[] argb;
        int w, h, frameH;
        if (entry.isGif()) {
            try (ImageInputStream in = ImageIO.createImageInputStream(entry.file().toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    BufferedImage first = reader.read(0);
                    w = first.getWidth();
                    h = first.getHeight();
                    argb = first.getRGB(0, 0, w, h, null, 0, w);
                } finally {
                    reader.dispose();
                }
            }
            frameH = h;
        } else {
            try (InputStream in = Files.newInputStream(entry.file()); NativeImage img = NativeImage.read(in)) {
                w = img.getWidth();
                h = img.getHeight();
                argb = CapeManager.toArgb(img);
            }
            frameH = Math.min(h, w * CapeManager.BASE_H / CapeManager.BASE_W);
        }
        // centre crop to 10:16 within the first frame
        int cw = Math.min(w, frameH * CapeManager.BASE_W / CapeManager.BASE_H);
        int ch = cw * CapeManager.BASE_H / CapeManager.BASE_W;
        int ox = (w - cw) / 2, oy = (frameH - ch) / 2;
        NativeImage out = new NativeImage(THUMB_W, THUMB_H, false);
        for (int y = 0; y < THUMB_H; y++) {
            for (int x = 0; x < THUMB_W; x++) {
                out.setColorArgb(x, y, 0xFF000000 | argb[(oy + y * ch / THUMB_H) * w + ox + x * cw / THUMB_W]);
            }
        }
        return out;
    }

    private int maxScroll() {
        int rows = (entries.size() + columns - 1) / Math.max(1, columns);
        return Math.max(0, rows - visibleRows);
    }

    private void updateButtons() {
        if (deleteButton != null) deleteButton.active = CapeManager.currentName() != null;
    }

    private void saveCurrent() {
        String name = nameField.getText().isBlank() ? CapeLibrary.uniqueName("cape") : nameField.getText();
        try {
            CapeLibrary.Entry saved = CapeLibrary.saveCurrent(name);
            status = Text.literal("Saved \"" + saved.name() + "\"");
            nameField.setText("");
            reload();
        } catch (Exception e) {
            CapeCreatorClient.LOGGER.warn("Could not save cape", e);
            status = Text.literal("Couldn't save: " + e.getMessage());
        }
        updateButtons();
    }

    private void confirmDelete() {
        String current = CapeManager.currentName();
        CapeLibrary.Entry entry = entries.stream().filter(e -> e.name().equals(current)).findFirst().orElse(null);
        if (entry == null) return;
        client.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    CapeLibrary.delete(entry);
                    status = Text.literal("Deleted \"" + entry.name() + "\"");
                } catch (Exception e) {
                    status = Text.literal("Couldn't delete: " + e.getMessage());
                }
            }
            client.setScreen(this);
        }, Text.literal("Delete \"" + entry.name() + "\"?"), Text.literal("This removes it from your saved capes. The cape you're wearing stays on.")));
    }

    private int tileAt(double mx, double my) {
        if (mx < gridX || my < gridY) return -1;
        int col = (int) ((mx - gridX) / TILE_W), row = (int) ((my - gridY) / TILE_H);
        if (col >= columns || row >= visibleRows) return -1;
        int i = (row + scrollRow) * columns + col;
        return i < entries.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int i = tileAt(mouseX, mouseY);
        if (i >= 0 && button == 0) {
            try {
                CapeLibrary.wear(entries.get(i));
                status = Text.literal("Wearing \"" + entries.get(i).name() + "\"");
            } catch (Exception e) {
                CapeCreatorClient.LOGGER.warn("Could not wear {}", entries.get(i).file(), e);
                status = Text.literal("Couldn't load that cape: " + e.getMessage());
            }
            updateButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No saved capes yet. Save the one you're wearing below."),
                    width / 2, height / 2 - 20, 0xFFA0A0A0);
        }
        int hovered = tileAt(mouseX, mouseY);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                int i = (row + scrollRow) * columns + col;
                if (i >= entries.size()) break;
                int x = gridX + col * TILE_W + (TILE_W - THUMB_W) / 2, y = gridY + row * TILE_H + 2;
                boolean wearing = entries.get(i).name().equals(CapeManager.currentName());
                int border = wearing ? 0xFF55FF55 : i == hovered ? 0xFFFFFFFF : 0xFF404040;
                outline(context, x - 2, y - 2, THUMB_W + 4, THUMB_H + 4, border);
                if (wearing) outline(context, x - 1, y - 1, THUMB_W + 2, THUMB_H + 2, border);
                Identifier thumb = thumbs.get(i);
                if (thumb != null) {
                    context.drawTexture(RenderLayer::getGuiTextured, thumb, x, y, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
                } else {
                    context.fill(x, y, x + THUMB_W, y + THUMB_H, 0xFF300000);
                }
            }
        }
        if (hovered >= 0) {
            context.drawTooltip(textRenderer, Text.literal(entries.get(hovered).name()), mouseX, mouseY);
        }
        if (maxScroll() > 0) {
            context.drawTextWithShadow(textRenderer, Text.literal("Scroll for more"), 8, height - 64, 0xFFA0A0A0);
        }
        String wearing = CapeManager.currentName();
        Text line = !status.getString().isEmpty() ? status
                : Text.literal(wearing != null ? "Wearing \"" + wearing + "\"" : "Click a cape to wear it");
        context.drawCenteredTextWithShadow(textRenderer, line, width / 2, height - 62, 0xFFE0E0E0);
    }

    @Override
    public void close() {
        // back to a fresh editor so it picks up the cape that's now on
        client.setScreen(new CapeEditorScreen());
    }

    @Override
    public void removed() {
        freeThumbs();
    }

    private void freeThumbs() {
        for (Identifier id : thumbs) {
            if (id != null) client.getTextureManager().destroyTexture(id);
        }
        thumbs.clear();
    }

    private static void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
