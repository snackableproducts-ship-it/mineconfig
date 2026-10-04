package dev.xmine.config.module;

import dev.xmine.config.core.Category;
import dev.xmine.config.core.XModule;
import dev.xmine.config.gui.UiUtil;
import dev.xmine.config.hud.*;
import dev.xmine.config.render.Overlay;
import dev.xmine.config.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;

public final class CombatModules {
    private CombatModules() {}

    // ------------------------------------------------------------------ CPS
    public static final class CpsCounter extends TextHudModule {
        private final BoolSetting left = add(new BoolSetting("Left click CPS", true));
        private final BoolSetting right = add(new BoolSetting("Right click CPS", true));
        private final ModeSetting style = add(new ModeSetting("Display style", "Inline", "Inline", "Stacked", "Compact"));
        private int lastKey = -1;

        public CpsCounter() {
            super("CPS Counter", "Left and right clicks per second", Category.COMBAT, 0.4, 7);
            alsoIn(Category.HUD);
            enabledByDefault();
        }

        @Override protected void beforeRender() { ClickTracker.poll(mc); updateLines(); }

        @Override protected void updateLines() {
            int l = ClickTracker.leftCps(), r = ClickTracker.rightCps();
            int key = l + r * 100 + style.index() * 10000 + (left.get() ? 100000 : 0) + (right.get() ? 200000 : 0);
            if (key == lastKey) return;
            lastKey = key;
            boolean sl = left.get(), sr = right.get();
            if (!sl && !sr) sl = true;
            String both = l + " | " + r, one = String.valueOf(sl ? l : r);
            switch (style.get()) {
                case "Stacked" -> lines = sl && sr ? new String[]{"Left: " + l, "Right: " + r}
                        : new String[]{(sl ? "Left: " : "Right: ") + one};
                case "Compact" -> lines = new String[]{sl && sr ? both : one};
                default -> lines = new String[]{"CPS: " + (sl && sr ? both : one)};
            }
        }
    }

    // ------------------------------------------------------------------ Hitboxes
    public static final class HitboxDisplay extends XModule {
        public HitboxDisplay() { super("Hitbox Display", "Shows entity hitboxes (same as F3+B)", Category.COMBAT); }
        @Override public void onEnable() { if (mc.getEntityRenderDispatcher() != null) mc.getEntityRenderDispatcher().setRenderHitboxes(true); }
        @Override public void onDisable() { if (mc.getEntityRenderDispatcher() != null) mc.getEntityRenderDispatcher().setRenderHitboxes(false); }
        @Override public void onTick() { mc.getEntityRenderDispatcher().setRenderHitboxes(true); }
    }

