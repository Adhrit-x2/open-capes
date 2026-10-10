package com.adhrit.capecreator;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Holds the player's cape design and turns it into a texture Minecraft can render.
 *
 * The design is one or more animation frames, each a grid of (10 * scale) x (16 * scale) ARGB
 * pixels — the part of the cape you see from behind. The frame currently on screen gets laid out
 * onto a standard (64 * scale) x (32 * scale) cape texture, including the inside, the edges and
 * the elytra wings. With more than one frame the texture is re-uploaded as the animation advances.
 */
public final class CapeManager {
    public static final int BASE_W = 10;
    public static final int BASE_H = 16;
    public static final int[] SCALES = {1, 2, 4, 8, 16};
    public static final int[] FPS_OPTIONS = {1, 2, 4, 6, 8, 12, 16, 20};
    public static final int MAX_FRAMES = 64;

    public static final Identifier TEXTURE_ID = Identifier.of(CapeCreatorClient.MOD_ID, "custom_cape");
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(CapeCreatorClient.MOD_ID);
    private static final Path DESIGN_FILE = DIR.resolve("design.png");
    private static final Path SETTINGS_FILE = DIR.resolve("settings.properties");

    private static int scale = 1;
    private static List<int[]> frames = new ArrayList<>(List.of(defaultDesign(1)));
    private static int editFrame = 0;
    private static int fps = 8;
    private static boolean enabled = true;
    private static String currentName;
    // colours for the inside and the edges of the cape; null = automatic (main background colour)
    private static Integer insideColor;
    private static Integer edgeColor;

    // the most detailed version of the frames, kept while only the size is being switched
    private static List<int[]> detailFrames;
    private static int detailScale;

    private static Integer backgroundColor;   // cached; cleared whenever the design changes

    private static NativeImageBackedTexture texture;
    private static boolean dirty = true;
    private static int shownFrame = -1;

    /** Everything needed to undo an edit or cancel the editor. */
    public record State(List<int[]> frames, int scale, int editFrame, int fps, Integer insideColor, Integer edgeColor) {}

    private CapeManager() {}

    public static int width() { return BASE_W * scale; }
    public static int height() { return BASE_H * scale; }
    public static int scale() { return scale; }
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int fps() { return fps; }

    /** Custom inside colour, or null for automatic. */
    public static Integer insideColor() { return insideColor; }
    public static Integer edgeColor() { return edgeColor; }
    public static void setInsideColor(Integer argb) { insideColor = argb == null ? null : 0xFF000000 | argb; dirty = true; }
    public static void setEdgeColor(Integer argb) { edgeColor = argb == null ? null : 0xFF000000 | argb; dirty = true; }
    public static int effectiveInsideColor() { return insideColor != null ? insideColor : backgroundColor(); }
    public static int effectiveEdgeColor() { return edgeColor != null ? edgeColor : backgroundColor(); }

