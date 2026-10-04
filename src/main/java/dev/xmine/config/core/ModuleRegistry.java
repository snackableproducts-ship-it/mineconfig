package dev.xmine.config.core;

import dev.xmine.config.module.*;

/**
 * Single place that lists every module. To add a new module: write a class extending XModule
 * (or HudModule / TextHudModule) and add one line here. Nothing else needs to change.
 */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static void registerDefaults(ModuleManager m) {
        // Combat
        m.register(new CombatModules.CpsCounter());
        m.register(new CombatModules.HitboxDisplay());
        m.register(new CombatModules.Crosshair());
        m.register(new CombatModules.AimAssist());
        m.register(new CombatModules.TargetHud());
        m.register(new CombatModules.ArmorHud());
        m.register(new CombatModules.TotemCounter());
        m.register(new CombatModules.PotionEffects());
        m.register(new CombatModules.AttackIndicator());
        // Movement
        m.register(new MovementModules.Sprint());
        m.register(new MovementModules.AutoJump());
        m.register(new MovementModules.Step());
        m.register(new MovementModules.NoSlow());
        m.register(new MovementModules.SafeWalk());
        m.register(new MovementModules.Speed());
        m.register(new MovementModules.Flight());
        m.register(new MovementModules.HighJump());
        m.register(new MovementModules.InventoryMove());
        // Player
        m.register(new PlayerModules.AutoEat());
        m.register(new PlayerModules.AutoTool());
        m.register(new PlayerModules.AutoArmor());
        m.register(new PlayerModules.ItemSwap());
        m.register(new PlayerModules.FastPlace());
        m.register(new PlayerModules.FastBreak());
        m.register(new PlayerModules.NoFall());
        m.register(new PlayerModules.Freecam());
        // Render
        m.register(new RenderModules.Fullbright());
        m.register(new RenderModules.Esp());
        m.register(new RenderModules.PlayerEsp());
        m.register(new RenderModules.ItemEsp());
        m.register(new RenderModules.MobEsp());
        m.register(new RenderModules.Tracers());
        m.register(new RenderModules.Nametags());
        m.register(new RenderModules.Waypoints());
        m.register(new RenderModules.BlockOverlay());
        m.register(new RenderModules.DamageIndicator());
        m.register(new RenderModules.MotionBlur());
        m.register(new RenderModules.Keystrokes());
        m.register(new RenderModules.FpsCounter());
        m.register(new RenderModules.Coordinates());
        m.register(new RenderModules.PingDisplay());
        // World
        m.register(new WorldModules.BlockFinder());
        m.register(new WorldModules.ChestFinder());
        m.register(new WorldModules.EntityCounter());
        m.register(new WorldModules.TimeChanger());
        m.register(new WorldModules.WeatherChanger());
        m.register(new WorldModules.LightLevel());
        m.register(new WorldModules.Minimap());
        // HUD
        m.register(new HudModules.ArrayListHud());
        m.register(new HudModules.Watermark());
        m.register(new HudModules.Notifications());
        // Misc
        m.register(new MiscModules.Zoom());
        m.register(new MiscModules.DiscordRpc());
        m.register(new MiscModules.ChatTimestamps());
        m.register(new MiscModules.AutoGG());
        m.register(new MiscModules.ChatFilter());
        m.register(new MiscModules.ScreenshotManager());
        m.register(new MiscModules.ConfigManagerModule());
        m.register(new MiscModules.ModuleKeybinds());
        m.register(new MiscModules.ClickGui());
    }
}
