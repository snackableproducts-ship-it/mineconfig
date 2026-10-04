package dev.xmine.config.input;

import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;
import dev.xmine.config.hud.NotificationManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;

/**
 * Polls module keybinds once per tick (only for modules that actually have a key) and
 * enforces conflict rules when assigning keys.
 */
public final class KeybindManager {
    private static XModule[] bound = new XModule[0];
    private static boolean stale = true;

    private KeybindManager() {}

    public static void invalidate() { stale = true; }

    private static void rebuild() {
        ArrayList<XModule> l = new ArrayList<>();
        for (XModule m : ModuleManager.INSTANCE.all()) if (m.getKeybind().isBound()) l.add(m);
        bound = l.toArray(new XModule[0]);
        stale = false;
    }

    public static void poll(MinecraftClient mc) {
        if (stale) rebuild();
        if (bound.length == 0 || mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null; // never fire while typing in chat / GUIs
        for (XModule m : bound) {
            boolean down = free && InputUtil.isKeyPressed(handle, m.getKeybind().get());
            if (m.isHoldMode()) {
                if (down != m.keyHeld) { m.keyHeld = down; m.setEnabled(down); }
            } else {
                if (down && !m.keyHeld) m.onKeyPress();
                m.keyHeld = down;
            }
        }
    }

    /**
     * Try to bind a key. Rules:
     *  1. Keys used by any vanilla control (including the XMINE open key) are refused.
     *  2. If another module already uses the key, it is unbound (one key = one module).
     * @return a short message for the user
     */
    public static String assign(XModule m, int key) {
        for (KeyBinding kb : MinecraftClient.getInstance().options.allKeys) {
            InputUtil.Key bk = KeyBindingHelper.getBoundKeyOf(kb);
            if (bk.getCategory() == InputUtil.Type.KEYSYM && bk.getCode() == key) {
                return "Key already used by: " + I18n.translate(kb.getTranslationKey());
            }
        }
        String note = "";
        for (XModule other : ModuleManager.INSTANCE.all()) {
            if (other != m && other.getKeybind().get() == key) {
                other.getKeybind().clear();
                note = " (unbound from " + other.getName() + ")";
            }
        }
        m.getKeybind().set(key);
        return m.getName() + " bound to " + m.getKeybind().display() + note;
    }

    public static void clear(XModule m) {
        m.getKeybind().clear();
        NotificationManager.push("Cleared keybind: " + m.getName());
    }

    public static void clearAll() {
        for (XModule m : ModuleManager.INSTANCE.all()) m.getKeybind().clear();
    }
}
