package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** RGB colour (0xRRGGBB, no alpha). The GUI offers a preset palette. */
public final class ColorSetting extends Setting<Integer> {
    public static final int[] PALETTE = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5B8CFF,
            0xAA55FF, 0xFF55FF, 0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000
    };

    public ColorSetting(String name, int rgb) { super(name, rgb & 0xFFFFFF); }

    @Override protected Integer sanitize(Integer v) { return v & 0xFFFFFF; }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsInt()); }
}
