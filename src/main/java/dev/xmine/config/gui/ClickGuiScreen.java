package dev.xmine.config.gui;

import dev.xmine.config.XmineClient;
import dev.xmine.config.configuration.ConfigManager;
import dev.xmine.config.core.Category;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;
import dev.xmine.config.input.KeybindManager;
import dev.xmine.config.module.MiscModules.ClickGui;
import dev.xmine.config.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The XMINE CONFIG main menu: category sidebar, search, scrollable module cards and a reusable
 * settings panel that renders ANY Setting generically.
 *
 * Input uses an immediate-mode "region" list: during render() every clickable thing registers a
 * rectangle; mouse handlers hit-test that list. This keeps drawing and hit-testing in one place.
 */
public class ClickGuiScreen extends Screen {
    private static final int SIDE_W = 118, PAD = 10, TOP_H = 40, CARD_H = 30, GAP = 4;
    private static final int BG = 0xF2101218, SIDE_BG = 0xFF0A0B0F, CARD = 0xFF1A1D26, CARD_HOVER = 0xFF232733,
            FIELD = 0xFF12141B, OFF = 0xFF3A3F4F, TEXT = 0xFFE8EBF2, DIM = 0xFF8D93A5;

    private static final int R_CAT = 1, R_SEARCH = 2, R_TOGGLE = 3, R_CUSTOM = 4, R_KEY = 5, R_BACK = 6, R_BOOL = 7,
            R_SLIDER = 8, R_MODE = 9, R_MODE_OPT = 10, R_COLOR = 11, R_SWATCH = 12, R_TEXT = 13, R_ACTION = 14,
            R_EDIT_HUD = 15, R_RESET_HUD = 16, R_RESET_ALL = 17, R_YES = 18, R_NO = 19, R_PKEY = 20;

    private static final class Region {
        int x, y, w, h, type, val;
        Object target;
        boolean scrolled;
    }

    private final List<Region> regions = new ArrayList<>();
    private int regionCount;

    // layout
    private int px, py, pw, ph, vx, vy, vw, vh;
    // state
    private Category cat = null;                 // null = "All"
    private String query = "";
    private boolean searchFocus, listDirty = true;
    private final List<XModule> shown = new ArrayList<>();
    private float scroll, scrollT, pScroll, pScrollT;
    private int contentH, pContentH;
    private XModule panel;                       // module whose settings panel is open
    private XModule binding;                     // module waiting for a key press
    private TextSetting editing;
    private Setting<?> open;                     // open dropdown / colour picker
    private NumberSetting drag;
    private int dragX, dragW;
    private String confirmText, toast;
    private Runnable confirmAct;
    private long toastUntil, lastNs = System.nanoTime();
    private final float[] catHover = new float[8];

    public ClickGuiScreen() { super(Text.literal(XmineClient.NAME)); }

    @Override public boolean shouldPause() { return false; }

    private int accent() { return ClickGui.accent(); }

    // =====================================================================================
    // Rendering
    // =====================================================================================
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float k = ClickGui.animations() ? 1f - (float) Math.exp(-dt * 16f) : 1f;