    // ------------------------------------------------------------------ Crosshair
    public static final class Crosshair extends XModule {
        /** Read by InGameHudMixin to cancel the vanilla crosshair. */
        public static boolean hideVanilla;
        private final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Dot", "Circle"));
        private final NumberSetting size = add(new NumberSetting("Size", 5, 1, 20, 1));
        private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 10, 1));
        private final NumberSetting thick = add(new NumberSetting("Thickness", 1, 1, 5, 1));
        private final NumberSetting opacity = add(new NumberSetting("Opacity", 100, 10, 100, 1).suffix("%"));
        private final ColorSetting color = add(new ColorSetting("Color", 0x55FF55));
        private final BoolSetting outline = add(new BoolSetting("Outline", true));
        private final BoolSetting hide = add(new BoolSetting("Hide vanilla crosshair", true));

        public Crosshair() {
            super("Crosshair", "Custom crosshair: style, size, gap, colour (Custom Crosshair)", Category.COMBAT);
            alsoIn(Category.RENDER);
        }

        @Override public void onEnable() { hideVanilla = hide.get(); }
        @Override public void onDisable() { hideVanilla = false; }
        @Override public void onTick() { hideVanilla = hide.get(); }

        @Override public void onHud(DrawContext c, float td) {
            if (mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson()) return;
            int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
            int t = thick.getInt(), s = size.getInt(), g = gap.getInt();
            int col = (Math.round(opacity.getFloat() * 2.55f) << 24) | color.get();
            switch (style.get()) {
                case "Dot" -> rect(c, cx - t, cy - t, cx + t + 1, cy + t + 1, col);
                case "Circle" -> {
                    int r = s + g;
                    for (int i = 0; i < 32; i++) {
                        double a = i * Math.PI / 16;
                        int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                        rect(c, x, y, x + t, y + t, col);
                    }
                }
                default -> {
                    int hy = cy - t / 2, hx = cx - t / 2;
                    rect(c, cx - g - s, hy, cx - g, hy + t, col);
                    rect(c, cx + g + 1, hy, cx + g + 1 + s, hy + t, col);
                    rect(c, hx, cy - g - s, hx + t, cy - g, col);
                    rect(c, hx, cy + g + 1, hx + t, cy + g + 1 + s, col);
                }
            }
        }

        private void rect(DrawContext c, int x1, int y1, int x2, int y2, int col) {
            if (outline.get()) c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xB0000000);
            c.fill(x1, y1, x2, y2, col);
        }
    }

    // ------------------------------------------------------------------ Aim assist
    public static final class AimAssist extends XModule {
        private final NumberSetting range = add(new NumberSetting("Range", 4.5, 1, 6, 0.1));
        private final NumberSetting fov = add(new NumberSetting("FOV", 90, 10, 180, 1).suffix("\u00b0"));
        private final NumberSetting speed = add(new NumberSetting("Max speed", 6, 1, 30, 0.5).suffix("\u00b0/tick"));
        private final BoolSetting players = add(new BoolSetting("Players", true));
        private final BoolSetting mobs = add(new BoolSetting("Mobs", true));
        private final BoolSetting needClick = add(new BoolSetting("Only while attacking", true));

        public AimAssist() { super("Aim Assist", "Gently rotates your view towards nearby targets (will be flagged on most servers)", Category.COMBAT); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.currentScreen != null) return;
            if (needClick.get() && !mc.options.attackKey.isPressed()) return;
            double r2 = range.get() * range.get(), bestAng = fov.get() / 2.0;
            float yaw = p.getYaw(), pitch = p.getPitch(), bdy = 0, bdp = 0;
            boolean found = false;
            for (Entity e : mc.world.getEntities()) {
                if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                if (e instanceof PlayerEntity ? !players.get() : !mobs.get()) continue;
                if (p.squaredDistanceTo(e) > r2) continue;
                double dx = e.getX() - p.getX(), dy = e.getBodyY(0.65) - p.getEyeY(), dz = e.getZ() - p.getZ();
                float ty = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                float dyaw = MathHelper.wrapDegrees(ty - yaw), dp = tp - pitch;
                double ang = Math.hypot(dyaw, dp);
                if (ang < bestAng) { bestAng = ang; bdy = dyaw; bdp = dp; found = true; }
            }
            if (!found) return;
            float max = speed.getFloat();
            p.setYaw(yaw + MathHelper.clamp(bdy * 0.35f, -max, max));
            p.setPitch(MathHelper.clamp(pitch + MathHelper.clamp(bdp * 0.35f, -max, max), -90f, 90f));
        }
    }

    // ------------------------------------------------------------------ Target HUD
    public static final class TargetHud extends HudModule {
        private final NumberSetting hold = add(new NumberSetting("Keep after losing target", 3, 0, 10, 0.5).suffix("s"));
        private final BoolSetting armor = add(new BoolSetting("Show armor value", true));
        private LivingEntity target;
        private int lastSeen;

        public TargetHud() {
            super("Target HUD", "Name and health of the entity you are looking at", Category.COMBAT, 40, 52);
            alsoIn(Category.HUD);
        }

        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.targetedEntity instanceof LivingEntity le && le != mc.player) { target = le; lastSeen = mc.player.age; }
            else if (target != null && (mc.player.age - lastSeen > hold.get() * 20 || !target.isAlive())) target = null;
        }

        private boolean preview() { return target == null && HudManager.isEditing(); }
        @Override public int contentWidth() { return 110; }
        @Override public int contentHeight() { return armor.get() ? 30 : 21; }

        @Override protected void renderContent(DrawContext c, float td) {
            if (target == null && !preview()) return;
            String name = preview() ? "Steve" : target.getDisplayName().getString();
            float hp = preview() ? 14f : target.getHealth(), max = preview() ? 20f : target.getMaxHealth();
            float f = MathHelper.clamp(hp / Math.max(1f, max), 0f, 1f);
            text(c, UiUtil.trim(name, 108), 0, 0, color.get());
            UiUtil.roundRect(c, 0, 11, 110, 6, 3, argb(0x333333));
            UiUtil.roundRect(c, 0, 11, Math.max(3, (int) (110 * f)), 6, 3,
                    argb(MathHelper.hsvToRgb(f * 0.33f, 0.8f, 1f)));
            if (armor.get()) text(c, fmt(hp, 1) + " / " + fmt(max, 0) + "   Armor " + (preview() ? 12 : target.getArmor()), 0, 20, 0xAAAAAA);
        }
    }

    // ------------------------------------------------------------------ Armor HUD
    public static final class ArmorHud extends HudModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private static ItemStack[] sample;
        private final BoolSetting icons = add(new BoolSetting("Armor icons", true));
        private final ModeSetting durability = add(new ModeSetting("Durability", "Percent", "Off", "Percent", "Value"));
        private final BoolSetting names = add(new BoolSetting("Item names", false));
        private final ModeSetting layout = add(new ModeSetting("Layout", "Vertical", "Vertical", "Horizontal"));

        public ArmorHud() {
            super("Armor HUD", "Equipped armor with durability", Category.COMBAT, 85, 60);
            alsoIn(Category.HUD);
        }

        private ItemStack stack(int i) {
            if (HudManager.isEditing()) {
                if (sample == null) sample = new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                        new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
                return sample[i];
            }
            return mc.player == null ? ItemStack.EMPTY : mc.player.getEquippedStack(SLOTS[i]);
        }

        private String info(ItemStack s) {
            StringBuilder sb = new StringBuilder();
            if (names.get()) sb.append(s.getName().getString());
            int max = s.getMaxDamage();
            if (max > 0 && !durability.is("Off")) {
                if (sb.length() > 0) sb.append(' ');
                int left = max - s.getDamage();
                sb.append(durability.is("Percent") ? (left * 100 / max) + "%" : left + "/" + max);
            }
            return sb.toString();
        }

        private int count() { int n = 0; for (int i = 0; i < 4; i++) if (!stack(i).isEmpty()) n++; return n; }

        @Override public int contentWidth() {
            boolean v = layout.is("Vertical");
            int w = 0, n = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack s = stack(i);
                if (s.isEmpty()) continue;
                int iw = (icons.get() ? 18 : 0) + tw(info(s));
                w = v ? Math.max(w, iw) : w + iw + 4;
                n++;
            }
            return Math.max(n == 0 ? 16 : w, 16);
        }

        @Override public int contentHeight() { return layout.is("Vertical") ? Math.max(16, count() * 18 - 2) : 16; }

        @Override protected void renderContent(DrawContext c, float td) {
            boolean v = layout.is("Vertical");
            int x = 0, y = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack s = stack(i);
                if (s.isEmpty()) continue;
                int w = 0;
                if (icons.get()) {
                    c.drawItem(s, x, y);
                    if (s.isItemBarVisible()) {
                        c.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
                        c.fill(x + 2, y + 13, x + 2 + s.getItemBarStep(), y + 14, 0xFF000000 | s.getItemBarColor());
                    }
                    w = 18;
                }
                String t = info(s);
                if (!t.isEmpty()) {
                    int col = s.getMaxDamage() > 0 ? s.getItemBarColor() : color.get();
                    text(c, t, x + w, y + 4, col);
                    w += tw(t);
                }
                if (v) y += 18; else x += w + 4;
            }
        }
    }

    // ------------------------------------------------------------------ Totem counter
    public static final class TotemCounter extends HudModule {
        private final BoolSetting icon = add(new BoolSetting("Show icon", true));
        private final BoolSetting zero = add(new BoolSetting("Show when zero", true));
        private int count = 0;

        public TotemCounter() {
            super("Totem Counter", "Totems of Undying in your inventory", Category.COMBAT, 85, 50);
        }

        @Override public void onTick() {
            if (mc.player == null || mc.player.age % 5 != 0) return; // cached; no need to scan every tick
            int n = 0;
            for (ItemStack s : mc.player.getInventory().main) if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) n += mc.player.getOffHandStack().getCount();
            count = n;
        }

        private boolean hidden() { return count == 0 && !zero.get() && !HudManager.isEditing(); }
        private String label() { return "x" + count; }
        @Override public int contentWidth() { return hidden() ? 1 : (icon.get() ? 18 : 0) + tw(label()); }
        @Override public int contentHeight() { return hidden() ? 1 : 16; }

        @Override protected void renderContent(DrawContext c, float td) {
            if (hidden()) return;
            int x = 0;
            if (icon.get()) { c.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0); x = 18; }
            text(c, label(), x, 4, count <= 1 ? 0xFF5555 : color.get());
        }
    }

    // ------------------------------------------------------------------ Potion effects
    public static final class PotionEffects extends HudModule {
        private final BoolSetting duration = add(new BoolSetting("Show duration", true));
        private final BoolSetting level = add(new BoolSetting("Show level", true));
        private final ArrayList<String> rows = new ArrayList<>();
        private final ArrayList<Integer> cols = new ArrayList<>();

        public PotionEffects() {
            super("Potion Effects", "Active status effects with time left (Potion Status)", Category.COMBAT, 0.4, 40);
            alsoIn(Category.HUD);
        }

        @Override public void onTick() {
            rows.clear(); cols.clear();
            if (mc.player == null) return;
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                StringBuilder sb = new StringBuilder(e.getEffectType().value().getName().getString());
                if (level.get()) sb.append(' ').append(e.getAmplifier() + 1);
                if (duration.get()) {
                    sb.append("  ");
                    if (e.isInfinite()) sb.append("inf");
                    else { int s = e.getDuration() / 20; sb.append(s / 60).append(':').append(s % 60 < 10 ? "0" : "").append(s % 60); }
                }
                rows.add(sb.toString());
                cols.add(e.getEffectType().value().isBeneficial() ? 0x55FF55 : 0xFF5555);
            }
        }

        private boolean preview() { return rows.isEmpty() && HudManager.isEditing(); }
        @Override public int contentWidth() {
            if (preview()) return tw("Speed 2  0:45");
            int w = 0; for (String r : rows) w = Math.max(w, tw(r)); return Math.max(1, w);
        }
        @Override public int contentHeight() { return preview() ? 9 : Math.max(1, rows.size() * 10 - 1); }
        @Override protected void renderContent(DrawContext c, float td) {
            if (preview()) { text(c, "Speed 2  0:45", 0, 0, 0x55FF55); return; }
            for (int i = 0; i < rows.size(); i++) text(c, rows.get(i), 0, i * 10, cols.get(i));
        }
    }

    // ------------------------------------------------------------------ Attack indicator
    public static final class AttackIndicator extends HudModule {
        private final ModeSetting mode = add(new ModeSetting("Display style", "Bar", "Bar", "Percent", "Both"));
        private final BoolSetting hideFull = add(new BoolSetting("Hide when ready", false));
        private float prog = 1f;

        public AttackIndicator() { super("Attack Indicator", "Attack cooldown meter", Category.COMBAT, 45, 56); }

        @Override public void onTick() { if (mc.player != null) prog = mc.player.getAttackCooldownProgress(0.5f); }
        private boolean hidden() { return hideFull.get() && prog >= 0.999f && !HudManager.isEditing(); }
        @Override public int contentWidth() { return hidden() ? 1 : 60; }
        @Override public int contentHeight() { return hidden() ? 1 : (mode.is("Bar") ? 5 : mode.is("Percent") ? 9 : 16); }

        @Override protected void renderContent(DrawContext c, float td) {
            if (hidden()) return;
            int y = 0;
            if (!mode.is("Bar")) { UiUtil.centerText(c, Math.round(prog * 100) + "%", 30, 0, argb(color.get())); y = mode.is("Both") ? 11 : 0; }
            if (!mode.is("Percent")) {
                UiUtil.roundRect(c, 0, y, 60, 5, 2, argb(0x333333));
                UiUtil.roundRect(c, 0, y, Math.max(2, (int) (60 * prog)), 5, 2, argb(prog >= 0.999f ? 0x55FF55 : color.get()));
            }
        }
    }
}
