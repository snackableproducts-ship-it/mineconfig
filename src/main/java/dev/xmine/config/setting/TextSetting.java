package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String name, String def, int maxLength) {
        super(name, def);
        this.maxLength = maxLength;
    }

    public int maxLength() { return maxLength; }

    @Override protected String sanitize(String v) {
        if (v == null) return "";
        return v.length() > maxLength ? v.substring(0, maxLength) : v;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsString()); }
}
