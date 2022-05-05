package me.cortex.cullmister.textureManager;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

//The point of this is to enable bindless textures, enabling quad merging and no rot LOD generation
public class BindlessTextureManager {
    private static final HashMap<Identifier, BindlessSprite> ALL_SPRITES = new HashMap<>();
    private static final HashMap<Identifier, HashMap<Identifier, BindlessSprite>> ALL_ATLAS = new HashMap<>();
    public static void GeneratedTextureAtlass(Map<Identifier, Pair<SpriteAtlasTexture, SpriteAtlasTexture.Data>> atlass) {
        RenderSystem.assertOnRenderThreadOrInit();
        for(var entry : atlass.entrySet()) {
            Identifier identifier = entry.getKey();

            var atlas = new HashMap<Identifier, BindlessSprite>();
            ALL_ATLAS.put(identifier, atlas);

            SpriteAtlasTexture atlas_ = entry.getValue().getFirst();
            SpriteAtlasTexture.Data data = entry.getValue().getSecond();
            for (Sprite sprite : data.sprites) {
                //System.err.println(sprite.getId());
                BindlessSprite sprite1 = new BindlessSprite(sprite);
                atlas.put(sprite.getId(), sprite1);
                ALL_SPRITES.put(sprite.getId(), sprite1);
            }
        }
        System.out.println(BindlessTextureManager.ALL_SPRITES.keySet().stream().sorted(Identifier::compareTo).collect(Collectors.toList()));
    }

    public static Map<Identifier, BindlessSprite> getAtlas(Identifier atlas) {
        return ALL_ATLAS.get(atlas);
    }

    public static BindlessSprite getSprite(Identifier atlas) {
        return ALL_SPRITES.get(atlas);
    }
}
