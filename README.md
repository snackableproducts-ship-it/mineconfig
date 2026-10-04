# XMINE CONFIG  (Minecraft Java 1.21.1 · Fabric · Java 21)

Client-side mod: press **Right Shift** (rebindable in Controls) for the XMINE CONFIG menu.

## Build
1. Install JDK 21.
2. Add the Gradle wrapper (not included): copy `gradlew`, `gradlew.bat` and `gradle/` from the
   official Fabric example mod, or run `gradle wrapper --gradle-version 8.12` once.
3. `./gradlew build`  ->  `build/libs/xmine-config-1.0.0.jar`  (drop into `.minecraft/mods` with Fabric API).
   `./gradlew runClient` launches a dev client.
If a version in `gradle.properties` is not found, bump `fabric_version` to the latest `+1.21.1` build.

## Layout
| package | role |
|---|---|
| `XmineClient` | entry point, tick + HUD hooks |
| `core` | `XModule` base, `Category`, `ModuleManager` (ticks only enabled modules), `ModuleRegistry` |
| `setting` | Bool / Number / Mode / Color / Text / Action settings (+ JSON) |
| `input` | `Keybind`, `KeybindManager` (polling, conflict rules, hold mode) |
| `configuration` | `ConfigManager` -> `config/xmineconfig.json`, debounced + atomic |
| `render` | `Projector` + `Overlay` (world->screen ESP drawing) |
| `hud` | `HudModule` / `TextHudModule`, `HudManager`, CPS tracker, notifications |
| `gui` | `ClickGuiScreen`, `HudEditorScreen`, `UiUtil` |
| `module` | all modules, grouped per category |
| `mixin` | 2 accessors + 7 small mixins (all injectors `require = 0` so a mismatch can't crash the game) |

## Adding a module
```java
public static final class MyMod extends XModule {
    private final BoolSetting flag = add(new BoolSetting("Flag", true));
    public MyMod() { super("My Mod", "What it does", Category.MISC); }
    @Override public void onTick() { /* only runs while enabled */ }
}
```
then add `m.register(new ...MyMod());` in `ModuleRegistry`. The GUI, keybinds, search and
config saving pick it up automatically. HUD elements extend `HudModule`/`TextHudModule`.

## Known limitations
* **Not compiled or run by the author's tooling** - expect to fix a few mapping nits on first build.
* Motion Blur and Discord RPC are placeholders (settings only). Minimap is an entity radar, not terrain.
* ESP/Tracers/Block Finder draw as a 2D overlay projected from the camera (approximate with some FOV effects).
* Cheat-style modules (Aim Assist, Flight, Speed, No Fall, Freecam, ESP...) are for single-player/private use;
  most multiplayer servers prohibit them.
