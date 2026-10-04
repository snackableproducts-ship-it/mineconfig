package dev.xmine.config.mixin;

import dev.xmine.config.module.MovementModules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    /** No Slow: pretend the player is not using an item when tickMovement applies the 0.2x slowdown. */
    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"),
            require = 0)
    private boolean xmine$noSlow(ClientPlayerEntity self) {
        return !MovementModules.NoSlow.active && self.isUsingItem();
    }
}
