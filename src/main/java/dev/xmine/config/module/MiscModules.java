package dev.xmine.config.module;

import dev.xmine.config.configuration.ConfigManager;
import dev.xmine.config.core.Category;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;
import dev.xmine.config.hud.NotificationManager;
import dev.xmine.config.input.KeybindManager;
import dev.xmine.config.setting.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MiscModules {
    private MiscModules() {}

    // ------------------------------------------------------------------ Zoom
    public static final class Zoom extends XModule {
        /** Read by GameRendererMixin. 1.0 = no zoom. */
        public static double factor = 1.0;
        private final NumberSetting amount = add(new NumberSetting("Zoom factor", 4, 1.5, 10, 0.5).suffix("x"));
        private final BoolSetting smooth = add(new BoolSetting("Smooth camera", true));
        private final BoolSetting hold = add(new BoolSetting("Hold key to zoom", true));
        private boolean prevSmooth;

        public Zoom() { super("Zoom", "Zoom in (bind a key; hold it to zoom)", Category.MISC); }

        @Override public boolean isHoldMode() { return hold.get(); }
        @Override public void onEnable() {
            factor = amount.get();
            if (mc.options != null) { prevSmooth = mc.options.smoothCameraEnabled; if (smooth.get()) mc.options.smoothCameraEnabled = true; }
        }
        @Override public void onTick() { factor = amount.get(); }
        @Override public void onDisable() { factor = 1.0; if (mc.options != null) mc.options.smoothCameraEnabled = prevSmooth; }
    }

    public static final class DiscordRpc extends XModule {
        private final BoolSetting server = add(new BoolSetting("Show server address", false));
        private final BoolSetting time = add(new BoolSetting("Show elapsed time", true));
        public DiscordRpc() { super("Discord RPC", "PLACEHOLDER - needs a Discord IPC library which is not bundled", Category.MISC); }
        @Override public void onEnable() { NotificationManager.push("Discord RPC: no IPC library bundled (settings only)"); }
    }

    // ------------------------------------------------------------------ Chat
    public static final class ChatTimestamps extends XModule {
        /** Read by ChatHudMixin. */
        public static boolean active;
        private final ModeSetting fmt = add(new ModeSetting("Format", "HH:mm", "HH:mm", "HH:mm:ss", "hh:mm a"));
        private final ColorSetting color = add(new ColorSetting("Color", 0xAAAAAA));
        private static ChatTimestamps instance;

        public ChatTimestamps() { super("Chat Timestamps", "Prefixes chat messages with the time", Category.MISC); instance = this; }
        @Override public void onEnable() { active = true; }
        @Override public void onDisable() { active = false; }

        public static Text decorate(Text msg) {
            if (instance == null) return msg;
            String t = LocalTime.now().format(DateTimeFormatter.ofPattern(instance.fmt.get(), Locale.ENGLISH));
            return Text.literal("[" + t + "] ").styled(s -> s.withColor(instance.color.get())).append(msg);
        }
    }

    public static final class AutoGG extends XModule {
        private final TextSetting message = add(new TextSetting("Message", "gg", 64));
        private final TextSetting triggers = add(new TextSetting("Trigger phrases (comma separated)", "has won,winner,game over,victory", 120));
        private final NumberSetting delay = add(new NumberSetting("Delay", 20, 0, 100, 5).suffix(" ticks"));
        private int pending = -1;
        private long lastSent;

        public AutoGG() { super("Auto GG", "Sends a message when the game ends (multiplayer)", Category.MISC); }

        /** Called from ChatHooks for every incoming chat/system message. */
        void onMessage(String text) {
            if (pending >= 0 || System.currentTimeMillis() - lastSent < 10_000) return;
            String low = text.toLowerCase(Locale.ROOT);
            for (String t : triggers.get().toLowerCase(Locale.ROOT).split(",")) {
                if (!t.isBlank() && low.contains(t.trim())) { pending = delay.getInt(); return; }
            }
        }

        @Override public void onTick() {
            if (pending < 0) return;
            if (pending-- == 0 && mc.getNetworkHandler() != null && !mc.isInSingleplayer()) {
                mc.getNetworkHandler().sendChatMessage(message.get());
                lastSent = System.currentTimeMillis();
            }
        }
        @Override public void onDisable() { pending = -1; }
    }

    public static final class ChatFilter extends XModule {
        private final TextSetting words = add(new TextSetting("Blocked words (comma separated)", "", 200));
        private final BoolSetting caseSensitive = add(new BoolSetting("Case sensitive", false));
        public ChatFilter() { super("Chat Filter", "Hides chat messages containing blocked words", Category.MISC); }

        boolean blocks(String text) {
            String list = words.get();
            if (list.isBlank()) return false;
            String hay = caseSensitive.get() ? text : text.toLowerCase(Locale.ROOT);
            for (String w : list.split(",")) {
                String t = caseSensitive.get() ? w.trim() : w.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty() && hay.contains(t)) return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ Utility modules
    public static final class ScreenshotManager extends XModule {
        private final BoolSetting notify = add(new BoolSetting("Notify when saved", true));

        public ScreenshotManager() {
            super("Screenshot Manager", "Take screenshots and open the screenshot folder", Category.MISC);
            alwaysOn();
            add(new ActionSetting("Capture", "Take screenshot", null, this::shot));
            add(new ActionSetting("Folder", "Open folder", null, () -> {
                File dir = new File(mc.runDirectory, "screenshots");
                dir.mkdirs();
                Util.getOperatingSystem().open(dir);
            }));
        }

        private void shot() {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(),
                    msg -> mc.execute(() -> { if (notify.get()) NotificationManager.push("Screenshot saved"); mc.inGameHud.getChatHud().addMessage(msg); }));
        }
    }

    public static final class ConfigManagerModule extends XModule {
        public ConfigManagerModule() {
            super("Config Manager", "Save, reload and reset your configuration", Category.MISC);
            alwaysOn();
            add(new ActionSetting("Save", "Save now", null, () -> { ConfigManager.saveNow(); NotificationManager.push("Config saved"); }));
            add(new ActionSetting("Load", "Reload from disk", "Reload the config file? Unsaved changes are lost.", () -> { ConfigManager.load(); NotificationManager.push("Config reloaded"); }));
            add(new ActionSetting("Folder", "Open config folder", null, () -> Util.getOperatingSystem().open(ConfigManager.file().getParent().toFile())));
            add(new ActionSetting("Reset HUD", "Reset HUD positions", "Move every HUD element back to its default position?", ModuleManager.INSTANCE::resetHudPositions));
            add(new ActionSetting("Reset all", "Reset ALL settings", "Reset ALL modules, settings and keybinds?", ModuleManager.INSTANCE::resetAll));
        }
    }

    public static final class ModuleKeybinds extends XModule {
        public ModuleKeybinds() {
            super("Module Keybinds", "Manage module keybinds (click a keybind chip in the list to assign one)", Category.MISC);
            alwaysOn();
            add(new ActionSetting("Clear", "Clear all keybinds", "Remove the keybind from EVERY module?", () -> { KeybindManager.clearAll(); NotificationManager.push("All keybinds cleared"); }));
        }
    }

    public static final class ClickGui extends XModule {
        private static ClickGui instance;
        private final ColorSetting accent = add(new ColorSetting("Accent color", 0x5B8CFF));
        private final BoolSetting animations = add(new BoolSetting("Animations", true));
        private final BoolSetting dim = add(new BoolSetting("Dim background", true));

        public ClickGui() {
            super("Click GUI", "The XMINE CONFIG menu itself (opens with Right Shift)", Category.MISC);
            alwaysOn();
            instance = this;
        }

        public static int accent() { return instance == null ? 0xFF5B8CFF : 0xFF000000 | instance.accent.get(); }
        public static boolean animations() { return instance == null || instance.animations.get(); }
        public static boolean dim() { return instance == null || instance.dim.get(); }
    }
}
