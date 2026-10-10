package com.adhrit.capecreator.test;

import com.adhrit.capecreator.CapeManager;
import com.adhrit.capecreator.client.CapeEditorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.platform.InputConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CapeCreatorClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            run(context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(ClientGameTestContext context) throws Exception {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            //#if MC >= 26.2
            world.getConnection().waitForChunksRender();
            //#elif MC >= 26.1
            world.getClientLevel().waitForChunksRender();
            //#else
            world.getClientWorld().waitForChunksRender();
            //#endif
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("gamerule fallDamage false");

            // The keybind should open the editor.
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitForScreen(CapeEditorScreen.class);

            // Paint a red cross on the default design.
            context.runOnClient(client -> {
                for (int y = 0; y < CapeManager.height(); y++) CapeManager.set(CapeManager.width() / 2, y, 0xFF0000);
                for (int x = 0; x < CapeManager.width(); x++) CapeManager.set(x, 5, 0xFF0000);
            });
            context.waitTicks(5);
            context.takeScreenshot("cape-editor");

            // Inside and edges of the cape use the main background colour (the blue), not the design.
            context.runOnClient(client -> {
                int bg = CapeManager.backgroundColor();
                check(bg == 0xFF2B4C9B, String.format("background should be the cape's blue, got %08X", bg));
                var tex = (net.minecraft.client.renderer.texture.DynamicTexture)
                        client.getTextureManager().getTexture(CapeManager.getTextureId());
                var img = tex.getPixels();
                int s = CapeManager.scale();
                int[][] spots = {{12 * s + 5 * s, 8 * s}, {0, 5 * s}, {11 * s, 9 * s}, {5 * s, 0}, {15 * s, 0}};
                for (int[] p : spots) {
                    int got = img.getPixel(p[0], p[1]);
                    check(got == bg, String.format("texture at %d,%d should be background %08X, got %08X", p[0], p[1], bg, got));
                }
                // the outside still shows the design (red cross)
                check(img.getPixel(s + CapeManager.width() / 2, s + 1) == 0xFFFF0000, "outside should still show the design");
            });

            // Import an animated GIF by "dropping" it on the screen, then check frames survive a save/load.
            Path gif;
            try (InputStream in = getClass().getResourceAsStream("/testdata/stripes.gif")) {
                gif = Files.createTempFile("stripes", ".gif");
                Files.copy(in, gif, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            dropAndCrop(context, gif);
            context.runOnClient(client -> {
                check(CapeManager.frameCount() == 4, "GIF should import 4 frames, got " + CapeManager.frameCount());
                check(CapeManager.fps() == 8, "GIF at 125ms/frame should be 8 fps, got " + CapeManager.fps());
                check(CapeManager.get(0, 0, 4) != CapeManager.get(1, 0, 4), "frames should differ");
                CapeManager.save();
                CapeManager.load();
                check(CapeManager.frameCount() == 4, "frames should survive save/load, got " + CapeManager.frameCount());
                CapeManager.selectFrame(2);
            });
            // A cape-site texture (64x32 layout, 3 frames stacked) should import just its back panels.
            Path strip;
            try (InputStream in = getClass().getResourceAsStream("/testdata/cape_strip.png")) {
                strip = Files.createTempFile("strip", ".png");
                Files.copy(in, strip, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.runOnClient(client -> {
                CapeManager.State before = CapeManager.snapshot();
                client.screen.onFilesDrop(List.of(strip));
                check(CapeManager.frameCount() == 3, "strip should import 3 frames, got " + CapeManager.frameCount());
                check(CapeManager.scale() == 1, "64-wide strip should be 1x, got " + CapeManager.scale());
                int[] expected = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
                for (int f = 0; f < 3; f++) {
                    for (int[] p : new int[][]{{0, 0}, {9, 15}, {4, 7}}) {
                        int got = CapeManager.get(f, p[0], p[1]);
                        check(got == expected[f], String.format("frame %d pixel %s: expected %08X got %08X", f, java.util.Arrays.toString(p), expected[f], got));
                    }
                }
                CapeManager.restore(before);
            });

            context.waitTicks(5);
            context.takeScreenshot("cape-editor-animated");

            // Click "Save & Close" with the real mouse so input routing is tested, not just the button action.
            double[] pos = context.computeOnClient(client -> {
                Button save = client.screen.children().stream()
                        .filter(e -> e instanceof Button b && b.getMessage().getString().equals("Save & Close"))
                        .map(e -> (Button) e).findFirst().orElseThrow();
                double scale = client.getWindow().getGuiScale();
                return new double[]{(save.getX() + save.getWidth() / 2.0) * scale, (save.getY() + save.getHeight() / 2.0) * scale};
            });
            context.getInput().setCursorPos(pos[0], pos[1]);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitForScreen(null);

            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.player.setXRot(20);
            });
            context.waitTicks(20);
            context.takeScreenshot("cape-in-world");
            context.waitTicks(3);
            context.takeScreenshot("cape-in-world-later-frame");

            // A 40x64 GIF should switch the cape to 4x resolution and render in world.
            Path hd;
            try (InputStream in = getClass().getResourceAsStream("/testdata/hd.gif")) {
                hd = Files.createTempFile("hd", ".gif");
                Files.copy(in, hd, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.setScreen(CapeEditorScreen::new);
            dropAndCrop(context, hd);
            context.runOnClient(client -> {
                check(CapeManager.scale() == 4, "40x64 GIF should import at 4x, got " + CapeManager.scale());
                client.setScreen(null);
            });
            context.waitTicks(10);
            context.takeScreenshot("cape-hd-in-world");

            // Side view of the cape in the editor preview.
            context.setScreen(CapeEditorScreen::new);
            context.<ReflectiveOperationException>runOnClient(client -> preview(client.screen).setAngles(90f, -10f));
            context.waitTicks(2);
            context.takeScreenshot("cape-side-a");
            context.waitTicks(7);
            context.takeScreenshot("cape-side-b");
            // Real mouse drags on the 3D preview: right turns it like grabbing it (yaw goes down),
            // down tips the top towards you (pitch goes down, i.e. viewed from above).
            context.<ReflectiveOperationException>runOnClient(client -> preview(client.screen).setAngles(0f, -10f));
            double[] centre = context.computeOnClient(client -> {
                double scale = client.getWindow().getGuiScale();
                return new double[]{(client.screen.width - 60) * scale, client.screen.height / 2.0 * scale};
            });
            context.getInput().setCursorPos(centre[0], centre[1]);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            for (int i = 0; i < 6; i++) {
                context.getInput().moveCursor(6 * 2, 4 * 2);
                context.waitTick();
            }
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.<ReflectiveOperationException>runOnClient(client -> {
                float y = preview(client.screen).yaw(), p = preview(client.screen).pitch();
                check(y < -20, "dragging right should turn the preview (yaw down), yaw=" + y);
                check(p < -20, "dragging down should tip the top towards you (pitch down), pitch=" + p);
            });
            context.takeScreenshot("preview-dragged");

            // ---- Sides: custom inside/edge colours ----
            context.runOnClient(client -> {
                check(CapeManager.insideColor() == null && CapeManager.edgeColor() == null, "sides should start on Auto");
            });
            clickButton(context, label -> label.equals("Sides"));
            context.waitForScreen(com.adhrit.capecreator.client.CapeSidesScreen.class);
            // click the red swatch (index 8: row 1, col 0) in the Inside palette with the real mouse
            double[] red = context.computeOnClient(client -> {
                double scale = client.getWindow().getGuiScale();
                int x = 16 + 6, y = 30 + 38 + 12 + 6;
                return new double[]{x * scale, y * scale};
            });
            context.getInput().setCursorPos(red[0], red[1]);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTick();
            context.runOnClient(client -> check(Integer.valueOf(0xFFFF0000).equals(CapeManager.insideColor()),
                    "clicking red in Inside should set it, got " + CapeManager.formatSideColor(CapeManager.insideColor())));
            // type a hex colour into the Edges box
            context.<ReflectiveOperationException>runOnClient(client -> {
                var edgesField = com.adhrit.capecreator.client.CapeSidesScreen.class.getDeclaredField("edges");
                edgesField.setAccessible(true);
                Object edges = edgesField.get(client.screen);
                var hexField = edges.getClass().getDeclaredField("hex");
                hexField.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox) hexField.get(edges)).setValue("#00FF00");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(Integer.valueOf(0xFF00FF00).equals(CapeManager.edgeColor()), "typing #00FF00 should set edges");
                var img = ((net.minecraft.client.renderer.texture.DynamicTexture)
                        client.getTextureManager().getTexture(CapeManager.getTextureId())).getPixels();
                int s = CapeManager.scale();
                check(img.getPixel(12 * s + 5 * s, 8 * s) == 0xFFFF0000, "inside should be red in the texture");
                check(img.getPixel(0, 5 * s) == 0xFF00FF00, "left edge should be green in the texture");
                check(img.getPixel(11 * s, 5 * s) == 0xFF00FF00, "right edge should be green in the texture");
            });
            context.takeScreenshot("sides-screen");
            clickButton(context, label -> label.equals("Done"));
            context.waitForScreen(CapeEditorScreen.class);
            // undo twice -> back to Auto
            context.runOnClient(client -> {
                //#if MC >= 1.21.9
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL));
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL));
                //#else
                client.screen.keyPressed(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL);
                client.screen.keyPressed(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL);
                //#endif
                check(CapeManager.insideColor() == null && CapeManager.edgeColor() == null,
                        "undo should put sides back to Auto, got " + CapeManager.formatSideColor(CapeManager.insideColor())
                                + " / " + CapeManager.formatSideColor(CapeManager.edgeColor()));
            });
            context.runOnClient(client -> client.setScreen(null));
            context.waitTicks(30);
            context.takeScreenshot("cape-standing");
            context.getInput().holdKey(options -> options.keySprint);
            context.getInput().holdKeyFor(options -> options.keyUp, 25);
            context.takeScreenshot("cape-sprinting");
            context.getInput().releaseKey(options -> options.keySprint);
            context.waitTicks(4);
            context.takeScreenshot("cape-just-stopped");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~25 ~");
            context.waitTicks(14);
            context.takeScreenshot("cape-falling");
            context.waitTicks(40);

            // A 160x256 GIF should import at 16x, show on the (scaled-down) editor canvas and in world.
            Path hq;
            try (InputStream in = getClass().getResourceAsStream("/testdata/hq.gif")) {
                hq = Files.createTempFile("hq", ".gif");
                Files.copy(in, hq, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.setScreen(CapeEditorScreen::new);
            dropAndCrop(context, hq);
            context.runOnClient(client -> {
                check(CapeManager.scale() == 16, "160x256 GIF should import at 16x, got " + CapeManager.scale());
                check(CapeManager.frameCount() == 8, "expected 8 frames, got " + CapeManager.frameCount());
            });
            // Cycling the Size button all the way round (16x -> 1x -> ... -> 16x) must keep full detail.
            CapeManager.State beforeCycle = context.computeOnClient(client -> CapeManager.snapshot());
            int[] expectedScales = {1, 2, 4, 8, 16};
            for (int expected : expectedScales) {
                clickButton(context, label -> label.startsWith("Size:"));
                context.waitTick();
                int got = context.computeOnClient(client -> CapeManager.scale());
                check(got == expected, "after clicking Size expected " + expected + "x, got " + got + "x");
            }
            context.runOnClient(client -> {
                for (int f = 0; f < beforeCycle.frames().size(); f++) {
                    check(java.util.Arrays.equals(beforeCycle.frames().get(f), CapeManager.snapshot().frames().get(f)),
                            "frame " + f + " lost detail after cycling sizes");
                }
            });

            context.waitTicks(3);
            context.takeScreenshot("cape-16x-editor");
            context.runOnClient(client -> client.setScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("cape-16x-in-world");

            // ---- import from a link, through the crop screen ----
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            byte[] gifBytes;
            try (InputStream in = getClass().getResourceAsStream("/testdata/hq.gif")) {
                gifBytes = in.readAllBytes();
            }
            server.createContext("/cape.gif", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "image/gif");
                exchange.sendResponseHeaders(200, gifBytes.length);
                exchange.getResponseBody().write(gifBytes);
                exchange.close();
            });
            server.createContext("/page", exchange -> {
                byte[] html = "<html>not an image</html>".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
                exchange.close();
            });
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            try {
                // a web page link should give a helpful error, not crash
                context.setScreen(CapeEditorScreen::new);
                context.runOnClient(client -> client.keyboardHandler.setClipboard(base + "/page"));
                context.getInput().holdControl();
                context.getInput().pressKey(InputConstants.KEY_V);
                context.getInput().releaseControl();
                context.waitForScreen(com.adhrit.capecreator.client.UrlImportScreen.class);
                context.waitFor(client -> client.screen instanceof com.adhrit.capecreator.client.UrlImportScreen
                        && screenText(client.screen).contains("web page"), 200);
                context.takeScreenshot("link-error");

                // a real GIF link opens the crop screen
                context.runOnClient(client -> client.keyboardHandler.setClipboard(base + "/cape.gif"));
                context.setScreen(CapeEditorScreen::new);
                context.getInput().holdControl();
                context.getInput().pressKey(InputConstants.KEY_V);
                context.getInput().releaseControl();
                context.waitForScreen(com.adhrit.capecreator.client.CropScreen.class);
                context.waitTicks(3);
                context.takeScreenshot("link-crop-screen");
                clickButton(context, label -> label.equals("Use this crop"));
                context.waitForScreen(CapeEditorScreen.class);
                context.runOnClient(client -> {
                    check(CapeManager.scale() == 16, "cropped 160x256 link should import at 16x, got " + CapeManager.scale());
                    check(CapeManager.frameCount() == 8, "link import should keep 8 frames, got " + CapeManager.frameCount());
                });
            } finally {
                server.stop(0);
            }

            // Link imports are auto-saved, named from the link
            check(com.adhrit.capecreator.CapeLibrary.list().stream().anyMatch(e -> e.name().equals("cape")),
                    "link import should be auto-saved as \"cape\", saved: " + com.adhrit.capecreator.CapeLibrary.list());
            String[][] names = {
                    {"https://storage.ko-fi.com/cdn/useruploads/display/8d97102e-8810-466a-9384-c043d1492529_pixel-fire-preview.gif", "pixel fire preview"},
                    {"https://i.pinimg.com/originals/10/d5/1a/10d51aa37731b05a5d085e15186e211c.gif", "link cape"},
                    {"https://example.com/gifs/Cool_Galaxy-Loop.gif?size=large", "Cool Galaxy Loop"},
                    {"https://example.com/", "link cape"},
                    {"https://64.media.tumblr.com/abc/tumblr_ord7ll2peg1sx56xso1_500.gif", "tumblr"},
                    {"https://animesher.com/orig/2/203/2030/20302/animesher.com_dark-chill-pleasing-2030251.gif", "animesher dark chill pleasing"},
            };
            for (String[] n : names) {
                String got = com.adhrit.capecreator.CapeLibrary.nameFromUrl(n[0]);
                check(got.equals(n[1]), "name for " + n[0] + ": expected \"" + n[1] + "\" got \"" + got + "\"");
            }
            context.runOnClient(client -> {
                for (var e : com.adhrit.capecreator.CapeLibrary.list()) com.adhrit.capecreator.CapeLibrary.delete(e);
            });

            // ---- saved capes ----
            context.runOnClient(client -> {
                CapeManager.setEdgeColor(0xFFFFFF00);
                com.adhrit.capecreator.CapeLibrary.saveCurrent("night city");
                CapeManager.importFrames(List.of(new int[]{0xFFFF0000}), 1, 1, 8);  // plain red
                check(CapeManager.edgeColor() == null, "a new image should reset sides to Auto");
                com.adhrit.capecreator.CapeLibrary.saveCurrent("red");
                check(com.adhrit.capecreator.CapeLibrary.list().size() == 2, "expected 2 saved capes");
                client.setScreen(null);
            });
            context.getInput().pressKey(InputConstants.KEY_RBRACKET);
            context.waitTick();
            context.runOnClient(client -> {
                check("night city".equals(CapeManager.currentName()),
                        "] after red should wrap to night city, got " + CapeManager.currentName());
                check(Integer.valueOf(0xFFFFFF00).equals(CapeManager.edgeColor()),
                        "night city should bring back its yellow edges, got " + CapeManager.formatSideColor(CapeManager.edgeColor()));
            });
            context.getInput().pressKey(InputConstants.KEY_RBRACKET);
            context.waitTick();
            context.runOnClient(client -> {
                check("red".equals(CapeManager.currentName()), "] should go to red, got " + CapeManager.currentName());
                check(CapeManager.get(0, 0, 0) == 0xFFFF0000, "red cape should be red");
            });
            context.getInput().pressKey(InputConstants.KEY_LBRACKET);
            context.waitTicks(10);
            context.runOnClient(client -> check("night city".equals(CapeManager.currentName()),
                    "[ should go back to night city, got " + CapeManager.currentName()));
            context.takeScreenshot("saved-cape-cycled-in-world");
            context.setScreen(com.adhrit.capecreator.client.SavedCapesScreen::new);
            context.waitTicks(3);
            context.takeScreenshot("saved-capes-screen");
            context.setScreen(() -> null);

            world.getServer().runCommand("item replace entity @a armor.chest with elytra");
            context.waitTicks(10);
            context.takeScreenshot("cape-elytra");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Clicks the first button whose label matches, using the real mouse. */
    private static void clickButton(ClientGameTestContext context, java.util.function.Predicate<String> label) {
        double[] pos = context.computeOnClient(client -> {
            Button b = client.screen.children().stream()
                    .filter(e -> e instanceof Button bw && label.test(bw.getMessage().getString()))
                    .map(e -> (Button) e).findFirst().orElseThrow();
            double scale = client.getWindow().getGuiScale();
            return new double[]{(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    /** All text drawn by a screen's widgets plus its fields, for simple "did this message appear" checks. */
    private static String screenText(net.minecraft.client.gui.screens.Screen screen) {
        try {
            var f = screen.getClass().getDeclaredField("status");
            f.setAccessible(true);
            return ((net.minecraft.network.chat.Component) f.get(screen)).getString();
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    /** Drops a file on the editor, then accepts the default crop in the crop screen. */
    private static void dropAndCrop(ClientGameTestContext context, Path file) {
        context.runOnClient(client -> client.screen.onFilesDrop(List.of(file)));
        context.waitForScreen(com.adhrit.capecreator.client.CropScreen.class);
        clickButton(context, label -> label.equals("Use this crop"));
        context.waitForScreen(CapeEditorScreen.class);
    }

    private static com.adhrit.capecreator.client.PlayerPreview preview(net.minecraft.client.gui.screens.Screen screen)
            throws ReflectiveOperationException {
        var f = screen.getClass().getDeclaredField("preview");
        f.setAccessible(true);
        return (com.adhrit.capecreator.client.PlayerPreview) f.get(screen);
    }

}
