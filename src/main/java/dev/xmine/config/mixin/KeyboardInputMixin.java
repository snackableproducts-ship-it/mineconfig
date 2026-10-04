package dev.xmine.config.mixin;

import dev.xmine.config.module.PlayerModules;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    /** Freecam: the keys move the camera, so zero the real player's movement input. */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void xmine$freecam(CallbackInfo ci) {
        if (!PlayerModules.Freecam.active) return;
        movementForward = 0; movementSideways = 0;
        pressingForward = pressingBack = pressingLeft = pressingRight = false;
        jumping = false; sneaking = false;
    }
}
