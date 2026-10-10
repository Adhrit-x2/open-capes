package com.adhrit.capecreator.client;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reads an animated GIF into full-size ARGB frames, compositing partial frames over the previous one.
 * Long GIFs keep every n-th frame so the whole loop fits in maxFrames at its original speed.
 */
final class GifImporter {
    record Result(List<int[]> frames, int width, int height, int fps) {}

    private GifImporter() {}

    static Result read(Path path, int maxFrames) throws IOException {
        return read(Files.readAllBytes(path), maxFrames);
    }

    static Result read(byte[] data, int maxFrames) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF reader available");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int count = reader.getNumImages(true);
                int step = (count + maxFrames - 1) / maxFrames;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                int[] canvas = new int[width * height];
                List<int[]> frames = new ArrayList<>();
                int totalDelayCs = 0;

                for (int i = 0; i < count; i++) {
                    BufferedImage img = reader.read(i);
                    Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    int left = intAttr(root, "ImageDescriptor", "imageLeftPosition");
                    int top = intAttr(root, "ImageDescriptor", "imageTopPosition");
                    totalDelayCs += Math.max(1, intAttr(root, "GraphicControlExtension", "delayTime"));

                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int cx = left + x, cy = top + y;
                            if (cx >= width || cy >= height) continue;
                            int argb = img.getRGB(x, y);
                            if ((argb >>> 24) != 0) canvas[cy * width + cx] = argb;
                        }
                    }
                    if (i % step == 0) frames.add(canvas.clone());
                }

                int fps = frames.isEmpty() ? 8 : Math.round(100f * frames.size() / totalDelayCs);
                return new Result(frames, width, height, Math.max(1, Math.min(20, fps)));
            } finally {
                reader.dispose();
            }
        }
    }

    private static int intAttr(Node root, String nodeName, String attr) {
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeName().equals(nodeName)) {
                Node a = n.getAttributes().getNamedItem(attr);
                return a == null ? 0 : Integer.parseInt(a.getNodeValue());
            }
        }
        return 0;
    }
}
