package me.cortex.cullmister.mixin;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mixin(SpriteAtlasTexture.Data.class)
public class MixinSpriteAtlasTexture {
    @Inject(at=@At("RETURN"), method = "<init>")
    private void spritedata(Set<Identifier> spriteIds, int width, int height, int maxLevel, List<Sprite> sprites, CallbackInfo ci)
    {

    }
}
