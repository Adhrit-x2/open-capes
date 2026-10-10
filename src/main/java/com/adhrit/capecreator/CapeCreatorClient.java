package com.adhrit.capecreator;

import com.adhrit.capecreator.client.CapeEditorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapeCreatorClient implements ClientModInitializer {
    public static final String MOD_ID = "capecreator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openEditorKey;
    private static KeyMapping nextCapeKey;
    private static KeyMapping previousCapeKey;

    @Override
    public void onInitializeClient() {
        CapeManager.load();

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.capecreator.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.capecreator"));

        nextCapeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.capecreator.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "category.capecreator"));
        previousCapeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.capecreator.previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "category.capecreator"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new CapeEditorScreen());
                }
            }
            while (nextCapeKey.consumeClick()) cycleSavedCape(client, 1);
            while (previousCapeKey.consumeClick()) cycleSavedCape(client, -1);
        });
    }

    private static void cycleSavedCape(Minecraft client, int direction) {
        if (client.player == null) return;
        try {
            CapeLibrary.Entry entry = CapeLibrary.cycle(direction);
            client.player.displayClientMessage(Component.literal(entry == null
                    ? "No saved capes yet - press J, then Saved, to save one"
                    : "Cape: " + entry.name()), true);
        } catch (Exception e) {
            LOGGER.warn("Could not switch cape", e);
            client.player.displayClientMessage(Component.literal("Couldn't load that cape"), true);
        }
    }
}
