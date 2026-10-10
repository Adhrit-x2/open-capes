package com.adhrit.capecreator.client;

import com.adhrit.capecreator.CapeManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/** A decoded image or animation, before it is cropped into a cape: full-size ARGB frames. */
public record SourceImage(List<int[]> frames, int width, int height, int fps) {
    /** Decodes a GIF (all frames), PNG or JPG. */
    public static SourceImage decode(byte[] data) throws IOException {
        if (data.length >= 4 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            GifImporter.Result gif = GifImporter.read(data, CapeManager.MAX_FRAMES);
            if (gif.frames().isEmpty()) throw new IOException("GIF has no frames");
            return new SourceImage(gif.frames(), gif.width(), gif.height(), gif.fps());
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new IOException("Unsupported image type. Use a GIF, PNG or JPG link.");
        int w = image.getWidth(), h = image.getHeight();
        return new SourceImage(List.of(image.getRGB(0, 0, w, h, null, 0, w)), w, h, CapeManager.fps());
    }

    /** Cuts the rectangle (x, y, w, h) out of every frame. */
    public List<int[]> crop(int x, int y, int w, int h) {
        return frames.stream().map(src -> {
            int[] out = new int[w * h];
            for (int row = 0; row < h; row++) {
                System.arraycopy(src, (y + row) * width + x, out, row * w, w);
            }
            return out;
        }).toList();
    }
}
