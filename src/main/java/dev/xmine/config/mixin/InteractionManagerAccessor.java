package dev.xmine.config.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    @Accessor("blockBreakingCooldown") int xmine$getBreakCooldown();
    @Accessor("blockBreakingCooldown") void xmine$setBreakCooldown(int v);
}
