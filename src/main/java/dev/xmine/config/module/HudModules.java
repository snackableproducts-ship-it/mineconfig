package dev.xmine.config.module;

import dev.xmine.config.XmineClient;
import dev.xmine.config.core.Category;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;
import dev.xmine.config.gui.UiUtil;
import dev.xmine.config.hud.*;
import dev.xmine.config.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class HudModules {
    private HudModules() {}

    public static final class ArrayListHud extends HudModule {
        private final BoolSetting hideHud = add(new BoolSetting("Hide HUD modules", true));
        private final BoolSetting showKeys = add(new BoolSetting("Show keybinds", false));
        private final ModeSetting sort = add(new ModeSetting("Sort", "Length", "Length", "Alphabetical"));
        private final ModeSetting align = add(new ModeSetting("Alignment", "Right", "Left", "Right"));
        private final ModeSetting colors = add(new ModeSetting("Color mode", "Rainbow", "Static", "Rainbow"));
        private String[] labels = new String[0];
        private int builtVersion = -1, builtKey = -1, maxW;

        public ArrayListHud() {
            super("Array List", "List of enabled modules", Category.HUD, 99, 1);
            enabledByDefault();
        }

        /** Rebuilds only when a module was toggled or a display option changed, not every frame. */
        @Override public void onTick() {
            int key = (hideHud.get() ? 1 : 0) + (showKeys.get() ? 2 : 0) + sort.index() * 4;
            if (builtVersion == ModuleManager.INSTANCE.version() && key == builtKey) return;
            builtVersion = ModuleManager.INSTANCE.version(); builtKey = key;
            List<String> l = new ArrayList<>();
            for (XModule m : ModuleManager.INSTANCE.all()) {
                if (!m.isEnabled() || (hideHud.get() && m instanceof HudModule)) continue;
                l.add(m.getName() + (showKeys.get() && m.getKeybind().isBound() ? " [" + m.getKeybind().display() + "]" : ""));
            }
            labels = l.toArray(new String[0]);
            if (sort.is("Length")) Arrays.sort(labels, Comparator.comparingInt((String s) -> mc.textRenderer.getWidth(s)).reversed());
            else Arrays.sort(labels);
            maxW = 0;
            for (String s : labels) maxW = Math.max(maxW, mc.textRenderer.getWidth(s));
        }

        private boolean preview() { return labels.length == 0 && HudManager.isEditing(); }
        @Override public int contentWidth() { return preview() ? tw("Fullbright") : Math.max(1, maxW); }
        @Override public int contentHeight() { return preview() ? 19 : Math.max(1, labels.length * 10 - 1); }

        @Override protected void renderContent(DrawContext c, float td) {
            String[] l = preview() ? new String[]{"Fullbright", "Sprint"} : labels;
            int w = contentWidth();
            long t = System.currentTimeMillis() / 20;
            for (int i = 0; i < l.length; i++) {
                int x = align.is("Right") ? w - tw(l[i]) : 0;
                int col = colors.is("Rainbow") ? MathHelper.hsvToRgb(((t + i * 12L) % 360) / 360f, 0.6f, 1f) : color.get();
                text(c, l[i], x, i * 10, col);
            }
        }
    }

    public static final class Watermark extends HudModule {
        private final TextSetting label = add(new TextSetting("Text", XmineClient.NAME, 24));
        private final BoolSetting version = add(new BoolSetting("Show version", true));
        private final BoolSetting accentBar = add(new BoolSetting("Accent bar", true));

        public Watermark() {
            super("Watermark", "Client name tag", Category.HUD, 0.4, 94);
            enabledByDefault();
        }

        private String txt() { return label.get() + (version.get() ? " " + XmineClient.VERSION : ""); }
        @Override public int contentWidth() { return tw(txt()) + (accentBar.get() ? 4 : 0); }
        @Override public int contentHeight() { return 9; }
        @Override protected void renderContent(DrawContext c, float td) {
            int x = 0;
            if (accentBar.get()) { c.fill(0, -1, 2, 10, 0xFF000000 | ClickGuiColor.get()); x = 4; }
            text(c, txt(), x, 0, color.get());
        }
    }

    /** Tiny indirection so Watermark follows the GUI accent colour. */
    private static final class ClickGuiColor { static int get() { return MiscModules.ClickGui.accent() & 0xFFFFFF; } }

    public static final class Notifications extends HudModule {
        private final NumberSetting duration = add(new NumberSetting("Duration", 3, 1, 10, 0.5).suffix("s"));

        public Notifications() {
            super("Notifications", "Pop-ups when modules are toggled", Category.MISC, 70, 90);
            alsoIn(Category.HUD);
            enabledByDefault();
        }

        private int live() {
            long now = System.currentTimeMillis(), d = (long) (duration.get() * 1000);
            int n = 0;
            for (NotificationManager.Entry e : NotificationManager.entries()) if (now - e.created < d) n++;
            return Math.min(n, 6);
        }

        @Override public int contentWidth() { return 150; }
        @Override public int contentHeight() { int n = live(); return n == 0 ? (HudManager.isEditing() ? 12 : 1) : n * 14 - 2; }

        @Override protected void renderContent(DrawContext c, float td) {
            long now = System.currentTimeMillis(), d = (long) (duration.get() * 1000);
            int y = 0, acc = 0xFF000000 | ClickGuiColor.get();
            boolean any = false;
            for (NotificationManager.Entry e : NotificationManager.entries()) {
                long age = now - e.created;
                if (age >= d) continue;
                any = true;
                float a = age > d - 400 ? (d - age) / 400f : 1f;
                UiUtil.roundRect(c, 0, y, 150, 12, 3, UiUtil.alpha(0xCC12141B, a));
                c.fill(0, y + 2, 2, y + 10, UiUtil.alpha(acc, a));
                c.drawText(mc.textRenderer, UiUtil.trim(e.text, 140), 6, y + 2, UiUtil.alpha(0xFFFFFFFF, Math.max(a, 0.05f)), false);
                y += 14;
                if (y > 6 * 14) break;
            }
            if (!any && HudManager.isEditing()) {
                UiUtil.roundRect(c, 0, 0, 150, 12, 3, 0xCC12141B);
                c.fill(0, 2, 2, 10, acc);
                UiUtil.text(c, "Sprint enabled", 6, 2, 0xFFFFFFFF);
            }
        }
    }
}
