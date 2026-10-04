package dev.xmine.config.mixin;

import dev.xmine.config.module.MiscModules;
import dev.xmine.config.render.Projector;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Applies Zoom and records the real world FOV so the ESP projection matches what is on screen. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void xmine$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        double fov = cir.getReturnValue();
        if (MiscModules.Zoom.factor > 1.0) { fov /= MiscModules.Zoom.factor; cir.setReturnValue(fov); }
        Projector.worldFov = fov;
    }
}