    /** Reads "auto" or a hex colour; anything else means automatic. */
    public static Integer parseSideColor(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("auto")) return null;
        try {
            return 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatSideColor(Integer argb) {
        return argb == null ? "auto" : String.format("#%06X", argb & 0xFFFFFF);
    }
    public static void setFps(int value) { fps = value; }

    // ---- frames ----

    public static int frameCount() { return frames.size(); }
    public static int editFrame() { return editFrame; }

    public static void selectFrame(int index) {
        editFrame = Math.floorMod(index, frames.size());
    }

    /** Inserts a copy of the current frame right after it and selects the copy. */
    public static boolean duplicateFrame() {
        if (frames.size() >= MAX_FRAMES) return false;
        frames.add(editFrame + 1, frames.get(editFrame).clone());
        editFrame++;
        edited();
        return true;
    }

    public static boolean deleteFrame() {
        if (frames.size() <= 1) return false;
        frames.remove(editFrame);
        editFrame = Math.min(editFrame, frames.size() - 1);
        edited();
        return true;
    }

    /** Pixel of the frame being edited. */
    public static int get(int x, int y) { return get(editFrame, x, y); }

    public static int get(int frame, int x, int y) { return frames.get(frame)[y * width() + x]; }

    public static void set(int x, int y, int argb) {
        frames.get(editFrame)[y * width() + x] = 0xFF000000 | argb;
        edited();
    }

    public static State snapshot() {
        List<int[]> copy = new ArrayList<>();
        for (int[] f : frames) copy.add(f.clone());
        return new State(copy, scale, editFrame, fps, insideColor, edgeColor);
    }

    public static void restore(State state) {
        frames = new ArrayList<>();
        for (int[] f : state.frames()) frames.add(f.clone());
        scale = state.scale();
        editFrame = Math.min(state.editFrame(), frames.size() - 1);
        fps = state.fps();
        insideColor = state.insideColor();
        edgeColor = state.edgeColor();
        edited();
    }

    /**
     * Changes resolution. Switching sizes without editing is lossless: the most detailed version
     * is kept aside and every size is resampled from it, so going 16x -> 1x -> 16x gets the
     * original detail back. Shrinking averages pixels; enlarging repeats them.
     */
    public static void setScale(int newScale) {
        if (newScale == scale) return;
        if (detailFrames == null || detailScale < scale) {
            detailFrames = frames;
            detailScale = scale;
        }
        List<int[]> resized = new ArrayList<>();
        for (int[] src : detailFrames) resized.add(resample(src, detailScale, newScale));
        frames = resized;
        scale = newScale;
        backgroundColor = null;
        dirty = true;
    }

    private static int[] resample(int[] src, int from, int to) {
        int sw = BASE_W * from, w = BASE_W * to, h = BASE_H * to;
        if (from == to) return src.clone();
        int[] out = new int[w * h];
        if (to > from) {
            int k = to / from;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) out[y * w + x] = src[(y / k) * sw + x / k];
            }
            return out;
        }
        int k = from / to, n = k * k;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int dy = 0; dy < k; dy++) {
                    for (int dx = 0; dx < k; dx++) {
                        int c = src[(y * k + dy) * sw + x * k + dx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                out[y * w + x] = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
        return out;
    }

    /** Any real edit makes the kept-aside detailed version stale. */
    private static void edited() {
        backgroundColor = null;
        detailFrames = null;
        currentName = null;
        dirty = true;
    }

    /** Copies an image into the current frame, stretched to fit the grid. */
    public static void importImage(NativeImage image) {
        int[] argb = toArgb(image);
        int[] crop = capeCrop(image.getWidth(), image.getHeight());
        int w = width(), h = height();
        int[] f = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                f[y * w + x] = 0xFF000000 | argb[(crop[1] + y * crop[3] / h) * image.getWidth() + crop[0] + x * crop[2] / w];
            }
        }
        frames.set(editFrame, f);
        edited();
    }

    /** The centred region {x, y, w, h} of a srcW x srcH image that has the cape's 10:16 shape. */
    private static int[] capeCrop(int srcW, int srcH) {
        if (srcW * BASE_H > srcH * BASE_W) {
            int cw = Math.max(1, srcH * BASE_W / BASE_H);
            return new int[]{(srcW - cw) / 2, 0, cw, srcH};
        }
        int ch = Math.max(1, srcW * BASE_H / BASE_W);
        return new int[]{0, (srcH - ch) / 2, srcW, ch};
    }

    /**
     * Replaces the whole animation with the given ARGB frames (each srcW x srcH), picking the
     * resolution closest to the source and stretching to fit.
     */
    public static void importFrames(List<int[]> argbFrames, int srcW, int srcH, int newFps) {
        if (argbFrames.isEmpty()) return;
        int[] crop = capeCrop(srcW, srcH);
        scale = nearestScale(Math.max(1, crop[2] / BASE_W));
        int w = width(), h = height();
        List<int[]> result = new ArrayList<>();
        for (int[] src : argbFrames.subList(0, Math.min(argbFrames.size(), MAX_FRAMES))) {
            int[] f = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    f[y * w + x] = 0xFF000000 | src[(crop[1] + y * crop[3] / h) * srcW + crop[0] + x * crop[2] / w];
                }
            }
            result.add(f);
        }
        frames = result;
        editFrame = 0;
        fps = newFps;
        insideColor = null;
        edgeColor = null;
        edited();
    }

    /**
     * If an image uses the standard cape texture layout, returns the back panel of each frame
     * (each 10s x 16s, where s = pixels per cape pixel), otherwise null. Recognised layouts:
     * 64x32 (any multiple, e.g. 128x64 HD) with animation frames stacked vertically, as used by
     * cape sites, and the legacy 22x17 cape-only layout.
     */
    public static List<int[]> extractCapeFrames(int[] argb, int w, int h) {
        int frameH;
        if (w >= 64 && w % 64 == 0 && h % (w / 2) == 0) {
            frameH = w / 2;
        } else if (w >= 22 && w % 22 == 0 && h * 22 == w * 17) {
            frameH = h;
        } else {
            return null;
        }
        int s = layoutScale(w);
        int cw = 10 * s, ch = 16 * s;
        List<int[]> result = new ArrayList<>();
        for (int f = 0; f < h / frameH && f < MAX_FRAMES; f++) {
            int[] out = new int[cw * ch];
            for (int y = 0; y < ch; y++) {
                System.arraycopy(argb, (f * frameH + s + y) * w + s, out, y * cw, cw);
            }
            result.add(out);
        }
        return result;
    }

    /** Pixels per cape pixel for an image already recognised by {@link #extractCapeFrames}. */
    public static int layoutScale(int width) {
        return width % 64 == 0 ? width / 64 : width / 22;
    }

    public static int[] toArgb(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) argb[y * w + x] = image.getColorArgb(x, y);
        }
        return argb;
    }

    private static int[] sample(NativeImage image, int top, int regionH) {
        int w = width(), h = height();
        int[] f = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                f[y * w + x] = 0xFF000000 | image.getColorArgb(x * image.getWidth() / w, top + y * regionH / h);
            }
        }
        return f;
    }

    // ---- texture ----

    /** Called from the mixin; returns the texture to use as the local player's cape. */
    public static Identifier getTextureId() {
        int frame = frames.size() == 1 ? 0 : (int) ((Util.getMeasuringTimeMs() * fps / 1000) % frames.size());
        if (dirty || frame != shownFrame) rebuildTexture(frame);
        return TEXTURE_ID;
    }

    private static void rebuildTexture(int frame) {
        dirty = false;
        shownFrame = frame;
        int s = scale;
        int texW = 64 * s, texH = 32 * s;
        if (texture == null || texture.getImage() == null
                || texture.getImage().getWidth() != texW || texture.getImage().getHeight() != texH) {
            texture = new NativeImageBackedTexture(() -> "capecreator custom cape", texW, texH, true);
            // Smooth filtering suits detailed 8x/16x images; small pixel-art capes stay crisp.
            texture.setFilter(s >= 8, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
        }
        NativeImage img = texture.getImage();
        img.fillRect(0, 0, texW, texH, 0);

        int w = width(), h = height();
        // Edges (left, right, top, bottom faces) and the inside: custom colours, or the main background colour.
        int edge = effectiveEdgeColor(), inside = effectiveInsideColor();
        img.fillRect(0, 0, 22 * s, 17 * s, edge);
        img.fillRect(12 * s, s, 10 * s, 16 * s, inside);
        // Outside of the cape (seen from behind the player): the design itself.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setColorArgb(s + x, s + y, get(frame, x, y));
            }
        }

        // Elytra region (22..46 x 0..22): fill every face from the design, then put the
        // design itself, stretched, on the outer face of the wing (24..34 x 2..22).
        int ex = 22 * s, ew = 24 * s, eh = 22 * s;
        for (int y = 0; y < eh; y++) {
            for (int x = 0; x < ew; x++) {
                img.setColorArgb(ex + x, y, get(frame, x * w / ew, y * h / eh));
            }
        }
        int fx = 24 * s, fy = 2 * s, fw = 10 * s, fh = 20 * s;
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                img.setColorArgb(fx + x, fy + y, get(frame, x * w / fw, y * h / fh));
            }
        }

        texture.upload();
    }

    /**
     * The cape's main background colour: the most common colour across all frames (similar shades
     * grouped together, then averaged), so it stays steady while an animation plays. Big HD capes
     * are sampled rather than counted pixel by pixel.
     */
    public static int backgroundColor() {
        if (backgroundColor != null) return backgroundColor;
        int total = width() * height() * frames.size();
        int stride = Math.max(1, total / 250_000);
        int[] count = new int[4096];
        long[] sumR = new long[4096], sumG = new long[4096], sumB = new long[4096];
        int seen = 0;
        for (int[] f : frames) {
            for (int i = (seen % stride); i < f.length; i += stride) {
                int c = f[i];
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                int bucket = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                count[bucket]++;
                sumR[bucket] += r;
                sumG[bucket] += g;
                sumB[bucket] += b;
            }
            seen += f.length;
        }
        int best = 0;
        for (int i = 1; i < count.length; i++) {
            if (count[i] > count[best]) best = i;
        }
        int n = Math.max(1, count[best]);
        backgroundColor = 0xFF000000 | (int) (sumR[best] / n) << 16 | (int) (sumG[best] / n) << 8 | (int) (sumB[best] / n);
        return backgroundColor;
    }

    private static int[] defaultDesign(int s) {
        int w = BASE_W * s, h = BASE_H * s;
        int[] data = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean border = x < s || x >= w - s || y < s || y >= h - s;
                data[y * w + x] = border ? 0xFFF2C94C : 0xFF2B4C9B;
            }
        }
        return data;
    }

    // ---- persistence ----

    /** Loads design.png, which stores the frames stacked vertically, plus settings.properties. */
    public static void load() {
        try {
            if (Files.exists(SETTINGS_FILE)) {
                Properties props = new Properties();
                try (Reader r = Files.newBufferedReader(SETTINGS_FILE)) {
                    props.load(r);
                }
                enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
                fps = Integer.parseInt(props.getProperty("fps", "8"));
                currentName = props.getProperty("current");
                insideColor = parseSideColor(props.getProperty("inside"));
                edgeColor = parseSideColor(props.getProperty("edges"));
            }
            String name = currentName;
            if (Files.exists(DESIGN_FILE)) readStrip(DESIGN_FILE);
            currentName = name;
        } catch (IOException | RuntimeException e) {
            CapeCreatorClient.LOGGER.error("Failed to load cape design", e);
        }
        detailFrames = null;
        dirty = true;
    }

    /** Reads a design PNG: frames of (10s x 16s) stacked vertically. */
    public static void readStrip(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            scale = nearestScale(Math.max(1, image.getWidth() / BASE_W));
            int frameH = image.getWidth() * BASE_H / BASE_W;
            int count = Math.max(1, Math.min(MAX_FRAMES, image.getHeight() / frameH));
            frames = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                frames.add(sample(image, i * frameH, Math.min(frameH, image.getHeight())));
            }
            editFrame = 0;
        }
        edited();
    }

    /** Writes the current design as a PNG with frames stacked vertically. */
    public static void writeStrip(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        int w = width(), h = height();
        try (NativeImage image = new NativeImage(w, h * frames.size(), false)) {
            for (int i = 0; i < frames.size(); i++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        image.setColorArgb(x, i * h + y, get(i, x, y));
                    }
                }
            }
            image.writeTo(file);
        }
    }

    public static void save() {
        try {
            writeStrip(DESIGN_FILE);
            Properties props = new Properties();
            props.setProperty("enabled", Boolean.toString(enabled));
            props.setProperty("fps", Integer.toString(fps));
            if (currentName != null) props.setProperty("current", currentName);
            props.setProperty("inside", formatSideColor(insideColor));
            props.setProperty("edges", formatSideColor(edgeColor));
            try (Writer wr = Files.newBufferedWriter(SETTINGS_FILE)) {
                props.store(wr, "Cape Creator settings");
            }
        } catch (IOException e) {
            CapeCreatorClient.LOGGER.error("Failed to save cape design", e);
        }
    }

    /** Name of the saved cape being worn, or null once it's been edited. */
    public static String currentName() { return currentName; }

    public static void setCurrentName(String name) { currentName = name; }

    public static Path directory() { return DIR; }

    private static int nearestScale(int s) {
        int best = SCALES[0];
        for (int candidate : SCALES) {
            if (candidate <= s) best = candidate;
        }
        return best;
    }
}
