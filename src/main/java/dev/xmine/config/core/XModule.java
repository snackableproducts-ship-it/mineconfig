package dev.xmine.config.core;

import com.google.gson.JsonObject;
import dev.xmine.config.XmineClient;
import dev.xmine.config.configuration.ConfigManager;
import dev.xmine.config.hud.NotificationManager;
import dev.xmine.config.input.Keybind;
import dev.xmine.config.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Base class of every feature. A module has a name, description, category, ON/OFF state,
 * keybind and a list of settings. Only ENABLED modules receive tick/HUD callbacks
 * (see {@link ModuleManager}), so disabled modules cost nothing.
 *
 * Named XModule to avoid clashing with java.lang.Module.
 */
public abstract class XModule {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name, description;
    private final Category category;
    private final EnumSet<Category> categories;
    private final List<Setting<?>> settings = new ArrayList<>();
    private List<Setting<?>> ordered;
    private final Keybind keybind = new Keybind();
    private boolean enabled, defaultEnabled, alwaysOn;

    // GUI animation state (0..1), owned by the GUI but stored here to avoid per-frame maps
    public float uiAnim, uiHover;
    // Set by KeybindManager for edge detection
    public boolean keyHeld;
    // Set by ModuleManager when the module threw; faulted modules are skipped
    public boolean faulted;

    protected XModule(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.categories = EnumSet.of(category);
    }

    // ---- construction helpers -------------------------------------------------
    protected final <T extends Setting<?>> T add(T s) { settings.add(s); return s; }
    /** "Common" settings (position/scale/opacity...) are listed after module-specific ones. */
    protected final <T extends Setting<?>> T addCommon(T s) { s.setCommon(true); settings.add(s); return s; }
    protected final void alsoIn(Category... cs) { categories.addAll(Arrays.asList(cs)); }
    protected final void enabledByDefault() { defaultEnabled = true; enabled = true; }
    protected final void alwaysOn() { alwaysOn = true; defaultEnabled = true; enabled = true; }
    protected final void defaultKey(int key) { keybind.setDefault(key); }

    // ---- accessors ------------------------------------------------------------
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isIn(Category c) { return categories.contains(c); }
    public EnumSet<Category> getCategories() { return categories; }
    public boolean isEnabled() { return enabled; }
    public boolean isAlwaysOn() { return alwaysOn; }
    public Keybind getKeybind() { return keybind; }
    public boolean hasSettings() { return !settings.isEmpty(); }

    public List<Setting<?>> getSettings() {
        if (ordered == null) {
            ordered = new ArrayList<>(settings);
            ordered.sort(Comparator.comparing((Setting<?> st) -> st.isCommon())); // stable: specific settings first
        }
        return ordered;
    }

    // ---- state ----------------------------------------------------------------
    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean v) {
        if (alwaysOn) v = true;
        if (v == enabled) return;
        enabled = v;
        ModuleManager mm = ModuleManager.INSTANCE;
        mm.refreshActive();
        try {
            if (v) onEnable(); else onDisable();
        } catch (Throwable t) {
            XmineClient.LOGGER.error("Module {} failed during {}", name, v ? "enable" : "disable", t);
        }
        if (!mm.isQuiet()) {
            ConfigManager.markDirty();
            NotificationManager.push(name + (v ? " enabled" : " disabled"));
        }
    }

    // ---- lifecycle hooks (override as needed; all must be null-safe w.r.t. mc.player/world) ----
    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onHud(DrawContext ctx, float tickDelta) {}
    /** Keybind pressed. Default: toggle. */
    public void onKeyPress() { toggle(); }
    /** True = enabled only while the key is held. */
    public boolean isHoldMode() { return false; }
    protected void onReset() {}

    // ---- persistence ----------------------------------------------------------
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("key", keybind.get());
        JsonObject s = new JsonObject();
        for (Setting<?> st : settings) if (st.isPersistent()) s.add(st.getName(), st.toJson());
        o.add("settings", s);
        saveExtra(o);
        return o;
    }

    public void fromJson(JsonObject o) {
        // settings first so onEnable() sees the loaded values
        if (o.has("settings") && o.get("settings").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting<?> st : settings) {
                if (!st.isPersistent() || !s.has(st.getName())) continue;
                try { st.fromJson(s.get(st.getName())); } catch (Exception ignored) { /* keep default */ }
            }
        }
        if (o.has("key")) { try { keybind.set(o.get("key").getAsInt()); } catch (Exception ignored) {} }
        try { loadExtra(o); } catch (Exception ignored) {}
        if (o.has("enabled")) { try { setEnabled(o.get("enabled").getAsBoolean()); } catch (Exception ignored) {} }
    }

    protected void saveExtra(JsonObject o) {}
    protected void loadExtra(JsonObject o) {}

    public void resetToDefaults() {
        for (Setting<?> s : settings) s.reset();
        keybind.reset();
        setEnabled(defaultEnabled);
        onReset();
    }
}
