package com.adhrit.capecreator;

import com.adhrit.capecreator.client.SourceImage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Saved capes, kept in config/capecreator/saved. Each cape is either a design PNG (frames stacked
 * vertically, written by "Save current") with an optional .properties file for its speed, or any
 * GIF dropped into the folder.
 */
public final class CapeLibrary {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(CapeCreatorClient.MOD_ID).resolve("saved");

    public record Entry(String name, Path file) {
        public boolean isGif() {
            return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif");
        }
    }

    private CapeLibrary() {}

    public static Path directory() { return DIR; }

    public static List<Entry> list() {
        if (!Files.isDirectory(DIR)) return List.of();
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".png") || n.endsWith(".gif");
                    })
                    .map(p -> new Entry(stripExtension(p.getFileName().toString()), p))
                    .sorted(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            CapeCreatorClient.LOGGER.warn("Could not list saved capes", e);
            return List.of();
        }
    }

    /** Saves the current design under the given name (replacing one with the same name) and commits it as worn. */
    public static Entry saveCurrent(String rawName) throws IOException {
        Entry entry = addCurrent(rawName);
        CapeManager.save();
        return entry;
    }

    /**
     * Adds the current design to the saved capes without committing the editor's design file, so an
     * editor "Cancel" afterwards still works as expected.
     */
    public static Entry addCurrent(String rawName) throws IOException {
        String name = sanitize(rawName);
        Files.createDirectories(DIR);
        Path png = DIR.resolve(name + ".png");
        Files.deleteIfExists(DIR.resolve(name + ".gif"));
        CapeManager.writeStrip(png);
        Properties props = new Properties();
        props.setProperty("fps", Integer.toString(CapeManager.fps()));
        props.setProperty("inside", CapeManager.formatSideColor(CapeManager.insideColor()));
        props.setProperty("edges", CapeManager.formatSideColor(CapeManager.edgeColor()));
        try (Writer w = Files.newBufferedWriter(DIR.resolve(name + ".properties"))) {
            props.store(w, "Cape Creator saved cape");
        }
        CapeManager.setCurrentName(name);
        return new Entry(name, png);
    }

    /** Puts a saved cape on and remembers it as the current design. */
    public static void wear(Entry entry) throws IOException {
        if (entry.isGif()) {
            SourceImage gif = SourceImage.decode(Files.readAllBytes(entry.file()));
            CapeManager.importFrames(gif.frames(), gif.width(), gif.height(), gif.fps());
        } else {
            CapeManager.readStrip(entry.file());
        }
        // speed and side colours saved alongside (also works for GIFs once they have a .properties)
        Path props = DIR.resolve(entry.name() + ".properties");
        Properties p = new Properties();
        if (Files.exists(props)) {
            try (Reader r = Files.newBufferedReader(props)) {
                p.load(r);
            }
            if (!entry.isGif()) CapeManager.setFps(Integer.parseInt(p.getProperty("fps", Integer.toString(CapeManager.fps()))));
        }
        CapeManager.setInsideColor(CapeManager.parseSideColor(p.getProperty("inside")));
        CapeManager.setEdgeColor(CapeManager.parseSideColor(p.getProperty("edges")));
        CapeManager.setEnabled(true);
        CapeManager.setCurrentName(entry.name());
        CapeManager.save();
    }

    /** Remembers the side colours for the saved cape being worn, so they come back next time. */
    public static void updateSideColors(String name) throws IOException {
        Path file = DIR.resolve(name + ".properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            }
        }
        p.setProperty("inside", CapeManager.formatSideColor(CapeManager.insideColor()));
        p.setProperty("edges", CapeManager.formatSideColor(CapeManager.edgeColor()));
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Cape Creator saved cape");
        }
    }

    public static void delete(Entry entry) throws IOException {
        Files.deleteIfExists(entry.file());
        Files.deleteIfExists(DIR.resolve(entry.name() + ".properties"));
        if (entry.name().equals(CapeManager.currentName())) CapeManager.setCurrentName(null);
    }

    /** Wears the next (or previous) saved cape after the current one. Returns it, or null if there are none. */
    public static Entry cycle(int direction) throws IOException {
        List<Entry> entries = list();
        if (entries.isEmpty()) return null;
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(CapeManager.currentName())) index = i;
        }
        int next = index < 0 ? (direction > 0 ? 0 : entries.size() - 1) : Math.floorMod(index + direction, entries.size());
        Entry entry = entries.get(next);
        wear(entry);
        return entry;
    }

    public static String uniqueName(String base) {
        String name = sanitize(base);
        List<String> taken = new ArrayList<>(list().stream().map(Entry::name).toList());
        if (!taken.contains(name)) return name;
        for (int i = 2; ; i++) {
            if (!taken.contains(name + " " + i)) return name + " " + i;
        }
    }

    /** A readable cape name from a link's file name; random-looking names become "link cape". */
    public static String nameFromUrl(String url) {
        String path = url;
        try {
            path = java.net.URI.create(url.trim()).getPath();
        } catch (IllegalArgumentException ignored) {
        }
        String file = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        String base = stripExtension(file).replaceAll("[_\\-.+]+", " ").trim();
        // drop hash-like chunks, e.g. "8d97102e 8810 ... pixel fire preview" -> "pixel fire preview"
        String words = String.join(" ", java.util.Arrays.stream(base.split(" "))
                .filter(w -> !w.isEmpty() && !isJunkWord(w))
                .toList()).trim();
        String compact = words.replace(" ", "");
        boolean random = compact.chars().filter(Character::isDigit).count() * 3 >= Math.max(1, compact.length());
        return words.isEmpty() || random ? "link cape" : sanitize(words);
    }

    /** Hash/ID-like chunks of file names: "8d97102e", "2030251", "ord7ll2peg1sx56xso1", "500", plus "com"/"www". */
    private static boolean isJunkWord(String w) {
        String lower = w.toLowerCase(Locale.ROOT);
        if (lower.equals("com") || lower.equals("www") || lower.equals("orig")) return true;
        long digits = w.chars().filter(Character::isDigit).count();
        if (digits == w.length()) return true;                                   // pure numbers
        if (w.length() >= 4 && w.matches("[0-9a-fA-F]+") && digits > 0) return true;  // hex hashes
        return w.length() >= 8 && digits >= 2;                                   // random IDs like ord7ll2peg1sx56xso1
    }

    private static String sanitize(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9 _\\-]", "").replaceAll("\\s+", " ");
        if (name.length() > 40) name = name.substring(0, 40).trim();
        return name.isEmpty() ? "cape" : name;
    }

    private static String stripExtension(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
}
