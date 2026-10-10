package com.adhrit.capecreator.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * A 3D view of the player inside a box: drag to turn it (like grabbing it), drag up/down to look
 * from above or below, scroll to zoom, right-click to reset.
 */
public class PlayerPreview {
    private static final float PITCH_DRAG = -1;   // dragging down tips the top towards you

    private final float defaultYaw, defaultPitch;
    private float yaw, pitch, zoom = 1;
    private boolean dragging;
    private int x1, y1, x2, y2;

    /** Yaw 0 faces away from the camera (cape visible); negative pitch looks from above. */
    public PlayerPreview(float defaultYaw, float defaultPitch) {
        this.defaultYaw = defaultYaw;
        this.defaultPitch = defaultPitch;
        reset();
    }

    public void reset() {
        yaw = defaultYaw;
        pitch = defaultPitch;
        zoom = 1;
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void setAngles(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setBounds(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public boolean contains(double mx, double my) {
        return x2 - x1 > 40 && mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) reset();
        else dragging = true;
        return true;
    }

    public boolean mouseDragged(double dx, double dy) {
        if (!dragging) return false;
        yaw -= (float) dx * 2.5f;
        pitch = MathHelper.clamp(pitch + PITCH_DRAG * (float) dy * 2.5f, -85f, 85f);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my) || amount == 0) return false;
        zoom = MathHelper.clamp(zoom * (float) Math.pow(1.12, amount), 0.5f, 2.5f);
        return true;
    }

    public void render(DrawContext context, TextRenderer textRenderer) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        int w = x2 - x1;
        if (player == null || w < 40) return;

        context.fill(x1, y1, x2, y2, 0x40000000);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Drag to rotate, scroll to zoom"),
                (x1 + x2) / 2, y2 - 12, 0xFFA0A0A0);

        float size = Math.min(w * 0.55f, (y2 - y1) * 0.38f) * zoom;
        float bodyYaw = player.bodyYaw, entityYaw = player.getYaw(), entityPitch = player.getPitch();
        float headYaw = player.headYaw, lastHeadYaw = player.lastHeadYaw;

        player.bodyYaw = yaw;
        player.setYaw(yaw);
        player.setPitch(0);
        player.headYaw = yaw;
        player.lastHeadYaw = yaw;

        context.enableScissor(x1, y1, x2, y2);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(pitch));
        // drawEntity rotates around the feet; shift by the rotated centre so the model spins around its middle
        Vector3f offset = rotation.transform(new Vector3f(0, -(player.getHeight() / 2 + 0.0625f * player.getScale()), 0));
        InventoryScreen.drawEntity(context, (x1 + x2) / 2f, (y1 + y2 - 10) / 2f, size, offset, rotation, null, player);
        context.disableScissor();

        player.bodyYaw = bodyYaw;
        player.setYaw(entityYaw);
        player.setPitch(entityPitch);
        player.headYaw = headYaw;
        player.lastHeadYaw = lastHeadYaw;
    }
}
