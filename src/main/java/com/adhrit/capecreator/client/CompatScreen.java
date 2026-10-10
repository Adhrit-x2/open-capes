package com.adhrit.capecreator.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 1.21.9
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//#endif

/**
 * Base for this mod's screens. Minecraft 1.21.9 changed mouse/keyboard handlers to take event
 * objects; this class turns them back into plain numbers so the screens don't need version checks.
 * Screens override the on* methods and call the super* methods for vanilla behaviour.
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return superMouseClicked(mouseX, mouseY, button);
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return superMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return superMouseReleased(mouseX, mouseY, button);
    }

    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return superKeyPressed(keyCode, scanCode, modifiers);
    }

    //#if MC >= 1.21.9
    private int clickModifiers;
    private boolean doubleClick;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        clickModifiers = event.modifiers();
        doubleClick = doubled;
        return onMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        clickModifiers = event.modifiers();
        return onMouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        clickModifiers = event.modifiers();
        return onMouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key(), event.scancode(), event.modifiers());
    }

    protected boolean superMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, clickModifiers)), doubleClick);
    }

    protected boolean superMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, clickModifiers)), deltaX, deltaY);
    }

    protected boolean superMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, clickModifiers)));
    }

    protected boolean superKeyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    protected static boolean isPasteKey(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers).isPaste();
    }
    //#else
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return onMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean superMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean superMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    protected boolean superMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean superKeyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected static boolean isPasteKey(int keyCode, int scanCode, int modifiers) {
        return Screen.isPaste(keyCode);
    }
    //#endif
}
