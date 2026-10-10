package com.adhrit.capecreator;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
//#if MC >= 1.21.11
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//#endif
//#if MC >= 1.21.6
import net.minecraft.client.renderer.RenderPipelines;
//#elif MC >= 1.21.2
import net.minecraft.client.renderer.RenderType;
//#endif

/**
 * Every Minecraft API that differs between the versions this mod supports, in one place, so the
 * rest of the code can stay version-independent. See the preprocessor notes in build.gradle.
 */
public final class Compat {
    private Compat() {}

    /** The Cmd (Mac) / Windows-key modifier bit; InputConstants only has it from 1.21.9. */
    //#if MC >= 1.21.9
    public static final int MOD_SUPER = InputConstants.MOD_SUPER;
    //#else
    public static final int MOD_SUPER = 8;
    //#endif

    // ---- textures and pixels (pixels are always ARGB here) ----

    //#if MC >= 1.21.11
    /** 1.21.11 moved texture filtering into a sampler that only subclasses can change. */
    public static final class FilterableTexture extends DynamicTexture {
        FilterableTexture(String name, int width, int height) {
            super(() -> name, width, height, true);
        }

        FilterableTexture(String name, NativeImage image) {
            super(() -> name, image);
        }

        void setLinear(boolean linear) {
            this.sampler = RenderSystem.getSamplerCache().getRepeat(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
        }
    }
    //#endif

    public static DynamicTexture newTexture(String name, int width, int height) {
        //#if MC >= 1.21.11
        return new FilterableTexture(name, width, height);
        //#elif MC >= 1.21.5
        return new DynamicTexture(() -> name, width, height, true);
        //#else
        return new DynamicTexture(width, height, true);
        //#endif
    }

    public static DynamicTexture newTexture(String name, NativeImage image) {
        //#if MC >= 1.21.11
        return new FilterableTexture(name, image);
        //#elif MC >= 1.21.5
        return new DynamicTexture(() -> name, image);
        //#else
        return new DynamicTexture(image);
        //#endif
    }

    public static void setLinearFilter(DynamicTexture texture, boolean linear) {
        //#if MC >= 1.21.11
        if (texture instanceof FilterableTexture f) f.setLinear(linear);
        //#else
        texture.setFilter(linear, false);
        //#endif
    }

    public static int getArgb(NativeImage image, int x, int y) {
        //#if MC >= 1.21.2
        return image.getPixel(x, y);
        //#else
        return swapRedBlue(image.getPixelRGBA(x, y));
        //#endif
    }

    public static void setArgb(NativeImage image, int x, int y, int argb) {
        //#if MC >= 1.21.2
        image.setPixel(x, y, argb);
        //#else
        image.setPixelRGBA(x, y, swapRedBlue(argb));
        //#endif
    }

    public static void fill(NativeImage image, int x, int y, int width, int height, int argb) {
        for (int j = y; j < y + height; j++) {
            for (int i = x; i < x + width; i++) setArgb(image, i, j, argb);
        }
    }

    /** ARGB <-> ABGR (older NativeImage pixel order). */
    static int swapRedBlue(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }

    // ---- GUI drawing ----

    /** Draws a (regionWidth x regionHeight) region at (u, v) of a texture, stretched to (width x height). */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                            int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        //#if MC >= 1.21.6
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        //#elif MC >= 1.21.2
        g.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        //#else
        g.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#endif
    }

    public static void tooltip(GuiGraphics g, Font font, Component text, int x, int y) {
        //#if MC >= 1.21.6
        g.setTooltipForNextFrame(font, text, x, y);
        //#else
        g.renderTooltip(font, text, x, y);
        //#endif
    }

    // ---- key bindings ----

    //#if MC >= 1.21.9
    private static KeyMapping.Category category;
    //#endif

    /** A key binding in this mod's "Cape Creator" controls category. */
    public static KeyMapping keyMapping(String translationKey, int defaultKey) {
        //#if MC >= 1.21.9
        if (category == null) category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(CapeCreatorClient.MOD_ID, "main"));
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, defaultKey, category);
        //#else
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, defaultKey, "category.capecreator");
        //#endif
    }

    // ---- misc ----

    public static void toast(Component title, Component description) {
        Minecraft mc = Minecraft.getInstance();
        //#if MC >= 26.2
        SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        //#elif MC >= 1.21.2
        SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        //#else
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        //#endif
    }

    /** Message just above the hotbar. */
    public static void overlayMessage(Player player, Component message) {
        //#if MC >= 26.1
        player.sendOverlayMessage(message);
        //#else
        player.displayClientMessage(message, true);
        //#endif
    }

    /** Opens a folder in Finder / Explorer. */
    public static void openFolder(Path path) {
        //#if MC >= 26.3
        com.mojang.blaze3d.Blaze3D.openPath(path);
        //#else
        net.minecraft.Util.getPlatform().openPath(path);
        //#endif
    }
}
