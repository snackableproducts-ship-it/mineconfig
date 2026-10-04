package dev.xmine.config.render;

import dev.xmine.config.gui.UiUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** 2D drawing helpers that work on projected world coordinates. No per-call allocations. */
public final class Overlay {
    private static final double[] A = new double[3], B = new double[3], S1 = new double[2], S2 = new double[2];
    private static final double[] P = new double[2];
    private static final double NEAR = 0.05;

    private Overlay() {}

    /** Thick line using a rotated 1px-high fill (DrawContext has no line primitive). */
    public static void line2d(DrawContext ctx, double x1, double y1, double x2, double y2, int color, float thickness) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5 || len > 6000) return;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.peek().getPositionMatrix().translate((float) x1, (float) y1, 0f).rotateZ((float) Math.atan2(dy, dx));
        ctx.fill(0, 0, (int) Math.ceil(len), Math.max(1, Math.round(thickness)), color);
        m.pop();
    }

    /** 3D line with near-plane clipping so boxes partly behind the camera still draw correctly. */
    public static void line3d(DrawContext ctx, double ax, double ay, double az, double bx, double by, double bz,
                              int color, float th) {
        Projector.toView(ax, ay, az, A);
        Projector.toView(bx, by, bz, B);
        if (A[2] < NEAR && B[2] < NEAR) return;
        if (A[2] < NEAR) clip(A, B); else if (B[2] < NEAR) clip(B, A);
        Projector.viewToScreen(A, S1);
        Projector.viewToScreen(B, S2);
        line2d(ctx, S1[0], S1[1], S2[0], S2[1], color, th);
    }

    private static void clip(double[] bad, double[] good) {
        double t = (NEAR - bad[2]) / (good[2] - bad[2]);
        bad[0] += (good[0] - bad[0]) * t;
        bad[1] += (good[1] - bad[1]) * t;
        bad[2] = NEAR;
    }

    public static void box3d(DrawContext c, double x0, double y0, double z0, double x1, double y1, double z1,
                             int col, float th) {
        // bottom ring, top ring, verticals
        line3d(c, x0, y0, z0, x1, y0, z0, col, th); line3d(c, x1, y0, z0, x1, y0, z1, col, th);
        line3d(c, x1, y0, z1, x0, y0, z1, col, th); line3d(c, x0, y0, z1, x0, y0, z0, col, th);
        line3d(c, x0, y1, z0, x1, y1, z0, col, th); line3d(c, x1, y1, z0, x1, y1, z1, col, th);
        line3d(c, x1, y1, z1, x0, y1, z1, col, th); line3d(c, x0, y1, z1, x0, y1, z0, col, th);
        line3d(c, x0, y0, z0, x0, y1, z0, col, th); line3d(c, x1, y0, z0, x1, y1, z0, col, th);
        line3d(c, x1, y0, z1, x1, y1, z1, col, th); line3d(c, x0, y0, z1, x0, y1, z1, col, th);
    }

    /** Screen-aligned bounding rectangle of a 3D box. Skipped if any corner is behind the camera. */
    public static void box2d(DrawContext c, double x0, double y0, double z0, double x1, double y1, double z1, int col) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            if (!Projector.project(x, y, z, P)) return;
            minX = Math.min(minX, P[0]); maxX = Math.max(maxX, P[0]);
            minY = Math.min(minY, P[1]); maxY = Math.max(maxY, P[1]);
        }
        UiUtil.outline(c, (int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY), col);
    }

    /** Entity bounding box at its interpolated position. */
    public static void entityBox(DrawContext c, Entity e, float td, int col, boolean threeD, float th) {
        double x = MathHelper.lerp(td, e.prevX, e.getX());
        double y = MathHelper.lerp(td, e.prevY, e.getY());
        double z = MathHelper.lerp(td, e.prevZ, e.getZ());
        double hw = e.getWidth() / 2.0, h = e.getHeight();
        if (threeD) box3d(c, x - hw, y, z - hw, x + hw, y + h, z + hw, col, th);
        else box2d(c, x - hw, y, z - hw, x + hw, y + h, z + hw, col);
    }

    /** Text with a small dark plate, centred on (sx, sy). */
    public static void label(DrawContext c, String text, double sx, double sy, int color, float scale, boolean plate) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = tr.getWidth(text);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate((float) sx, (float) sy, 0f);
        m.scale(scale, scale, 1f);
        if (plate) c.fill(-w / 2 - 2, -1, w / 2 + 3, 9, 0x90000000);
        c.drawText(tr, text, -w / 2, 0, color, false);
        m.pop();
    }
}
