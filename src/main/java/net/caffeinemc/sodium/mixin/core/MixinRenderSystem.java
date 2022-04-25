package net.caffeinemc.sodium.mixin.core;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.CoreRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
    @Inject(method = "flipFrame", at = @At("RETURN"))
    private static void finishSyncCallback(long window, CallbackInfo ci) {
        CoreRenderer.OnFlipCallback();
    }
}
