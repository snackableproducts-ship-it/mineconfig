package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** Numeric setting with enforced min / max / step. Rendered as a slider. */
public final class NumberSetting extends Setting<Double> {
    private final double min, max, step;
    private String suffix = "";

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min; this.max = max; this.step = step;
    }

    public NumberSetting suffix(String s) { this.suffix = s; return this; }

    @Override protected Double sanitize(Double v) {
        double d = Math.max(min, Math.min(max, v));
        if (step > 0) d = min + Math.round((d - min) / step) * step;
        d = Math.max(min, Math.min(max, d));
        return Math.round(d * 1e6) / 1e6;
    }

    public void setValue(double v) { set(v); }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return value.floatValue(); }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double fraction() { return max == min ? 0 : (value - min) / (max - min); }

    public String format() {
        int dec = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
        return String.format(Locale.ROOT, "%." + dec + "f", value) + suffix;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsDouble()); }
}
