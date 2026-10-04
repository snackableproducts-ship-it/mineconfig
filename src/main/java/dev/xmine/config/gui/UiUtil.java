package dev.xmine.config.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Small drawing toolkit: rounded rects (scanline based, cheap), colour maths, scaled text. */
public final class UiUtil {
    private UiUtil() {}

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Multiply the alpha channel of an ARGB colour. */
    public static int alpha(int argb, float a) {
        int al = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Rounded rectangle: one centre fill plus 2 thin fills per corner row. Radius is clamped. */
    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, color); return; }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    public static void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(tr(), s, x, y, color, false);
    }

    public static void centerText(DrawContext c, String s, int cx, int y, int color) {
        c.drawText(tr(), s, cx - tr().getWidth(s) / 2, y, color, false);
    }

    public static void scaledText(DrawContext c, String s, int x, int y, float scale, int color) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate((float) x, (float) y, 0f);
        m.scale(scale, scale, 1f);
        c.drawText(tr(), s, 0, 0, color, false);
        m.pop();
    }

    public static String trim(String s, int maxWidth) {
        if (tr().getWidth(s) <= maxWidth) return s;
        return tr().trimToWidth(s, Math.max(0, maxWidth - tr().getWidth("..."))) + "...";
    }
}
