package com.adhrit.capecreator.smoke;

import com.adhrit.capecreator.CapeManager;
import com.adhrit.capecreator.Compat;
import com.adhrit.capecreator.client.CapeEditorScreen;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game smoke test for Minecraft versions older than Fabric's client game test API (before
 * 1.21.4): creates a creative world, checks the custom cape is on the player and in its texture,
 * opens the editor, takes screenshots, then quits. Prints "CAPECREATOR SMOKE: PASS" or FAIL.
 */
public class SmokeTest implements ClientModInitializer {
    private int stage;
    private int ticks;
    private final List<String> problems = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        ticks++;
        switch (stage) {
            case 0 -> {
                // once loading is done (a fresh game shows the accessibility welcome screen first)
                if (mc.getOverlay() == null && mc.screen != null && ticks > 40) {
                    createWorld(mc);
                    next();
                } else if (ticks > 2400) {
                    fail(mc, "game never finished loading");
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    next();
                } else if (ticks > 2400) {
                    fail(mc, "world never loaded");
                }
            }
            case 2 -> {
                if (ticks == 60) {
                    checkCape(mc);
                    screenshot(mc, "smoke-in-world.png");
                    mc.setScreen(new CapeEditorScreen());
                    next();
                }
            }
            case 3 -> {
                if (ticks == 20) {
                    if (!(mc.screen instanceof CapeEditorScreen)) problems.add("editor screen did not open");
                    screenshot(mc, "smoke-editor.png");
                    mc.setScreen(null);
                    next();
                }
            }
            case 4 -> {
                if (ticks == 10) {
                    System.out.println("CAPECREATOR SMOKE: " + (problems.isEmpty() ? "PASS" : "FAIL " + problems));
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    private void next() {
        stage++;
        ticks = 0;
    }

    private void fail(Minecraft mc, String why) {
        problems.add(why);
        stage = 4;
        ticks = 0;
    }

    private void createWorld(Minecraft mc) {
        //#if MC >= 1.21.2
        GameRules rules = new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
        //#else
        GameRules rules = new GameRules();
        //#endif
        LevelSettings settings = new LevelSettings("Cape Smoke Test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("cape-smoke-" + System.currentTimeMillis(), settings,
                new WorldOptions(12345L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }

    /** The mixin must put our cape on the player, and the cape texture must hold the design. */
    private void checkCape(Minecraft mc) {
        if (!CapeManager.TEXTURE_ID.equals(mc.player.getSkin().capeTexture())) {
            problems.add("player's cape is " + mc.player.getSkin().capeTexture() + ", not ours");
        }
        AbstractTexture tex = mc.getTextureManager().getTexture(CapeManager.getTextureId());
        if (!(tex instanceof DynamicTexture dyn) || dyn.getPixels() == null) {
            problems.add("cape texture missing");
            return;
        }
        NativeImage img = dyn.getPixels();
        int s = CapeManager.scale();
        int got = Compat.getArgb(img, s, s), expected = CapeManager.get(0, 0, 0);
        if (got != expected) problems.add(String.format("cape texture pixel %08X != design %08X", got, expected));
    }

    private void screenshot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
    }
}
