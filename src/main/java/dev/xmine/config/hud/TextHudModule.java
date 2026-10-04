package dev.xmine.config.hud;

import dev.xmine.config.core.Category;
import net.minecraft.client.gui.DrawContext;

/** HUD element made of text lines. Subclasses fill {@link #lines} in updateLines() (runs once per tick). */
public abstract class TextHudModule extends HudModule {
    protected String[] lines = {""};

    protected TextHudModule(String name, String desc, Category cat, double x, double y) {
        super(name, desc, cat, x, y);
    }

    protected abstract void updateLines();

    @Override public void onTick() { updateLines(); }

    @Override public int contentWidth() {
        int w = 0;
        for (String l : lines) w = Math.max(w, tw(l));
        return w;
    }

    @Override public int contentHeight() { return lines.length * 10 - 1; }

    @Override protected void renderContent(DrawContext ctx, float td) {
        for (int i = 0; i < lines.length; i++) text(ctx, lines[i], 0, i * 10, lineColor(i));
    }

    /** Override to colour individual lines (e.g. FPS green/yellow/red). */
    protected int lineColor(int i) { return color.get(); }
}
