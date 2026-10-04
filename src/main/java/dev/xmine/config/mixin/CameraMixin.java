package dev.xmine.config.mixin;

import dev.xmine.config.module.PlayerModules.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void xmine$freecam(CallbackInfo ci) {
        if (!Freecam.active) return;
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        setPos(Freecam.cx(td), Freecam.cy(td), Freecam.cz(td));
    }
}
