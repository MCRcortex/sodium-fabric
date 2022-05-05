package me.cortex.cullmister.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import me.cortex.cullmister.textures.BindlessTextureManager;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ModelLoader.class)
public class MixinModelLoader {
    @Shadow
    @Final
    private Map<Identifier, Pair<SpriteAtlasTexture, SpriteAtlasTexture.Data>> spriteAtlasData;

    @Inject(at=@At("RETURN"), method = "<init>")
    private void spritedata(ResourceManager resourceManager, BlockColors blockColors, Profiler profiler, int i, CallbackInfo ci)
    {
        RenderSystem.recordRenderCall(()-> {
            BindlessTextureManager.GeneratedTextureAtlass(spriteAtlasData);
            BindlessTextureManager.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getPointerBuffer();
        });
    }
}
