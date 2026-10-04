package dev.xmine.config.hud;

import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;

import java.util.ArrayList;
import java.util.List;

/** Shared HUD state: edit-mode flag, FPS measurement, helpers for the HUD editor. */
public final class HudManager {
    private static boolean editing;
    private static int frames, fps;
    private static long windowStart = System.nanoTime();

    private HudManager() {}

    public static boolean isEditing() { return editing; }
    public static void setEditing(boolean e) { editing = e; }
    public static int fps() { return fps; }

    /** Called once per rendered frame; measures FPS over 0.5s windows (no allocation). */
    public static void frame() {
        frames++;
        long now = System.nanoTime();
        if (now - windowStart >= 500_000_000L) {
            fps = (int) Math.round(frames * 1e9 / (now - windowStart));
            frames = 0;
            windowStart = now;
        }
    }

    /** All enabled HUD elements (used by the editor). */
    public static List<HudModule> enabledElements() {
        ArrayList<HudModule> l = new ArrayList<>();
        for (XModule m : ModuleManager.INSTANCE.all()) if (m instanceof HudModule h && h.isEnabled()) l.add(h);
        return l;
    }
}
