package dev.xmine.config.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xmine.config.core.Category;
import dev.xmine.config.core.XModule;
import dev.xmine.config.gui.UiUtil;
import dev.xmine.config.hud.*;
import dev.xmine.config.render.Overlay;
import dev.xmine.config.render.Projector;
import dev.xmine.config.setting.*;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class RenderModules {
    private RenderModules() {}

    // ------------------------------------------------------------------ Fullbright
    public static final class Fullbright extends XModule {
        private boolean applied;
        public Fullbright() { super("Fullbright", "See clearly in the dark (client-side night vision)", Category.RENDER); }
        @Override public void onTick() {
            StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            // refresh before the vanilla "fading" flicker starts (<200 ticks left)
            if (cur == null || cur.getDuration() < 300) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 800, 0, false, false, false));
                applied = true;
            }
        }
        @Override public void onDisable() {
            if (applied && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            applied = false;
        }
    }

    // ------------------------------------------------------------------ ESP family
    abstract static class EspBase extends XModule {
        protected final ModeSetting style = add(new ModeSetting("Style", "3D Box", "3D Box", "2D Box"));
        protected final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 1).suffix(" blocks"));
        protected final NumberSetting thick = add(new NumberSetting("Line width", 1, 1, 3, 1));

        EspBase(String n, String d) { super(n, d, Category.RENDER); }
        abstract int colorFor(Entity e); // -1 = skip

        @Override public void onHud(DrawContext c, float td) {
            double r2 = range.get() * range.get();
            boolean d3 = style.is("3D Box");
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player && !PlayerModules.Freecam.active) continue;
                if (mc.player.squaredDistanceTo(e) > r2) continue;
                int col = colorFor(e);
                if (col < 0) continue;
                Overlay.entityBox(c, e, td, 0xFF000000 | col, d3, thick.getFloat());
            }
        }
    }

    public static final class Esp extends EspBase {
        private final BoolSetting players = add(new BoolSetting("Players", true));
        private final BoolSetting mobs = add(new BoolSetting("Mobs", true));
        private final BoolSetting items = add(new BoolSetting("Items", false));
        public Esp() { super("ESP", "Boxes around players, mobs and items through walls"); }
        @Override int colorFor(Entity e) {
            if (e instanceof PlayerEntity) return players.get() ? 0xFF5555 : -1;
            if (e instanceof MobEntity) return mobs.get() ? (e instanceof HostileEntity ? 0xFFAA00 : 0x55FF55) : -1;
            if (e instanceof ItemEntity) return items.get() ? 0x55FFFF : -1;
            return -1;
        }
    }

    public static final class PlayerEsp extends EspBase {
        private final ColorSetting color = add(new ColorSetting("Color", 0xFF5555));
        public PlayerEsp() { super("Player ESP", "Boxes around other players"); }
        @Override int colorFor(Entity e) { return e instanceof PlayerEntity ? color.get() : -1; }
    }

    public static final class ItemEsp extends EspBase {
        private final ColorSetting color = add(new ColorSetting("Color", 0x55FFFF));
        private final BoolSetting names = add(new BoolSetting("Show names", true));
        public ItemEsp() { super("Item ESP", "Boxes around dropped items"); }
        @Override int colorFor(Entity e) { return e instanceof ItemEntity ? color.get() : -1; }
        @Override public void onHud(DrawContext c, float td) {
            super.onHud(c, td);
            if (!names.get()) return;
            double r2 = range.get() * range.get();
            double[] p = new double[2];
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity ie) || mc.player.squaredDistanceTo(e) > r2) continue;
                if (Projector.project(e.getX(), e.getY() + e.getHeight() + 0.3, e.getZ(), p))
                    Overlay.label(c, ie.getStack().getName().getString() + (ie.getStack().getCount() > 1 ? " x" + ie.getStack().getCount() : ""), p[0], p[1], 0xFFFFFFFF, 0.8f, true);
            }
        }
    }

    public static final class MobEsp extends EspBase {
        private final ColorSetting hostile = add(new ColorSetting("Hostile color", 0xFFAA00));
        private final ColorSetting passive = add(new ColorSetting("Passive color", 0x55FF55));
        public MobEsp() { super("Mob ESP", "Boxes around hostile and passive mobs"); }
        @Override int colorFor(Entity e) { return e instanceof MobEntity ? (e instanceof HostileEntity ? hostile.get() : passive.get()) : -1; }
    }

    public static final class Tracers extends XModule {
        private final BoolSetting players = add(new BoolSetting("Players", true));
        private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
        private final BoolSetting items = add(new BoolSetting("Items", false));
        private final ModeSetting origin = add(new ModeSetting("Origin", "Crosshair", "Crosshair", "Bottom"));
        private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 1).suffix(" blocks"));
        private final NumberSetting thick = add(new NumberSetting("Line width", 1, 1, 3, 1));
        private final double[] p = new double[2];

        public Tracers() { super("Tracers", "Lines from your crosshair to nearby entities", Category.RENDER); }

        @Override public void onHud(DrawContext c, float td) {
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            double ox = sw / 2.0, oy = origin.is("Bottom") ? sh : sh / 2.0, r2 = range.get() * range.get();
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || mc.player.squaredDistanceTo(e) > r2) continue;
                int col;
                if (e instanceof PlayerEntity) { if (!players.get()) continue; col = 0xFFFF5555; }
                else if (e instanceof MobEntity) { if (!mobs.get()) continue; col = e instanceof HostileEntity ? 0xFFFFAA00 : 0xFF55FF55; }
                else if (e instanceof ItemEntity) { if (!items.get()) continue; col = 0xFF55FFFF; }
                else continue;
                double x = MathHelper.lerp(td, e.prevX, e.getX()), y = MathHelper.lerp(td, e.prevY, e.getY()) + e.getHeight() / 2, z = MathHelper.lerp(td, e.prevZ, e.getZ());
                if (Projector.project(x, y, z, p)) Overlay.line2d(c, ox, oy, p[0], p[1], col, thick.getFloat());
            }
        }
    }

    public static final class Nametags extends XModule {
        private final BoolSetting health = add(new BoolSetting("Show health", true));
        private final BoolSetting distance = add(new BoolSetting("Show distance", true));
        private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 1).suffix(" blocks"));
        private final double[] p = new double[2];

        public Nametags() { super("Nametags", "Name, health and distance tags above players", Category.RENDER); }

        @Override public void onHud(DrawContext c, float td) {
            double r2 = range.get() * range.get();
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof PlayerEntity pl) || e == mc.player || mc.player.squaredDistanceTo(e) > r2) continue;
                double x = MathHelper.lerp(td, e.prevX, e.getX()), y = MathHelper.lerp(td, e.prevY, e.getY()) + e.getHeight() + 0.45, z = MathHelper.lerp(td, e.prevZ, e.getZ());
                if (!Projector.project(x, y, z, p)) continue;
                StringBuilder sb = new StringBuilder(pl.getName().getString());
                if (health.get()) sb.append("  ").append(Math.round(pl.getHealth() + pl.getAbsorptionAmount())).append("hp");
                if (distance.get()) sb.append("  ").append(Math.round(mc.player.distanceTo(e))).append("m");
                Overlay.label(c, sb.toString(), p[0], p[1], 0xFFFFFFFF, scale.getFloat(), true);
            }
        }
    }

    // ------------------------------------------------------------------ Waypoints
    public static final class Waypoints extends XModule {
        public static final class Waypoint { String name, dim; int x, y, z; }
        private final List<Waypoint> list = new ArrayList<>();
        private final NumberSetting maxDist = add(new NumberSetting("Max distance (0 = unlimited)", 0, 0, 10000, 50));
        private final ColorSetting color = add(new ColorSetting("Color", 0x55FFFF));
        private final BoolSetting dist = add(new BoolSetting("Show distance", true));
        private final double[] p = new double[2];

        public Waypoints() {
            super("Waypoints", "Saved locations shown as on-screen markers", Category.RENDER);
            alsoIn(Category.WORLD);
            add(new ActionSetting("Add here", "Add waypoint", null, this::addHere));
            add(new ActionSetting("Remove last", "Remove last", null, () -> { if (!list.isEmpty()) { list.remove(list.size() - 1); dev.xmine.config.configuration.ConfigManager.markDirty(); } }));
            add(new ActionSetting("Clear all", "Clear all", "Delete ALL waypoints?", () -> { list.clear(); dev.xmine.config.configuration.ConfigManager.markDirty(); }));
        }

        private void addHere() {
            if (mc.player == null || mc.world == null) return;
            Waypoint w = new Waypoint();
            w.name = "Waypoint " + (list.size() + 1);
            w.x = mc.player.getBlockX(); w.y = mc.player.getBlockY(); w.z = mc.player.getBlockZ();
            w.dim = mc.world.getRegistryKey().getValue().toString();
            list.add(w);
            dev.xmine.config.configuration.ConfigManager.markDirty();
            NotificationManager.push("Added " + w.name);
        }

        @Override public void onHud(DrawContext c, float td) {
            String dim = mc.world.getRegistryKey().getValue().toString();
            for (Waypoint w : list) {
                if (!w.dim.equals(dim)) continue;
                double d = Math.sqrt(mc.player.squaredDistanceTo(w.x + 0.5, w.y + 0.5, w.z + 0.5));
                if (maxDist.get() > 0 && d > maxDist.get()) continue;
                if (!Projector.project(w.x + 0.5, w.y + 1.0, w.z + 0.5, p)) continue;
                int col = 0xFF000000 | color.get();
                c.fill((int) p[0] - 2, (int) p[1] + 10, (int) p[0] + 3, (int) p[1] + 15, col);
                Overlay.label(c, w.name + (dist.get() ? " (" + Math.round(d) + "m)" : ""), p[0], p[1], col, 0.9f, true);
            }
        }

        @Override protected void saveExtra(JsonObject o) {
            JsonArray a = new JsonArray();
            for (Waypoint w : list) {
                JsonObject j = new JsonObject();
                j.addProperty("name", w.name); j.addProperty("dim", w.dim);
                j.addProperty("x", w.x); j.addProperty("y", w.y); j.addProperty("z", w.z);
                a.add(j);
            }
            o.add("waypoints", a);
        }

        @Override protected void loadExtra(JsonObject o) {
            if (!o.has("waypoints")) return;
            list.clear();
            o.getAsJsonArray("waypoints").forEach(e -> {
                JsonObject j = e.getAsJsonObject();
                Waypoint w = new Waypoint();
                w.name = j.get("name").getAsString(); w.dim = j.get("dim").getAsString();
                w.x = j.get("x").getAsInt(); w.y = j.get("y").getAsInt(); w.z = j.get("z").getAsInt();
                list.add(w);
            });
        }
    }

    public static final class BlockOverlay extends XModule {
        private final ColorSetting color = add(new ColorSetting("Color", 0x5B8CFF));
        private final NumberSetting thick = add(new NumberSetting("Line width", 2, 1, 4, 1));
        public BlockOverlay() { super("Block Overlay", "Custom highlight around the block you are looking at", Category.RENDER); }
        @Override public void onHud(DrawContext c, float td) {
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos bp = hit.getBlockPos();
            BlockState st = mc.world.getBlockState(bp);
            VoxelShape shape = st.getOutlineShape(mc.world, bp);
            Box b = shape.isEmpty() ? new Box(bp) : shape.getBoundingBox().offset(bp);
            Overlay.box3d(c, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, 0xFF000000 | color.get(), thick.getFloat());
        }
    }

    // ------------------------------------------------------------------ Damage indicator
    public static final class DamageIndicator extends XModule {
        private static final class Pop { double x, y, z; String text; int age; }
        private final Int2FloatOpenHashMap hp = new Int2FloatOpenHashMap();
        private final ArrayList<Pop> pops = new ArrayList<>();
        private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        private final double[] p = new double[2];

        public DamageIndicator() { super("Damage Indicator", "Floating damage numbers above entities", Category.RENDER); hp.defaultReturnValue(-1f); }

        @Override public void onDisable() { hp.clear(); pops.clear(); }

        @Override public void onTick() {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le == mc.player || mc.player.squaredDistanceTo(le) > 1024) continue;
                float now = le.getHealth(), old = hp.put(le.getId(), now);
                if (old >= 0 && now < old - 0.05f) {
                    Pop pop = new Pop();
                    pop.x = le.getX(); pop.y = le.getY() + le.getHeight(); pop.z = le.getZ();
                    pop.text = "-" + Math.round((old - now) * 10) / 10f;
                    pops.add(pop);
                }
            }
            for (int i = pops.size() - 1; i >= 0; i--) if (++pops.get(i).age > 30) pops.remove(i);
            if (hp.size() > 600) hp.clear(); // dead/unloaded ids: cheap reset instead of bookkeeping
        }

        @Override public void onHud(DrawContext c, float td) {
            for (Pop pop : pops) {
                float t = (pop.age + td) / 30f;
                if (!Projector.project(pop.x, pop.y + 0.3 + t * 0.8, pop.z, p)) continue;
                int a = Math.max(8, (int) (255 * (1f - t)));
                Overlay.label(c, pop.text, p[0], p[1], (a << 24) | 0xFF5555, scale.getFloat(), false);
            }
        }
    }

    public static final class MotionBlur extends XModule {
        private final NumberSetting strength = add(new NumberSetting("Strength", 50, 0, 100, 1).suffix("%"));
        public MotionBlur() { super("Motion Blur", "PLACEHOLDER - real motion blur needs a post-processing shader, not implemented yet", Category.RENDER); }
        @Override public void onEnable() { NotificationManager.push("Motion Blur: not implemented yet (settings only)"); }
    }

    // ------------------------------------------------------------------ Keystrokes
    public static final class Keystrokes extends HudModule {
        private final BoolSetting wasd = add(new BoolSetting("W/A/S/D", true));
        private final BoolSetting space = add(new BoolSetting("Space", true));
        private final BoolSetting mouse = add(new BoolSetting("Mouse buttons", true));
        private final BoolSetting cps = add(new BoolSetting("Show CPS on mouse keys", true));
        private final NumberSetting size = add(new NumberSetting("Key size", 22, 14, 36, 1));
        private final ColorSetting pressed = add(new ColorSetting("Pressed color", 0xFFFFFF));
        private final float[] anim = new float[7];

        public Keystrokes() {
            super("Keystrokes", "Shows WASD, space and mouse button presses", Category.RENDER, 0.4, 70);
            alsoIn(Category.HUD);
        }

        @Override protected void beforeRender() { ClickTracker.poll(mc); }

        private int s() { return size.getInt(); }
        @Override public int contentWidth() { return s() * 3 + 4; }
        @Override public int contentHeight() {
            int rows = (wasd.get() ? 2 : 0) + (space.get() ? 1 : 0) + (mouse.get() ? 1 : 0);
            return Math.max(1, rows * s() + Math.max(0, rows - 1) * 2);
        }

        private void key(DrawContext c, int i, boolean down, int x, int y, int w, int h, String label) {
            anim[i] += ((down ? 1f : 0f) - anim[i]) * 0.4f;
            UiUtil.roundRect(c, x, y, w, h, 3, UiUtil.lerp(argb(0x000000) & 0x00FFFFFF | ((int) (alphaF() * 140) << 24),
                    (int) (alphaF() * 230) << 24 | pressed.get(), anim[i]));
            int tc = UiUtil.lerp(0xFFFFFFFF, 0xFF000000, anim[i]);
            UiUtil.centerText(c, label, x + w / 2, y + (h - 9) / 2 + 1, tc);
        }

        @Override protected void renderContent(DrawContext c, float td) {
            int s = s(), g = 2, y = 0;
            if (wasd.get()) {
                key(c, 0, mc.options.forwardKey.isPressed(), s + g, y, s, s, "W");
                y += s + g;
                key(c, 1, mc.options.leftKey.isPressed(), 0, y, s, s, "A");
                key(c, 2, mc.options.backKey.isPressed(), s + g, y, s, s, "S");
                key(c, 3, mc.options.rightKey.isPressed(), 2 * (s + g), y, s, s, "D");
                y += s + g;
            }
            if (space.get()) { key(c, 4, mc.options.jumpKey.isPressed(), 0, y, 3 * s + 2 * g, s, "SPACE"); y += s + g; }
            if (mouse.get()) {
                int w = (3 * s + g) / 2 - 1;
                key(c, 5, ClickTracker.leftDown(), 0, y, w, s, cps.get() ? "L " + ClickTracker.leftCps() : "LMB");
                key(c, 6, ClickTracker.rightDown(), w + g, y, w, s, cps.get() ? "R " + ClickTracker.rightCps() : "RMB");
            }
        }
    }

    // ------------------------------------------------------------------ Text HUDs
    public static final class FpsCounter extends TextHudModule {
        private final BoolSetting label = add(new BoolSetting("Show FPS label", true));
        private final ModeSetting style = add(new ModeSetting("Display style", "Prefix", "Prefix", "Suffix", "Colored"));
        private int fps;

        public FpsCounter() {
            super("FPS Counter", "Frames per second", Category.RENDER, 0.4, 1);
            alsoIn(Category.HUD);
            enabledByDefault();
        }

        @Override protected void updateLines() {
            fps = HudManager.fps();
            lines = new String[]{!label.get() ? String.valueOf(fps) : style.is("Suffix") ? fps + " FPS" : "FPS: " + fps};
        }

        @Override protected int lineColor(int i) {
            return style.is("Colored") ? (fps >= 60 ? 0x55FF55 : fps >= 30 ? 0xFFFF55 : 0xFF5555) : color.get();
        }
    }

    public static final class Coordinates extends TextHudModule {
        private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        private final BoolSetting showX = add(new BoolSetting("Show X", true));
        private final BoolSetting showY = add(new BoolSetting("Show Y", true));
        private final BoolSetting showZ = add(new BoolSetting("Show Z", true));
        private final BoolSetting dir = add(new BoolSetting("Show direction", true));
        private final NumberSetting dec = add(new NumberSetting("Decimals", 0, 0, 2, 1));
        private final ModeSetting layout = add(new ModeSetting("Layout", "Vertical", "Vertical", "Horizontal"));

        public Coordinates() {
            super("Coordinates", "Your X / Y / Z position and facing", Category.RENDER, 0.4, 14);
            alsoIn(Category.HUD);
            enabledByDefault();
        }

        @Override protected void updateLines() {
            if (mc.player == null) return;
            int d = dec.getInt();
            ArrayList<String> l = new ArrayList<>(4);
            if (showX.get()) l.add("X: " + fmt(mc.player.getX(), d));
            if (showY.get()) l.add("Y: " + fmt(mc.player.getY(), d));
            if (showZ.get()) l.add("Z: " + fmt(mc.player.getZ(), d));
            if (dir.get()) l.add(DIRS[Math.floorMod(Math.round(MathHelper.wrapDegrees(mc.player.getYaw()) / 45f), 8)]);
            if (l.isEmpty()) l.add("-");
            lines = layout.is("Horizontal") ? new String[]{String.join("  ", l)} : l.toArray(new String[0]);
        }
    }

    public static final class PingDisplay extends TextHudModule {
        private final ModeSetting style = add(new ModeSetting("Display style", "Colored", "Plain", "Colored"));
        private int ping;

        public PingDisplay() { super("Ping Display", "Latency to the server", Category.RENDER, 0.4, 30); alsoIn(Category.HUD); }

        @Override protected void updateLines() {
            if (mc.player == null || mc.getNetworkHandler() == null) return;
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            ping = e == null ? 0 : e.getLatency();
            lines = new String[]{mc.isInSingleplayer() ? "Ping: local" : "Ping: " + ping + " ms"};
        }

        @Override protected int lineColor(int i) {
            return style.is("Colored") ? (ping < 80 ? 0x55FF55 : ping < 150 ? 0xFFFF55 : 0xFF5555) : color.get();
        }
    }
}
