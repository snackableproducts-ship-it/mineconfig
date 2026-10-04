package dev.xmine.config.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown") int xmine$getItemUseCooldown();
    @Accessor("itemUseCooldown") void xmine$setItemUseCooldown(int v);
}
