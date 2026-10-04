package dev.xmine.config.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xmine.config.XmineClient;
import dev.xmine.config.core.ModuleManager;
import dev.xmine.config.core.XModule;
import dev.xmine.config.input.KeybindManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON persistence: config/xmineconfig.json.
 * Saves are debounced (1s after the last change) so dragging a slider doesn't hammer the disk,
 * and always flushed when the GUI closes or the game stops.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("xmineconfig.json");
    private static boolean dirty;
    private static long dirtyAt;

    private ConfigManager() {}

    public static Path file() { return FILE; }

    public static void markDirty() {
        if (ModuleManager.INSTANCE.isQuiet()) return;
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    public static void tick() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 1000) saveNow();
    }

    public static synchronized void saveNow() {
        dirty = false;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("configVersion", 1);
            JsonObject mods = new JsonObject();
            for (XModule m : ModuleManager.INSTANCE.all()) mods.add(m.getName(), m.toJson());
            root.add("modules", mods);

            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("xmineconfig.json.tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp)) { GSON.toJson(root, w); }
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            XmineClient.LOGGER.error("Could not save config", e);
        }
    }

    public static synchronized void load() {
        ModuleManager mm = ModuleManager.INSTANCE;
        if (!Files.exists(FILE)) { saveNow(); return; }
        mm.setQuiet(true);
        try (BufferedReader r = Files.newBufferedReader(FILE)) {
            JsonElement parsed = JsonParser.parseReader(r);
            JsonObject mods = parsed.getAsJsonObject().getAsJsonObject("modules");
            if (mods != null) {
                for (XModule m : mm.all()) {
                    if (!mods.has(m.getName())) continue;
                    try { m.fromJson(mods.getAsJsonObject(m.getName())); }
                    catch (Exception e) { XmineClient.LOGGER.warn("Bad config entry for {}", m.getName(), e); }
                }
            }
        } catch (Exception e) {
            // Corrupt file: keep a backup and continue with defaults
            XmineClient.LOGGER.error("Config unreadable, backing up and using defaults", e);
            try { Files.move(FILE, FILE.resolveSibling("xmineconfig.json.bak"), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception ignored) {}
        } finally {
            mm.setQuiet(false);
            mm.refreshActive();
            KeybindManager.invalidate();
        }
    }
}
