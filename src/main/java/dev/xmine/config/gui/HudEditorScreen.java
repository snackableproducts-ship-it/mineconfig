package dev.xmine.config.gui;

import dev.xmine.config.XmineClient;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.hud.HudManager;
import dev.xmine.config.hud.HudModule;
import dev.xmine.config.module.MiscModules.ClickGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Edit HUD mode: shows every enabled HUD element with an outline; drag to move,
 * drag the corner handle or scroll to scale, right-click to show/hide, arrows to nudge.
 * Positions are written straight into the module's settings (and therefore auto-saved).
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule selected;
    private boolean dragging, resizing, confirmReset;
    private double grabX, grabY;
    private float startScale;
    private int startW;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override protected void init() { HudManager.setEditing(true); }
    @Override public void removed() { HudManager.setEditing(false); dev.xmine.config.configuration.ConfigManager.saveNow(); }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private static final int HANDLE = 6;

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        int acc = ClickGui.accent();
        c.fill(0, 0, width, height, 0x50000000);

        List<HudModule> els = HudManager.enabledElements();
        for (HudModule h : els) {
            h.render(c, delta);
            int x = h.pixelX(width), y = h.pixelY(height), w = h.boundsW(), hh = h.boundsH();
            boolean sel = h == selected;
            int col = !h.visible.get() ? 0xFFFF5555 : sel ? acc : 0x99FFFFFF;
            UiUtil.outline(c, x - 1, y - 1, w + 2, hh + 2, col);
            if (sel) UiUtil.outline(c, x - 2, y - 2, w + 4, hh + 4, UiUtil.alpha(acc, 0.4f));
            // resize handle (bottom-right)
            c.fill(x + w - HANDLE + 1, y + hh - HANDLE + 1, x + w + 1, y + hh + 1, sel ? acc : 0xAAFFFFFF);
            String label = h.getName() + (h.visible.get() ? "" : " (hidden)");
            UiUtil.scaledText(c, label, x, y > 10 ? y - 10 : y + hh + 3, 0.75f, sel ? acc : 0xFFC8CCD8);
        }

        UiUtil.centerText(c, "EDIT HUD   |   drag: move   corner/scroll: scale   right-click: show/hide   arrows: nudge   Esc: back",
                width / 2, height - 14, 0xFFC8CCD8);
        if (selected != null) {
            UiUtil.centerText(c, selected.getName() + "   scale " + selected.scale.format() + "   pos "
                    + selected.posX.format() + " / " + selected.posY.format(), width / 2, height - 26, acc);
        }
        int bw = 130, bx = width / 2 - bw - 4;
        button(c, mx, my, bx, 8, bw, 18, "Reset HUD Positions", 0xFF5A2A2F);
        button(c, mx, my, width / 2 + 4, 8, 70, 18, "Done", UiUtil.alpha(acc, 0.8f));

        if (confirmReset) {
            c.fill(0, 0, width, height, 0xB0000000);
            int dw = 250, dh = 70, dx = (width - dw) / 2, dy = (height - dh) / 2;
            UiUtil.roundRect(c, dx, dy, dw, dh, 8, 0xFF161922);
            UiUtil.centerText(c, "Reset all HUD positions to default?", dx + dw / 2, dy + 14, 0xFFE8EBF2);
            button(c, mx, my, dx + 30, dy + dh - 26, 80, 18, "Yes", 0xFF9B3A42);
            button(c, mx, my, dx + dw - 110, dy + dh - 26, 80, 18, "Cancel", 0xFF3A3F4F);
        }
    }

    private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, int color) {
        boolean hov = UiUtil.inside(mx, my, x, y, w, h);
        UiUtil.roundRect(c, x, y, w, h, 4, hov ? UiUtil.lerp(color, 0xFFFFFFFF, 0.12f) : color);
        UiUtil.centerText(c, label, x + w / 2, y + (h - 9) / 2 + 1, 0xFFE8EBF2);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (confirmReset) {
            int dw = 250, dh = 70, dx = (width - dw) / 2, dy = (height - dh) / 2;
            if (UiUtil.inside(mx, my, dx + 30, dy + dh - 26, 80, 18)) ModuleManager.INSTANCE.resetHudPositions();
            if (UiUtil.inside(mx, my, dx + 30, dy + dh - 26, 80, 18) || UiUtil.inside(mx, my, dx + dw - 110, dy + dh - 26, 80, 18)) confirmReset = false;
            return true;
        }
        int bw = 130, bx = width / 2 - bw - 4;
        if (UiUtil.inside(mx, my, bx, 8, bw, 18)) { confirmReset = true; return true; }
        if (UiUtil.inside(mx, my, width / 2 + 4, 8, 70, 18)) { close(); return true; }

        List<HudModule> els = HudManager.enabledElements();
        for (int i = els.size() - 1; i >= 0; i--) { // topmost first
            HudModule h = els.get(i);
            int x = h.pixelX(width), y = h.pixelY(height), w = h.boundsW(), hh = h.boundsH();
            if (!UiUtil.inside(mx, my, x - 2, y - 2, w + 4, hh + 4)) continue;
            selected = h;
            if (button == 1) { h.visible.toggle(); return true; }
            if (UiUtil.inside(mx, my, x + w - HANDLE - 2, y + hh - HANDLE - 2, HANDLE + 5, HANDLE + 5)) {
                resizing = true; startScale = h.scale.getFloat(); startW = Math.max(1, w);
            } else {
                dragging = true; grabX = mx - x; grabY = my - y;
            }
            return true;
        }
        selected = null;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (selected == null) return false;
        if (dragging) {
            // Clamp so the element can never be dragged off-screen
            double nx = Math.max(0, Math.min(width - selected.boundsW(), mx - grabX));
            double ny = Math.max(0, Math.min(height - selected.boundsH(), my - grabY));
            selected.posX.setValue(nx * 100.0 / width);
            selected.posY.setValue(ny * 100.0 / height);
            return true;
        }
        if (resizing) {
            double newW = mx - selected.pixelX(width);
            selected.scale.setValue(startScale * newW / startW);
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int b) { dragging = resizing = false; return true; }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudModule target = selected;
        if (target == null) {
            for (HudModule e : HudManager.enabledElements())
                if (UiUtil.inside(mx, my, e.pixelX(width), e.pixelY(height), e.boundsW(), e.boundsH())) target = e;
        }
        if (target != null) target.scale.setValue(target.scale.get() + Math.signum(v) * 0.05);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (confirmReset) { if (key == GLFW.GLFW_KEY_ESCAPE) confirmReset = false; return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE || XmineClient.openKey().matchesKey(key, scan)) { close(); return true; }
        if (selected != null) {
            double step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> selected.posX.setValue(selected.posX.get() - step * 100.0 / width);
                case GLFW.GLFW_KEY_RIGHT -> selected.posX.setValue(selected.posX.get() + step * 100.0 / width);
                case GLFW.GLFW_KEY_UP -> selected.posY.setValue(selected.posY.get() - step * 100.0 / height);
                case GLFW.GLFW_KEY_DOWN -> selected.posY.setValue(selected.posY.get() + step * 100.0 / height);
                default -> { return super.keyPressed(key, scan, mods); }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
