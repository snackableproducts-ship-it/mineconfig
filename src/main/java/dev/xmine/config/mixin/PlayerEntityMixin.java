package dev.xmine.config.mixin;

import dev.xmine.config.module.MovementModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    /** Safe Walk: behave as if sneaking for the "don't fall off edges" check only. */
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true, require = 0)
    private void xmine$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (MovementModules.SafeWalk.active && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(true);
    }
}
