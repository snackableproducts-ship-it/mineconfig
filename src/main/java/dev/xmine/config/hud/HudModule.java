package dev.xmine.config.hud;

import dev.xmine.config.core.Category;
import dev.xmine.config.core.XModule;
import dev.xmine.config.gui.UiUtil;
import dev.xmine.config.setting.BoolSetting;
import dev.xmine.config.setting.ColorSetting;
import dev.xmine.config.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A module with an on-screen element. Provides the shared position / scale / opacity / visibility
 * settings so every HUD element is customisable and draggable in the HUD editor.
 * Positions are stored as percentages of the screen so they survive resolution / GUI-scale changes.
 */
public abstract class HudModule extends XModule {
    public static final int PAD = 3;

    public final NumberSetting posX, posY, scale, opacity;
    public final BoolSetting visible, background, shadow;
    public final ColorSetting color;

    protected HudModule(String name, String desc, Category cat, double defX, double defY) {
        super(name, desc, cat);
        posX = addCommon(new NumberSetting("Position X", defX, 0, 100, 0.05).suffix("%"));
        posY = addCommon(new NumberSetting("Position Y", defY, 0, 100, 0.05).suffix("%"));
        scale = addCommon(new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.05));
        opacity = addCommon(new NumberSetting("Opacity", 100, 10, 100, 1).suffix("%"));
        visible = addCommon(new BoolSetting("Visible", true));
        background = addCommon(new BoolSetting("Background", true));
        shadow = addCommon(new BoolSetting("Text shadow", true));
        color = addCommon(new ColorSetting("Text color", 0xFFFFFF));
    }

    // ---- content contract -----------------------------------------------------
    public abstract int contentWidth();
    public abstract int contentHeight();
    protected abstract void renderContent(DrawContext ctx, float tickDelta);
    /** Called right before rendering each frame (poll input, refresh cached strings...). */
    protected void beforeRender() {}

    // ---- geometry -------------------------------------------------------------
    public int totalW() { return Math.max(12, contentWidth()) + PAD * 2; }
    public int totalH() { return Math.max(8, contentHeight()) + PAD * 2; }
    public int boundsW() { return Math.round(totalW() * scale.getFloat()); }
    public int boundsH() { return Math.round(totalH() * scale.getFloat()); }

    /** Pixel position, clamped so the element can never leave the screen. */
    public int pixelX(int sw) {
        double raw = posX.get() / 100.0 * sw;
        return (int) Math.round(Math.max(0, Math.min(Math.max(0, sw - boundsW()), raw)));
    }
    public int pixelY(int sh) {
        double raw = posY.get() / 100.0 * sh;
        return (int) Math.round(Math.max(0, Math.min(Math.max(0, sh - boundsH()), raw)));
    }

    public void resetPosition() { posX.reset(); posY.reset(); }

    // ---- rendering ------------------------------------------------------------
    @Override
    public final void onHud(DrawContext ctx, float td) {
        if (!visible.get() || HudManager.isEditing()) return;
        render(ctx, td);
    }

    /** Draws the element (also used by the HUD editor, regardless of the visible flag). */
    public void render(DrawContext ctx, float td) {
        beforeRender();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float s = scale.getFloat();
        int w = totalW(), h = totalH();
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) pixelX(sw), (float) pixelY(sh), 0f);
        m.scale(s, s, 1f);
        if (background.get()) UiUtil.roundRect(ctx, 0, 0, w, h, 3, ((int) (alphaF() * 0.55f * 255) << 24) | 0x0E1016);
        m.translate((float) PAD, (float) PAD, 0f);
        renderContent(ctx, td);
        m.pop();
    }

    // ---- helpers for subclasses ----------------------------------------------
    protected float alphaF() { return opacity.getFloat() / 100f; }
    protected int argb(int rgb) { return (Math.max(8, Math.round(alphaF() * 255)) << 24) | (rgb & 0xFFFFFF); }
    protected int tw(String s) { return mc.textRenderer.getWidth(s); }
    protected void text(DrawContext ctx, String s, int x, int y, int rgb) {
        ctx.drawText(mc.textRenderer, s, x, y, argb(rgb), shadow.get());
    }
    protected static String fmt(double v, int decimals) { return String.format(java.util.Locale.ROOT, "%." + decimals + "f", v); }
}
