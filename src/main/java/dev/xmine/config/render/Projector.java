package dev.xmine.config.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * World -> screen projection computed from the camera (position, yaw, pitch, FOV).
 * ESP/tracers/waypoints are drawn as a 2D overlay on the HUD layer using this, which avoids
 * touching the 3D render pipeline (fewer version-specific APIs, no mixins into WorldRenderer).
 * Updated once per frame by ModuleManager.renderHud().
 */
public final class Projector {
    /** Vertical FOV in degrees; written by GameRendererMixin each frame (includes zoom / sprint FOV). */
    public static double worldFov = 0;

    private static double cx, cy, cz, fx, fy, fz, rx, ry, rz, ux, uy, uz, tan, aspect;
    private static int sw, sh;
    private static final double[] TMP = new double[3];

    private Projector() {}

    public static void begin(MinecraftClient mc) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d p = cam.getPos();
        cx = p.x; cy = p.y; cz = p.z;
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double cp = Math.cos(pitch), sp = Math.sin(pitch), sy = Math.sin(yaw), cyw = Math.cos(yaw);
        // forward / right / up basis in Minecraft's yaw/pitch convention (yaw 0 = +Z)
        fx = -sy * cp; fy = -sp; fz = cyw * cp;
        rx = -cyw;     ry = 0;   rz = -sy;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        sw = mc.getWindow().getScaledWidth();
        sh = mc.getWindow().getScaledHeight();
        aspect = (double) sw / Math.max(1, sh);
        double fov = worldFov > 1 ? worldFov : mc.options.getFov().getValue();
        tan = Math.tan(Math.toRadians(fov) / 2.0);
    }

    public static int width() { return sw; }
    public static int height() { return sh; }

    /** Camera-relative coordinates: out = {right, up, depth}. No visibility test. */
    public static void toView(double x, double y, double z, double[] out) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        out[0] = dx * rx + dy * ry + dz * rz;
        out[1] = dx * ux + dy * uy + dz * uz;
        out[2] = dx * fx + dy * fy + dz * fz;
    }

    /** View-space -> scaled GUI pixels. Depth must be > 0. */
    public static void viewToScreen(double[] v, double[] out) {
        double nx = v[0] / (v[2] * tan * aspect);
        double ny = v[1] / (v[2] * tan);
        out[0] = (nx * 0.5 + 0.5) * sw;
        out[1] = (0.5 - ny * 0.5) * sh;
    }

    /** Returns false if the point is behind the camera. out = {screenX, screenY}. */
    public static boolean project(double x, double y, double z, double[] out) {
        toView(x, y, z, TMP);
        if (TMP[2] < 0.05) return false;
        viewToScreen(TMP, out);
        return true;
    }
}
