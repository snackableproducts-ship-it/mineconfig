package dev.xmine.config.hud;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * CPS measurement by polling the mouse buttons every frame (rising-edge detection).
 * Only polled while a module that needs it (CPS / Keystrokes) is rendering, so it costs nothing otherwise.
 */
public final class ClickTracker {
    private static final int N = 64;
    private static final long[] LEFT = new long[N], RIGHT = new long[N];
    private static int li, ri;
    private static boolean ld, rd;
    private static long last;

    private ClickTracker() {}

    public static void poll(MinecraftClient mc) {
        long now = System.nanoTime();
        if (now - last < 500_000L) return; // already polled this frame
        last = now;
        long ms = now / 1_000_000L;
        long h = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;
        boolean l = free && GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = free && GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (l && !ld) { LEFT[li] = ms; li = (li + 1) % N; }
        if (r && !rd) { RIGHT[ri] = ms; ri = (ri + 1) % N; }
        ld = l; rd = r;
    }

    private static int count(long[] a) {
        long ms = System.nanoTime() / 1_000_000L;
        int c = 0;
        for (long t : a) if (t != 0 && ms - t <= 1000) c++;
        return c;
    }

    public static int leftCps() { return count(LEFT); }
    public static int rightCps() { return count(RIGHT); }
    public static boolean leftDown() { return ld; }
    public static boolean rightDown() { return rd; }
}
