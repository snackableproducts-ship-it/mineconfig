package dev.xmine.config.module;

import dev.xmine.config.core.Category;
import dev.xmine.config.core.XModule;
import dev.xmine.config.gui.UiUtil;
import dev.xmine.config.hud.*;
import dev.xmine.config.render.Overlay;
import dev.xmine.config.setting.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorldModules {
    private WorldModules() {}

    // ------------------------------------------------------------------ Block finder
    public static final class BlockFinder extends XModule {
        private static final String[] NAMES = {"Diamond", "Iron", "Gold", "Coal", "Copper", "Redstone", "Lapis", "Emerald", "Ancient Debris", "Spawner"};
        private final ModeSetting target = add(new ModeSetting("Target", "Diamond", NAMES));
        private final NumberSetting radius = add(new NumberSetting("Radius", 16, 8, 32, 1).suffix(" blocks"));
        private final NumberSetting maxShown = add(new NumberSetting("Max highlights", 100, 10, 300, 10));
        private final ColorSetting color = add(new ColorSetting("Color", 0x55FFFF));
        private final NumberSetting thick = add(new NumberSetting("Line width", 1, 1, 3, 1));

        private List<BlockPos> results = new ArrayList<>(), pending = new ArrayList<>();
        private final Set<Block> targets = new HashSet<>();
        private final BlockPos.Mutable mut = new BlockPos.Mutable();
        private int cursor, ox, oy, oz, side, lastRadius = -1;
        private String lastTarget = "";

        public BlockFinder() { super("Block Finder", "Highlights chosen ores / blocks around you", Category.WORLD); }

        private void loadTargets(String t) {
            targets.clear();
            switch (t) {
                case "Diamond" -> { targets.add(Blocks.DIAMOND_ORE); targets.add(Blocks.DEEPSLATE_DIAMOND_ORE); }
                case "Iron" -> { targets.add(Blocks.IRON_ORE); targets.add(Blocks.DEEPSLATE_IRON_ORE); }
                case "Gold" -> { targets.add(Blocks.GOLD_ORE); targets.add(Blocks.DEEPSLATE_GOLD_ORE); targets.add(Blocks.NETHER_GOLD_ORE); }
                case "Coal" -> { targets.add(Blocks.COAL_ORE); targets.add(Blocks.DEEPSLATE_COAL_ORE); }
                case "Copper" -> { targets.add(Blocks.COPPER_ORE); targets.add(Blocks.DEEPSLATE_COPPER_ORE); }
                case "Redstone" -> { targets.add(Blocks.REDSTONE_ORE); targets.add(Blocks.DEEPSLATE_REDSTONE_ORE); }
                case "Lapis" -> { targets.add(Blocks.LAPIS_ORE); targets.add(Blocks.DEEPSLATE_LAPIS_ORE); }
                case "Emerald" -> { targets.add(Blocks.EMERALD_ORE); targets.add(Blocks.DEEPSLATE_EMERALD_ORE); }
                case "Ancient Debris" -> targets.add(Blocks.ANCIENT_DEBRIS);
                default -> targets.add(Blocks.SPAWNER);
            }
        }

        @Override public void onDisable() { results = new ArrayList<>(); pending = new ArrayList<>(); cursor = 0; lastRadius = -1; }

        /** Time-sliced scan: at most 4000 block lookups per tick, so even radius 32 never causes a lag spike. */
        @Override public void onTick() {
            int r = radius.getInt();
            if (!target.get().equals(lastTarget) || r != lastRadius) {
                lastTarget = target.get(); lastRadius = r; loadTargets(lastTarget);
                results = new ArrayList<>(); pending = new ArrayList<>(); cursor = -1;
            }
            if (cursor < 0 || cursor >= side * side * side) { // (re)start a pass centred on the player
                if (cursor >= 0) results = pending;
                pending = new ArrayList<>();
                side = r * 2 + 1; cursor = 0;
                ox = mc.player.getBlockX() - r; oy = mc.player.getBlockY() - r; oz = mc.player.getBlockZ() - r;
            }
            int end = Math.min(side * side * side, cursor + 4000);
            int bottom = mc.world.getBottomY(), top = mc.world.getTopY();
            for (; cursor < end; cursor++) {
                int y = oy + (cursor / side) % side;
                if (y < bottom || y >= top) continue;
                mut.set(ox + cursor % side, y, oz + cursor / (side * side));
                if (targets.contains(mc.world.getBlockState(mut).getBlock())) pending.add(mut.toImmutable());
            }
        }

        @Override public void onHud(DrawContext c, float td) {
            int col = 0xFF000000 | color.get(), n = 0, max = maxShown.getInt();
            for (BlockPos p : results) {
                if (n++ >= max) break;
                Overlay.box3d(c, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, col, thick.getFloat());
            }
        }
    }

    // ------------------------------------------------------------------ Chest finder
    public static final class ChestFinder extends XModule {
        private final NumberSetting chunks = add(new NumberSetting("Search radius", 4, 1, 8, 1).suffix(" chunks"));
        private final BoolSetting chests = add(new BoolSetting("Chests", true));
        private final BoolSetting barrels = add(new BoolSetting("Barrels", true));
        private final BoolSetting shulkers = add(new BoolSetting("Shulker boxes", true));
        private final BoolSetting ender = add(new BoolSetting("Ender chests", true));
        private final NumberSetting thick = add(new NumberSetting("Line width", 1, 1, 3, 1));
        private final List<BlockPos> pos = new ArrayList<>();
        private final List<Integer> cols = new ArrayList<>();

        public ChestFinder() { super("Chest Finder", "Highlights storage blocks in loaded chunks", Category.WORLD); }
        @Override public void onDisable() { pos.clear(); cols.clear(); }

        @Override public void onTick() {
            if (mc.player.age % 20 != 0) return; // rescanning once a second is plenty
            pos.clear(); cols.clear();
            int r = chunks.getInt(), pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                for (int cz = pcz - r; cz <= pcz + r; cz++) {
                    WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) {
                        int col = -1;
                        if (be instanceof ChestBlockEntity) { if (chests.get()) col = 0xFFAA00; }
                        else if (be instanceof BarrelBlockEntity) { if (barrels.get()) col = 0xAA7744; }
                        else if (be instanceof ShulkerBoxBlockEntity) { if (shulkers.get()) col = 0xFF55FF; }
                        else if (be instanceof EnderChestBlockEntity) { if (ender.get()) col = 0xAA55FF; }
                        if (col >= 0) { pos.add(be.getPos()); cols.add(col); }
                    }
                }
            }
        }

        @Override public void onHud(DrawContext c, float td) {
            for (int i = 0; i < pos.size(); i++) {
                BlockPos p = pos.get(i);
                Overlay.box3d(c, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0xFF000000 | cols.get(i), thick.getFloat());
            }
        }
    }

    // ------------------------------------------------------------------ Entity counter
    public static final class EntityCounter extends TextHudModule {
        private final BoolSetting players = add(new BoolSetting("Players", true));
        private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
        private final BoolSetting passive = add(new BoolSetting("Passive mobs", true));
        private final BoolSetting items = add(new BoolSetting("Items", true));

        public EntityCounter() { super("Entity Counter", "Counts loaded entities around you", Category.WORLD, 0.4, 60); }

        @Override protected void updateLines() {
            if (mc.world == null || mc.player.age % 10 != 0) return;
            int pl = 0, ho = 0, pa = 0, it = 0;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof PlayerEntity) pl++;
                else if (e instanceof HostileEntity) ho++;
                else if (e instanceof MobEntity) pa++;
                else if (e instanceof ItemEntity) it++;
            }
            ArrayList<String> l = new ArrayList<>(4);
            if (players.get()) l.add("Players: " + pl);
            if (hostile.get()) l.add("Hostile: " + ho);
            if (passive.get()) l.add("Passive: " + pa);
            if (items.get()) l.add("Items: " + it);
            if (l.isEmpty()) l.add("-");
            lines = l.toArray(new String[0]);
        }
    }

    // ------------------------------------------------------------------ Time / weather
    public static final class TimeChanger extends XModule {
        private final ModeSetting preset = add(new ModeSetting("Preset", "Custom", "Custom", "Day", "Noon", "Sunset", "Night", "Midnight"));
        private final NumberSetting time = add(new NumberSetting("Time", 6000, 0, 24000, 100));
        public TimeChanger() { super("Time Changer", "Client-side time of day", Category.WORLD); time.visibleIf(() -> preset.is("Custom")); }
        @Override public void onTick() {
            long t = switch (preset.get()) { case "Day" -> 1000; case "Noon" -> 6000; case "Sunset" -> 12500; case "Night" -> 14000; case "Midnight" -> 18000; default -> time.getInt(); };
            mc.world.setTimeOfDay(t); // the server re-syncs every few seconds, so we re-apply each tick
        }
    }

    public static final class WeatherChanger extends XModule {
        private final ModeSetting mode = add(new ModeSetting("Weather", "Clear", "Clear", "Rain", "Thunder"));
        public WeatherChanger() { super("Weather Changer", "Client-side weather", Category.WORLD); }
        @Override public void onTick() {
            mc.world.setRainGradient(mode.is("Clear") ? 0f : 1f);
            mc.world.setThunderGradient(mode.is("Thunder") ? 1f : 0f);
        }
    }

    // ------------------------------------------------------------------ Light level
    public static final class LightLevel extends TextHudModule {
        private final BoolSetting breakdown = add(new BoolSetting("Show block/sky split", true));
        private final BoolSetting warn = add(new BoolSetting("Warn when mobs can spawn", true));
        private int block;

        public LightLevel() { super("Light Level", "Light level at your feet", Category.WORLD, 0.4, 50); }

        @Override protected void updateLines() {
            BlockPos p = mc.player.getBlockPos();
            block = mc.world.getLightLevel(LightType.BLOCK, p);
            int sky = mc.world.getLightLevel(LightType.SKY, p);
            lines = breakdown.get() ? new String[]{"Light: " + Math.max(block, sky), "Block " + block + "  Sky " + sky}
                    : new String[]{"Light: " + Math.max(block, sky)};
        }

        @Override protected int lineColor(int i) { return warn.get() && block == 0 ? 0xFF5555 : color.get(); }
    }

    // ------------------------------------------------------------------ Minimap (radar)
    public static final class Minimap extends HudModule {
        private final NumberSetting size = add(new NumberSetting("Size", 90, 60, 160, 2).suffix(" px"));
        private final NumberSetting range = add(new NumberSetting("Range", 32, 8, 96, 1).suffix(" blocks"));
        private final BoolSetting rotate = add(new BoolSetting("Rotate with view", true));
        private final BoolSetting players = add(new BoolSetting("Players", true));
        private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
        private final BoolSetting passive = add(new BoolSetting("Passive mobs", true));
        private final BoolSetting items = add(new BoolSetting("Items", false));

        public Minimap() { super("Minimap", "Radar showing entities around you (entity radar, no terrain)", Category.WORLD, 88, 2); }

        @Override public int contentWidth() { return size.getInt(); }
        @Override public int contentHeight() { return size.getInt(); }

        @Override protected void renderContent(DrawContext c, float td) {
            int s = size.getInt(), h = s / 2;
            UiUtil.roundRect(c, 0, 0, s, s, 4, argb(0x101820) & 0x00FFFFFF | ((int) (alphaF() * 160) << 24));
            c.fill(h, 1, h + 1, s - 1, 0x33FFFFFF);
            c.fill(1, h, s - 1, h + 1, 0x33FFFFFF);
            if (mc.player == null) return;
            double sc = (s / 2.0) / range.get(), yaw = Math.toRadians(mc.player.getYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                int col;
                if (e instanceof PlayerEntity) { if (!players.get()) continue; col = 0xFFFF5555; }
                else if (e instanceof HostileEntity) { if (!hostile.get()) continue; col = 0xFFFFAA00; }
                else if (e instanceof MobEntity) { if (!passive.get()) continue; col = 0xFF55FF55; }
                else if (e instanceof ItemEntity) { if (!items.get()) continue; col = 0xFF55FFFF; }
                else continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                double sx = rotate.get() ? dx * rx + dz * rz : dx, sy = rotate.get() ? -(dx * fx + dz * fz) : dz;
                int x = h + (int) (sx * sc), y = h + (int) (sy * sc);
                if (x < 2 || y < 2 || x > s - 3 || y > s - 3) continue;
                c.fill(x - 1, y - 1, x + 2, y + 2, col);
            }
            c.fill(h - 1, h - 1, h + 2, h + 2, 0xFFFFFFFF); // you
        }
    }
}
