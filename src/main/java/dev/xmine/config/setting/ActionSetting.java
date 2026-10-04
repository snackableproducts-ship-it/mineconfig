package dev.xmine.config.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button inside a settings panel. If confirmText is set, the GUI asks for confirmation first. */
public final class ActionSetting extends Setting<Boolean> {
    private final String label, confirmText;
    private final Runnable action;

    public ActionSetting(String name, String label, String confirmText, Runnable action) {
        super(name, false);
        this.label = label;
        this.confirmText = confirmText;
        this.action = action;
    }

    public String label() { return label; }
    public String confirmText() { return confirmText; }
    public void run() { action.run(); }

    @Override public boolean isPersistent() { return false; }
    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement e) {}
}
