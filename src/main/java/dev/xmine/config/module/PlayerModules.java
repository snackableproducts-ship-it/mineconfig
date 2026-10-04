package dev.xmine.config.module;

import dev.xmine.config.core.Category;
import dev.xmine.config.core.XModule;
import dev.xmine.config.mixin.InteractionManagerAccessor;
import dev.xmine.config.mixin.MinecraftClientAccessor;
import dev.xmine.config.setting.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class PlayerModules {
    private PlayerModules() {}

    // ------------------------------------------------------------------ Auto eat
    public static final class AutoEat extends XModule {
        private final NumberSetting threshold = add(new NumberSetting("Eat below hunger", 14, 1, 19, 1));
        private final BoolSetting special = add(new BoolSetting("Allow golden apples etc.", false));
        private int prevSlot = -1;
        private boolean eating;

        public AutoEat() { super("Auto Eat", "Eats food from your hotbar when hungry", Category.PLAYER); }

        private boolean good(ItemStack s) {
            FoodComponent f = s.get(DataComponentTypes.FOOD);
            if (f == null) return false;
            if (s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO)
                    || s.isOf(Items.PUFFERFISH) || s.isOf(Items.CHICKEN)) return false;
            return special.get() || !f.canAlwaysEat();
        }

        private void stop() {
            mc.options.useKey.setPressed(false);
            if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().selectedSlot = prevSlot;
            prevSlot = -1;
            eating = false;
        }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            PlayerInventory inv = p.getInventory();
            if (mc.currentScreen != null) { if (eating) stop(); return; }
            int food = p.getHungerManager().getFoodLevel();
            if (eating) {
                if (food >= 20 || !good(inv.getMainHandStack())) stop();
                return;
            }
            if (food > threshold.get()) return;
            int best = -1; float bestSat = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getStack(i);
                if (!good(s)) continue;
                float sat = s.get(DataComponentTypes.FOOD).saturation();
                if (sat > bestSat) { bestSat = sat; best = i; }
            }
            if (best < 0) return;
            prevSlot = inv.selectedSlot;
            inv.selectedSlot = best;
            mc.options.useKey.setPressed(true);
            eating = true;
        }

        @Override public void onDisable() { if (eating) stop(); }
    }

    // ------------------------------------------------------------------ Auto tool
    public static final class AutoTool extends XModule {
        private final BoolSetting restore = add(new BoolSetting("Switch back afterwards", true));
        private int prev = -1;
        public AutoTool() { super("Auto Tool", "Selects the fastest hotbar tool for the block you mine", Category.PLAYER); }

        @Override public void onTick() {
            PlayerInventory inv = mc.player.getInventory();
            if (mc.currentScreen == null && mc.options.attackKey.isPressed()
                    && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockState st = mc.world.getBlockState(hit.getBlockPos());
                int best = inv.selectedSlot;
                float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(st);
                for (int i = 0; i < 9; i++) {
                    float sp = inv.getStack(i).getMiningSpeedMultiplier(st);
                    if (sp > bestSpeed * 1.01f) { bestSpeed = sp; best = i; }
                }
                if (best != inv.selectedSlot) { if (prev < 0) prev = inv.selectedSlot; inv.selectedSlot = best; }
            } else if (prev >= 0) {
                if (restore.get()) inv.selectedSlot = prev;
                prev = -1;
            }
        }
    }

    // ------------------------------------------------------------------ Auto armor
    public static final class AutoArmor extends XModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private final NumberSetting delay = add(new NumberSetting("Delay", 4, 1, 20, 1).suffix(" ticks"));
        private final BoolSetting upgrade = add(new BoolSetting("Replace worn armor with better", true));
        private int timer;

        public AutoArmor() { super("Auto Armor", "Equips the best armor from your inventory", Category.PLAYER); }

        private static int prot(ItemStack s) { return s.getItem() instanceof ArmorItem a ? a.getProtection() : -1; }
        /** Inventory index (0-8 hotbar, 9-35 main) -> PlayerScreenHandler slot id. */
        private static int handlerSlot(int invIndex) { return invIndex < 9 ? 36 + invIndex : invIndex; }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (--timer > 0 || mc.interactionManager == null || p.currentScreenHandler != p.playerScreenHandler) return;
            for (int i = 0; i < 4; i++) {
                EquipmentSlot slot = SLOTS[i];
                ItemStack worn = p.getEquippedStack(slot);
                int bestIdx = -1, bestProt = worn.isEmpty() ? 0 : prot(worn);
                for (int j = 0; j < 36; j++) {
                    ItemStack s = p.getInventory().getStack(j);
                    if (s.getItem() instanceof ArmorItem a && a.getType().getEquipmentSlot() == slot && a.getProtection() > bestProt) {
                        bestProt = a.getProtection(); bestIdx = j;
                    }
                }
                if (bestIdx < 0) continue;
                if (worn.isEmpty()) {
                    mc.interactionManager.clickSlot(0, handlerSlot(bestIdx), 0, SlotActionType.QUICK_MOVE, p); // shift-click equips
                    timer = delay.getInt();
                    return;
                } else if (upgrade.get()) {
                    mc.interactionManager.clickSlot(0, 5 + i, 0, SlotActionType.QUICK_MOVE, p); // take off; next cycle equips the better one
                    timer = delay.getInt();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Item swap
    public static final class ItemSwap extends XModule {
        public ItemSwap() {
            super("Item Swap", "While ON, its keybind swaps main hand and off hand (bind a key in Customize)", Category.PLAYER);
            add(new ActionSetting("Swap now", "Swap hands", null, this::swap));
        }
        private void swap() {
            if (mc.getNetworkHandler() == null) return;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
        @Override public void onKeyPress() { if (isEnabled()) swap(); else toggle(); }
    }

    // ------------------------------------------------------------------ Fast place / break / no fall
    public static final class FastPlace extends XModule {
        private final NumberSetting delay = add(new NumberSetting("Delay", 0, 0, 4, 1).suffix(" ticks"));
        public FastPlace() { super("Fast Place", "Removes the delay between placing blocks / using items", Category.PLAYER); }
        @Override public void onTick() {
            MinecraftClientAccessor a = (MinecraftClientAccessor) mc;
            if (a.xmine$getItemUseCooldown() > delay.getInt()) a.xmine$setItemUseCooldown(delay.getInt());
        }
    }

    public static final class FastBreak extends XModule {
        private final NumberSetting delay = add(new NumberSetting("Delay", 0, 0, 5, 1).suffix(" ticks"));
        public FastBreak() { super("Fast Break", "Removes the delay between breaking blocks", Category.PLAYER); }
        @Override public void onTick() {
            if (mc.interactionManager == null) return;
            InteractionManagerAccessor a = (InteractionManagerAccessor) mc.interactionManager;
            if (a.xmine$getBreakCooldown() > delay.getInt()) a.xmine$setBreakCooldown(delay.getInt());
        }
    }

    public static final class NoFall extends XModule {
        private final NumberSetting dist = add(new NumberSetting("Trigger distance", 2.5, 1.5, 10, 0.5).suffix(" blocks"));
        public NoFall() { super("No Fall", "Cancels fall damage (flagged on most servers)", Category.PLAYER); }
        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (p.fallDistance > dist.getFloat() && !p.isOnGround() && !p.isFallFlying() && mc.getNetworkHandler() != null)
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    // ------------------------------------------------------------------ Freecam
    public static final class Freecam extends XModule {
        /** Read by CameraMixin / KeyboardInputMixin. */
        public static boolean active;
        private static double x, y, z, px, py, pz;
        private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1).suffix(" b/t"));

        public Freecam() { super("Freecam", "Detach the camera and fly around while your body stays put", Category.PLAYER); }

        public static double cx(float td) { return px + (x - px) * td; }
        public static double cy(float td) { return py + (y - py) * td; }
        public static double cz(float td) { return pz + (z - pz) * td; }

        @Override public void onEnable() {
            if (mc.gameRenderer == null || mc.player == null) { setEnabled(false); return; }
            var pos = mc.gameRenderer.getCamera().getPos();
            x = px = pos.x; y = py = pos.y; z = pz = pos.z;
            active = true;
        }
        @Override public void onDisable() { active = false; }

        @Override public void onTick() {
            px = x; py = y; pz = z;
            if (mc.currentScreen != null) return;
            double f = 0, s = 0, u = 0;
            if (mc.options.forwardKey.isPressed()) f++;
            if (mc.options.backKey.isPressed()) f--;
            if (mc.options.leftKey.isPressed()) s++;
            if (mc.options.rightKey.isPressed()) s--;
            if (mc.options.jumpKey.isPressed()) u++;
            if (mc.options.sneakKey.isPressed()) u--;
            double len = Math.sqrt(f * f + s * s);
            if (len > 0) { f /= len; s /= len; }
            double yaw = Math.toRadians(mc.player.getYaw()), sin = Math.sin(yaw), cos = Math.cos(yaw), sp = speed.get();
            x += (s * cos - f * sin) * sp;
            z += (f * cos + s * sin) * sp;
            y += u * sp;
        }
    }
}