        pw = Math.min(560, width - 16);
        ph = Math.min(344, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        vx = px + SIDE_W + PAD; vw = pw - SIDE_W - 2 * PAD;
        vy = py + TOP_H;        vh = ph - TOP_H - PAD;
        regionCount = 0;

        if (ClickGui.dim()) c.fill(0, 0, width, height, 0x80000000);
        UiUtil.roundRect(c, px, py, pw, ph, 8, BG);
        drawSidebar(c, mx, my, k);
        if (panel == null) { drawSearch(c, mx, my); drawList(c, mx, my, k); }
        else drawPanel(c, mx, my, k);
        if (confirmAct != null) drawConfirm(c, mx, my);
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int tw = textRenderer.getWidth(toast) + 12;
            UiUtil.roundRect(c, (width - tw) / 2, py + ph + 4, tw, 14, 4, 0xE0000000);
            UiUtil.text(c, toast, (width - tw) / 2 + 6, py + ph + 8, TEXT);
        }
    }

    private Region reg(int type, int x, int y, int w, int h, Object target, int val, boolean scrolled) {
        Region r;
        if (regionCount < regions.size()) r = regions.get(regionCount);
        else { r = new Region(); regions.add(r); }
        regionCount++;
        r.type = type; r.x = x; r.y = y; r.w = w; r.h = h; r.target = target; r.val = val; r.scrolled = scrolled;
        return r;
    }

    private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, int color) {
        boolean hov = UiUtil.inside(mx, my, x, y, w, h);
        UiUtil.roundRect(c, x, y, w, h, 4, hov ? UiUtil.lerp(color, 0xFFFFFFFF, 0.12f) : color);
        UiUtil.centerText(c, label, x + w / 2, y + (h - 9) / 2 + 1, TEXT);
    }

    private void drawSidebar(DrawContext c, int mx, int my, float k) {
        UiUtil.roundRect(c, px, py, SIDE_W, ph, 8, SIDE_BG);
        c.fill(px + SIDE_W - 8, py, px + SIDE_W, py + ph, SIDE_BG);
        c.drawText(textRenderer, Text.literal(XmineClient.NAME).formatted(Formatting.BOLD), px + 12, py + 13, accent(), false);

        int rowH = Math.max(14, Math.min(22, (ph - 36 - 74) / 8));
        int y = py + 34;
        for (int i = 0; i < 8; i++) {
            Category cc = i == 0 ? null : Category.values()[i - 1];
            boolean sel = cat == cc, hov = UiUtil.inside(mx, my, px + 6, y, SIDE_W - 12, rowH - 2);
            catHover[i] += (((sel ? 1f : hov ? 0.5f : 0f)) - catHover[i]) * k;
            UiUtil.roundRect(c, px + 6, y, SIDE_W - 12, rowH - 2, 4, UiUtil.alpha(accent() | 0xFF000000, 0.25f * catHover[i]));
            if (sel) c.fill(px + 6, y + 4, px + 8, y + rowH - 6, accent());
            String label = cc == null ? "All" : cc.label();
            UiUtil.text(c, label, px + 15, y + (rowH - 9) / 2, UiUtil.lerp(DIM, TEXT, Math.min(1f, catHover[i] * 1.4f)));
            String n = String.valueOf(ModuleManager.INSTANCE.countIn(cc));
            UiUtil.text(c, n, px + SIDE_W - 14 - textRenderer.getWidth(n), y + (rowH - 9) / 2, 0xFF565B6B);
            reg(R_CAT, px + 6, y, SIDE_W - 12, rowH - 2, cc, 0, false);
            y += rowH;
        }

        int by = py + ph - 66, bw = SIDE_W - 20;
        button(c, mx, my, px + 10, by, bw, 16, "Edit HUD", accent() & 0xCCFFFFFF | 0xCC000000);
        reg(R_EDIT_HUD, px + 10, by, bw, 16, null, 0, false);
        button(c, mx, my, px + 10, by + 20, bw, 16, "Reset HUD Positions", OFF);
        reg(R_RESET_HUD, px + 10, by + 20, bw, 16, null, 0, false);
        button(c, mx, my, px + 10, by + 40, bw, 16, "Reset All Settings", 0xFF5A2A2F);
        reg(R_RESET_ALL, px + 10, by + 40, bw, 16, null, 0, false);
    }

    private void drawSearch(DrawContext c, int mx, int my) {
        int y = py + 10;
        UiUtil.roundRect(c, vx, y, vw, 20, 5, FIELD);
        if (searchFocus) UiUtil.outline(c, vx, y, vw, 20, UiUtil.alpha(accent(), 0.8f));
        boolean caret = searchFocus && (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty() && !searchFocus) UiUtil.text(c, "Search modules...  (try 'fps' or 'hud')", vx + 8, y + 6, 0xFF565B6B);
        else UiUtil.text(c, UiUtil.trim(query, vw - 70) + (caret ? "_" : ""), vx + 8, y + 6, TEXT);
        String cnt = shown.size() + " modules";
        UiUtil.text(c, cnt, vx + vw - 8 - textRenderer.getWidth(cnt), y + 6, 0xFF565B6B);
        reg(R_SEARCH, vx, y, vw, 20, null, 0, false);
    }

    private void drawList(DrawContext c, int mx, int my, float k) {
        if (listDirty) {
            ModuleManager.INSTANCE.query(cat, query, shown);
            listDirty = false;
        }
        contentH = shown.size() * (CARD_H + GAP);
        scrollT = Math.max(0, Math.min(Math.max(0, contentH - vh), scrollT));
        scroll += (scrollT - scroll) * k;

        c.enableScissor(vx, vy, vx + vw, vy + vh);
        if (shown.isEmpty()) UiUtil.centerText(c, "No modules match your search", vx + vw / 2, vy + 30, DIM);
        for (int i = 0; i < shown.size(); i++) {
            int y = vy + i * (CARD_H + GAP) - Math.round(scroll);
            if (y + CARD_H < vy || y > vy + vh) continue;
            drawCard(c, mx, my, k, shown.get(i), y);
        }
        c.disableScissor();
        scrollbar(c, scroll, contentH);
    }

    private void scrollbar(DrawContext c, float sc, int total) {
        if (total <= vh) return;
        int th = Math.max(20, vh * vh / total);
        int ty = vy + (int) ((vh - th) * (sc / (total - vh)));
        UiUtil.roundRect(c, vx + vw - 3, ty, 3, th, 1, 0x66FFFFFF);
    }

    private void drawCard(DrawContext c, int mx, int my, float k, XModule m, int y) {
        int cw = vw - 8;
        boolean inView = my >= vy && my < vy + vh;
        boolean hov = inView && UiUtil.inside(mx, my, vx, y, cw, CARD_H);
        m.uiHover += ((hov ? 1f : 0f) - m.uiHover) * k;
        m.uiAnim += ((m.isEnabled() ? 1f : 0f) - m.uiAnim) * k;
        int acc = accent();

        UiUtil.roundRect(c, vx, y, cw, CARD_H, 5, UiUtil.lerp(CARD, CARD_HOVER, m.uiHover));
        if (m.uiAnim > 0.02f) c.fill(vx, y + 7, vx + 2, y + CARD_H - 7, UiUtil.alpha(acc, m.uiAnim));

        // --- toggle pill ---
        int tw = 38, th = 14, tx = vx + cw - 8 - tw, ty = y + (CARD_H - th) / 2;
        int pill = UiUtil.lerp(OFF, acc, m.uiAnim);
        UiUtil.roundRect(c, tx, ty, tw, th, 7, m.isAlwaysOn() ? UiUtil.alpha(pill, 0.6f) : pill);
        UiUtil.roundRect(c, tx + 2 + Math.round(m.uiAnim * (tw - th)), ty + 2, th - 4, th - 4, 5, 0xFFFFFFFF);
        if (m.uiAnim > 0.5f) UiUtil.text(c, "ON", tx + 6, ty + 3, 0xFFFFFFFF);
        else UiUtil.text(c, "OFF", tx + tw - 6 - textRenderer.getWidth("OFF"), ty + 3, 0xFFB8BDCC);
        if (!m.isAlwaysOn()) reg(R_TOGGLE, tx, ty, tw, th, m, 0, true);
        int rx = tx - 4;

        // --- customize ---
        if (m.hasSettings()) {
            int bw = 56, bx = rx - bw;
            boolean bh = inView && UiUtil.inside(mx, my, bx, ty, bw, th);
            UiUtil.roundRect(c, bx, ty, bw, th, 4, bh ? 0xFF303545 : 0xFF262A37);
            UiUtil.centerText(c, "Customize", bx + bw / 2, ty + 3, bh ? TEXT : 0xFFB8BDCC);
            reg(R_CUSTOM, bx, ty, bw, th, m, 0, true);
            rx = bx - 4;
        }

        // --- keybind chip ---
        int kw = 46, kx = rx - kw;
        boolean waiting = binding == m;
        UiUtil.roundRect(c, kx, ty, kw, th, 4, waiting ? UiUtil.alpha(acc, 0.5f) : 0xFF1E212C);
        String key = waiting ? "..." : UiUtil.trim(m.getKeybind().display(), kw - 6);
        UiUtil.centerText(c, key, kx + kw / 2, ty + 3, m.getKeybind().isBound() || waiting ? TEXT : 0xFF565B6B);
        reg(R_KEY, kx, ty, kw, th, m, 0, true);

        // --- text ---
        int maxW = kx - 6 - (vx + 10);
        c.drawText(textRenderer, UiUtil.trim(m.getName(), maxW), vx + 10, y + 6, TEXT, false);
        UiUtil.scaledText(c, UiUtil.trim(m.getDescription(), (int) (maxW / 0.75f)), vx + 10, y + 18, 0.75f, DIM);
    }

    // =====================================================================================
    // Settings panel
    // =====================================================================================
    private void drawPanel(DrawContext c, int mx, int my, float k) {
        button(c, mx, my, vx, py + 10, 52, 20, "< Back", FIELD);
        reg(R_BACK, vx, py + 10, 52, 20, null, 0, false);
        c.drawText(textRenderer, Text.literal(panel.getName()).formatted(Formatting.BOLD), vx + 62, py + 11, TEXT, false);
        UiUtil.scaledText(c, UiUtil.trim(panel.getDescription(), (int) ((vw - 70) / 0.75f)), vx + 62, py + 23, 0.75f, DIM);

        pScrollT = Math.max(0, Math.min(Math.max(0, pContentH - vh), pScrollT));
        pScroll += (pScrollT - pScroll) * k;
        int rw = vw - 8;
        int y = vy - Math.round(pScroll);

        c.enableScissor(vx, vy, vx + vw, vy + vh);
        y = rowKeybind(c, mx, my, y, rw);
        for (Setting<?> s : panel.getSettings()) {
            if (!s.isVisible()) continue;
            y = drawSetting(c, mx, my, s, y, rw);
        }
        pContentH = y + Math.round(pScroll) - vy;
        c.disableScissor();
        scrollbar(c, pScroll, pContentH);
    }

    private void rowBg(DrawContext c, int mx, int my, int y, int h, int rw) {
        boolean hov = my >= vy && my < vy + vh && UiUtil.inside(mx, my, vx, y, rw, h);
        UiUtil.roundRect(c, vx, y, rw, h, 5, hov ? CARD_HOVER : CARD);
    }

    private int rowKeybind(DrawContext c, int mx, int my, int y, int rw) {
        int h = 24;
        rowBg(c, mx, my, y, h, rw);
        UiUtil.text(c, "Keybind", vx + 10, y + 8, TEXT);
        boolean waiting = binding == panel;
        int bw = 110, bx = vx + rw - 8 - bw;
        UiUtil.roundRect(c, bx, y + 4, bw, 16, 4, waiting ? UiUtil.alpha(accent(), 0.5f) : FIELD);
        UiUtil.centerText(c, waiting ? "Press a key..." : panel.getKeybind().display(), bx + bw / 2, y + 8, TEXT);
        UiUtil.scaledText(c, "Click to set - right-click or Backspace clears", bx - 150, y + 9, 0.75f, 0xFF565B6B);
        reg(R_PKEY, bx, y + 4, bw, 16, panel, 0, true);
        return y + h + 3;
    }

    private int drawSetting(DrawContext c, int mx, int my, Setting<?> s, int y, int rw) {
        int acc = accent();
        boolean inView = my >= vy && my < vy + vh;
        if (s instanceof BoolSetting b) {
            rowBg(c, mx, my, y, 24, rw);
            UiUtil.text(c, b.getName(), vx + 10, y + 8, TEXT);
            int bx = vx + rw - 8 - 14;
            UiUtil.roundRect(c, bx, y + 5, 14, 14, 3, b.get() ? acc : OFF);
            if (b.get()) UiUtil.roundRect(c, bx + 4, y + 9, 6, 6, 2, 0xFFFFFFFF);
            reg(R_BOOL, vx, y, rw, 24, b, 0, true);
            return y + 27;
        }
        if (s instanceof NumberSetting n) {
            rowBg(c, mx, my, y, 32, rw);
            UiUtil.text(c, n.getName(), vx + 10, y + 6, TEXT);
            String v = n.format();
            UiUtil.text(c, v, vx + rw - 10 - textRenderer.getWidth(v), y + 6, DIM);
            int tx = vx + 10, tw = rw - 20, ty = y + 21;
            UiUtil.roundRect(c, tx, ty, tw, 4, 2, FIELD);
            int fw = Math.max(2, (int) (tw * n.fraction()));
            UiUtil.roundRect(c, tx, ty, fw, 4, 2, acc);
            UiUtil.roundRect(c, tx + fw - 4, ty - 2, 8, 8, 4, 0xFFFFFFFF);
            reg(R_SLIDER, tx, y + 14, tw, 16, n, 0, true);
            return y + 35;
        }
        if (s instanceof ModeSetting m) {
            boolean isOpen = open == m;
            int h = 24 + (isOpen ? m.options().length * 16 + 4 : 0);
            rowBg(c, mx, my, y, h, rw);
            UiUtil.text(c, m.getName(), vx + 10, y + 8, TEXT);
            int bw = 120, bx = vx + rw - 8 - bw;
            UiUtil.roundRect(c, bx, y + 4, bw, 16, 4, FIELD);
            UiUtil.text(c, m.get(), bx + 6, y + 8, TEXT);
            UiUtil.text(c, isOpen ? "^" : "v", bx + bw - 12, y + 8, DIM);
            reg(R_MODE, bx, y + 4, bw, 16, m, 0, true);
            if (isOpen) {
                String[] opts = m.options();
                for (int i = 0; i < opts.length; i++) {
                    int oy = y + 24 + i * 16;
                    boolean sel = i == m.index();
                    boolean oh = inView && UiUtil.inside(mx, my, bx, oy, bw, 16);
                    UiUtil.roundRect(c, bx, oy, bw, 15, 3, oh ? 0xFF2C3142 : (sel ? UiUtil.alpha(acc, 0.35f) : 0xFF171A22));
                    UiUtil.text(c, opts[i], bx + 6, oy + 4, sel ? TEXT : 0xFFB8BDCC);
                    reg(R_MODE_OPT, bx, oy, bw, 16, m, i, true);
                }
            }
            return y + h + 3;
        }
        if (s instanceof ColorSetting col) {
            boolean isOpen = open == col;
            int h = 24 + (isOpen ? 46 : 0);
            rowBg(c, mx, my, y, h, rw);
            UiUtil.text(c, col.getName(), vx + 10, y + 8, TEXT);
            int sx = vx + rw - 8 - 34;
            UiUtil.roundRect(c, sx - 1, y + 5, 36, 14, 4, 0xFFFFFFFF);
            UiUtil.roundRect(c, sx, y + 6, 34, 12, 3, 0xFF000000 | col.get());
            reg(R_COLOR, sx, y + 5, 34, 14, col, 0, true);
            if (isOpen) {
                for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
                    int cx = vx + 10 + (i % 6) * 22, cy = y + 26 + (i / 6) * 20;
                    UiUtil.roundRect(c, cx - 1, cy - 1, 20, 18, 3, col.get() == ColorSetting.PALETTE[i] ? 0xFFFFFFFF : 0xFF2A2E3B);
                    UiUtil.roundRect(c, cx, cy, 18, 16, 3, 0xFF000000 | ColorSetting.PALETTE[i]);
                    reg(R_SWATCH, cx, cy, 18, 16, col, ColorSetting.PALETTE[i], true);
                }
            }
            return y + h + 3;
        }
        if (s instanceof TextSetting t) {
            rowBg(c, mx, my, y, 24, rw);
            UiUtil.text(c, t.getName(), vx + 10, y + 8, TEXT);
            int bw = Math.min(190, rw - 130), bx = vx + rw - 8 - bw;
            boolean ed = editing == t;
            UiUtil.roundRect(c, bx, y + 4, bw, 16, 4, FIELD);
            if (ed) UiUtil.outline(c, bx, y + 4, bw, 16, UiUtil.alpha(acc, 0.8f));
            String shownTxt = t.get() + (ed && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            int w = textRenderer.getWidth(shownTxt);
            // keep the caret visible by showing the tail of long strings
            String vis = w > bw - 10 ? textRenderer.trimToWidth(shownTxt, bw - 10, true) : shownTxt;
            UiUtil.text(c, vis, bx + 5, y + 8, TEXT);
            reg(R_TEXT, bx, y + 4, bw, 16, t, 0, true);
            return y + 27;
        }
        if (s instanceof ActionSetting a) {
            rowBg(c, mx, my, y, 24, rw);
            UiUtil.text(c, a.getName(), vx + 10, y + 8, TEXT);
            int bw = 120, bx = vx + rw - 8 - bw;
            button(c, mx, my, bx, y + 4, bw, 16, a.label(), a.confirmText() != null ? 0xFF5A2A2F : 0xFF2E3446);
            reg(R_ACTION, bx, y + 4, bw, 16, a, 0, true);
            return y + 27;
        }
        return y;
    }

    private void drawConfirm(DrawContext c, int mx, int my) {
        regionCount = 0; // modal: only the dialog is clickable
        c.fill(0, 0, width, height, 0xB0000000);
        int bw = 250, bh = 78, bx = (width - bw) / 2, by = (height - bh) / 2;
        UiUtil.roundRect(c, bx, by, bw, bh, 8, 0xFF161922);
        UiUtil.outline(c, bx, by, bw, bh, 0xFF2E3446);
        UiUtil.centerText(c, "Are you sure?", bx + bw / 2, by + 10, TEXT);
        UiUtil.centerText(c, UiUtil.trim(confirmText, bw - 20), bx + bw / 2, by + 26, DIM);
        button(c, mx, my, bx + 30, by + bh - 28, 80, 18, "Yes", 0xFF9B3A42);
        reg(R_YES, bx + 30, by + bh - 28, 80, 18, null, 0, false);
        button(c, mx, my, bx + bw - 110, by + bh - 28, 80, 18, "Cancel", OFF);
        reg(R_NO, bx + bw - 110, by + bh - 28, 80, 18, null, 0, false);
    }

    // =====================================================================================
    // Input
    // =====================================================================================
    private void say(String s) { toast = s; toastUntil = System.currentTimeMillis() + 2500; }

    private void ask(String text, Runnable act) { confirmText = text; confirmAct = act; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Region hit = null;
        for (int i = regionCount - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (!UiUtil.inside(mx, my, r.x, r.y, r.w, r.h)) continue;
            if (r.scrolled && !UiUtil.inside(mx, my, vx, vy, vw, vh)) continue;
            hit = r;
            break;
        }
        boolean keepText = hit != null && ((hit.type == R_TEXT && hit.target == editing) || hit.type == R_SEARCH);
        if (!keepText) { editing = null; searchFocus = false; }
        if (binding != null && (hit == null || (hit.type != R_KEY && hit.type != R_PKEY) || hit.target != binding)) binding = null;
        if (hit == null) { open = null; return true; }

        switch (hit.type) {
            case R_CAT -> { cat = (Category) hit.target; listDirty = true; scrollT = scroll = 0; panel = null; open = null; }
            case R_SEARCH -> searchFocus = true;
            case R_TOGGLE -> ((XModule) hit.target).toggle();
            case R_CUSTOM -> { panel = (XModule) hit.target; pScroll = pScrollT = 0; open = null; }
            case R_KEY, R_PKEY -> {
                XModule m = (XModule) hit.target;
                if (button == 1) KeybindManager.clear(m); else binding = binding == m ? null : m;
            }
            case R_BACK -> { panel = null; open = null; editing = null; }
            case R_BOOL -> ((BoolSetting) hit.target).toggle();
            case R_SLIDER -> { drag = (NumberSetting) hit.target; dragX = hit.x; dragW = hit.w; applyDrag(mx); }
            case R_MODE -> open = open == hit.target ? null : (Setting<?>) hit.target;
            case R_MODE_OPT -> { ((ModeSetting) hit.target).setIndex(hit.val); open = null; }
            case R_COLOR -> open = open == hit.target ? null : (Setting<?>) hit.target;
            case R_SWATCH -> { ((ColorSetting) hit.target).set(hit.val); }
            case R_TEXT -> editing = (TextSetting) hit.target;
            case R_ACTION -> {
                ActionSetting a = (ActionSetting) hit.target;
                if (a.confirmText() != null) ask(a.confirmText(), a::run); else a.run();
            }
            case R_EDIT_HUD -> client.setScreen(new HudEditorScreen(this));
            case R_RESET_HUD -> ask("Move every HUD element back to its default position?", ModuleManager.INSTANCE::resetHudPositions);
            case R_RESET_ALL -> ask("Reset ALL modules, settings and keybinds to defaults?", ModuleManager.INSTANCE::resetAll);
            case R_YES -> { Runnable r = confirmAct; confirmAct = null; if (r != null) r.run(); say("Done"); }
            case R_NO -> confirmAct = null;
            default -> {}
        }
        return true;
    }

    private void applyDrag(double mx) {
        if (drag == null) return;
        double f = Math.max(0, Math.min(1, (mx - dragX) / dragW));
        drag.setValue(drag.getMin() + f * (drag.getMax() - drag.getMin()));
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (drag != null) { applyDrag(mx); return true; }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) { drag = null; return super.mouseReleased(mx, my, b); }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (confirmAct != null) return true;
        if (panel != null) pScrollT -= (float) (vAmount * 28);
        else scrollT -= (float) (vAmount * 28);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (binding != null) { // key capture has priority over everything
            if (key == GLFW.GLFW_KEY_ESCAPE) say("Binding cancelled");
            else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) KeybindManager.clear(binding);
            else if (key != GLFW.GLFW_KEY_UNKNOWN) say(KeybindManager.assign(binding, key));
            binding = null;
            return true;
        }
        if (XmineClient.openKey().matchesKey(key, scan)) { close(); return true; } // Right Shift closes too
        if (confirmAct != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) confirmAct = null;
            else if (key == GLFW.GLFW_KEY_ENTER) { Runnable r = confirmAct; confirmAct = null; r.run(); }
            return true;
        }
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !editing.get().isEmpty()) editing.set(editing.get().substring(0, editing.get().length() - 1));
            else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) editing = null;
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) { query = query.substring(0, query.length() - 1); onQueryChanged(); }
            else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) searchFocus = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (panel != null) panel = null; else close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (chr < 32 || chr == 127) return false;
        if (editing != null) { editing.set(editing.get() + chr); return true; }
        if (panel == null && confirmAct == null && (searchFocus || Character.isLetterOrDigit(chr))) {
            if (query.length() < 32) { query += chr; searchFocus = true; onQueryChanged(); } // typing anywhere focuses search
            return true;
        }
        return false;
    }

    private void onQueryChanged() { listDirty = true; scrollT = scroll = 0; }

    @Override public void removed() { ConfigManager.saveNow(); }
}
