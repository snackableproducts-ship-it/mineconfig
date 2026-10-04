package dev.xmine.config.core;

import dev.xmine.config.XmineClient;
import dev.xmine.config.configuration.ConfigManager;
import dev.xmine.config.hud.HudModule;
import dev.xmine.config.render.Projector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry + dispatcher. Ticks and renders ONLY the enabled modules (cached array, rebuilt on toggle). */
public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<XModule> all = new ArrayList<>();
    private final Map<Class<?>, XModule> byClass = new HashMap<>();
    private XModule[] active = new XModule[0];
    private boolean quiet;
    private int version;

    private ModuleManager() {}

    public void register(XModule m) {
        all.add(m);
        byClass.put(m.getClass(), m);
        refreshActive();
    }

    public List<XModule> all() { return all; }
    public <T extends XModule> T get(Class<T> c) { return c.cast(byClass.get(c)); }
    public int version() { return version; }

    public void refreshActive() {
        ArrayList<XModule> l = new ArrayList<>();
        for (XModule m : all) if (m.isEnabled()) l.add(m);
        active = l.toArray(new XModule[0]);
        version++;
    }

    /** While quiet, enable/disable does not spam notifications or mark the config dirty. */
    public boolean isQuiet() { return quiet; }
    public void setQuiet(boolean q) { quiet = q; }
    public void runQuiet(Runnable r) {
        boolean prev = quiet;
        quiet = true;
        try { r.run(); } finally { quiet = prev; }
    }

    public void tick() {
        for (XModule m : active) {
            if (m.faulted) continue;
            try { m.onTick(); } catch (Throwable t) { fault(m, t); }
        }
    }

    public void renderHud(DrawContext ctx, float td) {
        Projector.begin(MinecraftClient.getInstance());
        for (XModule m : active) {
            if (m.faulted) continue;
            try { m.onHud(ctx, td); } catch (Throwable t) { fault(m, t); }
        }
    }

    private void fault(XModule m, Throwable t) {
        XmineClient.LOGGER.error("Module {} crashed and was disabled", m.getName(), t);
        m.faulted = true;
        m.setEnabled(false);
    }

    public int countIn(Category c) {
        if (c == null) return all.size();
        int n = 0;
        for (XModule m : all) if (m.isIn(c)) n++;
        return n;
    }

    /** Category filter + search across name, description and category labels. Null category = all. */
    public void query(Category c, String q, List<XModule> out) {
        out.clear();
        String[] tokens = q == null ? new String[0] : q.toLowerCase(Locale.ROOT).trim().split("\\s+");
        boolean searching = tokens.length > 0 && !tokens[0].isEmpty();
        for (XModule m : all) {
            if (!searching && c != null && !m.isIn(c)) continue;
            if (searching) {
                StringBuilder sb = new StringBuilder(m.getName()).append(' ').append(m.getDescription());
                for (Category cc : m.getCategories()) sb.append(' ').append(cc.label());
                String hay = sb.toString().toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String t : tokens) if (!hay.contains(t)) { ok = false; break; }
                if (!ok) continue;
            }
            out.add(m);
        }
    }

    public void resetAll() {
        runQuiet(() -> { for (XModule m : all) m.resetToDefaults(); });
        ConfigManager.saveNow();
    }

    public void resetHudPositions() {
        for (XModule m : all) if (m instanceof HudModule h) h.resetPosition();
        ConfigManager.saveNow();
    }
}
