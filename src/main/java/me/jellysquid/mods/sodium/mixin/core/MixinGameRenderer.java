package me.jellysquid.mods.sodium.mixin.core;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.nv.RenderPipeline;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void redirectClear(int mask, boolean getError) {
        if (RenderPipeline.cancleClear)
            return;
        RenderSystem.clear(mask, getError);
    }

    @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void redirectClear2(int mask, boolean getError) {
        if (RenderPipeline.cancleClear)
            return;
        RenderSystem.clear(mask, getError);
    }
}
