package com.adhrit.capecreator;

import com.adhrit.capecreator.client.CapeEditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapeCreatorClient implements ClientModInitializer {
    public static final String MOD_ID = "capecreator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openEditorKey;
    private static KeyBinding nextCapeKey;
    private static KeyBinding previousCapeKey;

    @Override
    public void onInitializeClient() {
        CapeManager.load();

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.capecreator.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.capecreator"));

        nextCapeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.capecreator.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "category.capecreator"));
        previousCapeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.capecreator.previous", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "category.capecreator"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CapeEditorScreen());
                }
            }
            while (nextCapeKey.wasPressed()) cycleSavedCape(client, 1);
            while (previousCapeKey.wasPressed()) cycleSavedCape(client, -1);
        });
    }

    private static void cycleSavedCape(MinecraftClient client, int direction) {
        if (client.player == null) return;
        try {
            CapeLibrary.Entry entry = CapeLibrary.cycle(direction);
            client.player.sendMessage(Text.literal(entry == null
                    ? "No saved capes yet - press J, then Saved, to save one"
                    : "Cape: " + entry.name()), true);
        } catch (Exception e) {
            LOGGER.warn("Could not switch cape", e);
            client.player.sendMessage(Text.literal("Couldn't load that cape"), true);
        }
    }
}
