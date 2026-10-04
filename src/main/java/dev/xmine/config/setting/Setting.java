package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import dev.xmine.config.configuration.ConfigManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Reusable setting base. The GUI renders any Setting subclass generically, so modules never need custom UI. */
public abstract class Setting<T> {
    private final String name;
    protected T value;
    private final T defaultValue;
    private BooleanSupplier visibleWhen = () -> true;
    private boolean common;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public T get() { return value; }
    public T getDefault() { return defaultValue; }

    public void set(T v) {
        T n = sanitize(v);
        if (!Objects.equals(n, value)) {
            value = n;
            ConfigManager.markDirty();
        }
    }

    protected T sanitize(T v) { return v; }
    public void reset() { set(defaultValue); }

    /** Hide this setting in the panel unless the condition holds (e.g. only show "Size" for style X). */
    public void visibleIf(BooleanSupplier s) { visibleWhen = s; }
    public boolean isVisible() { return visibleWhen.getAsBoolean(); }

    public void setCommon(boolean c) { common = c; }
    public boolean isCommon() { return common; }
    public boolean isPersistent() { return true; }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement e);
}
