package dev.xmine.config.mixin;

import dev.xmine.config.module.CombatModules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void xmine$crosshair(CallbackInfo ci) {
        if (CombatModules.Crosshair.hideVanilla) ci.cancel();
    }
}
