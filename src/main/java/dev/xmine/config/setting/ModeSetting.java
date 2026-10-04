package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Dropdown: one value out of a fixed list. */
public final class ModeSetting extends Setting<String> {
    private final String[] options;

    public ModeSetting(String name, String def, String... options) {
        super(name, def);
        this.options = options;
    }

    public String[] options() { return options; }

    @Override protected String sanitize(String v) {
        for (String o : options) if (o.equals(v)) return v;
        return getDefault();
    }

    public int index() {
        for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
        return 0;
    }

    public void setIndex(int i) { if (i >= 0 && i < options.length) set(options[i]); }
    public boolean is(String s) { return value.equals(s); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsString()); }
}
