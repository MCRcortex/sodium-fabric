package me.cortex.cullmister.textures;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL15.glMapBuffer;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;

//The point of this is to enable bindless textures, enabling quad merging and no rot LOD generation
public class BindlessTextureManager {
    private static final HashMap<Identifier, BindlessSprite> ALL_SPRITES = new HashMap<>();
    private static final HashMap<Identifier, Atlas> ALL_ATLAS = new HashMap<>();

    public static void GeneratedTextureAtlass(Map<Identifier, Pair<SpriteAtlasTexture, SpriteAtlasTexture.Data>> atlass) {
        RenderSystem.assertOnRenderThreadOrInit();
        for(var entry : atlass.entrySet()) {
            Identifier identifier = entry.getKey();

            Atlas atlas = new Atlas(identifier);
            ALL_ATLAS.put(identifier, atlas);

            SpriteAtlasTexture atlas_ = entry.getValue().getFirst();
            SpriteAtlasTexture.Data data = entry.getValue().getSecond();
            for (Sprite sprite : data.sprites) {
                //System.err.println(sprite.getId());
                BindlessSprite sprite1 = new BindlessSprite(sprite);
                atlas.sprites.put(sprite.getId(), sprite1);
                ALL_SPRITES.put(sprite.getId(), sprite1);
            }
        }
        System.out.println(BindlessTextureManager.ALL_SPRITES.keySet().stream().sorted(Identifier::compareTo).collect(Collectors.toList()));
    }

    public static Atlas getAtlas(Identifier atlas) {
        return ALL_ATLAS.get(atlas);
    }

    public static BindlessSprite getSprite(Identifier sprite) {
        return ALL_SPRITES.get(sprite);
    }


    public static class Atlas {
        final HashMap<Identifier, BindlessSprite> sprites = new HashMap<>();
        final Identifier id;
        private BindlessBuffer spritePointerBuffer;
        private Object2ShortOpenHashMap<Identifier> sprite2index;

        public Atlas(Identifier id) {
            this.id = id;
        }

        void build() {
            spritePointerBuffer = new BindlessBuffer(sprites.size()* 8L, GL_MAP_WRITE_BIT|GL_DYNAMIC_STORAGE_BIT);
            //TODO: maybe dont do a map and just do a direct bufferdata
            long ptr = nglMapNamedBuffer(spritePointerBuffer.id, GL_WRITE_ONLY);
            sprite2index = new Object2ShortOpenHashMap<>();
            short i = 0;
            for (var is : sprites.entrySet()) {
                short id = i++;
                sprite2index.put(is.getKey(), id);
                MemoryUtil.memPutAddress(ptr+(8L*id), is.getValue().getAddress());
                System.out.println(is.getKey()+" -> "+id);
            }
            glUnmapNamedBuffer(spritePointerBuffer.id);
        }

        public BindlessBuffer getPointerBuffer() {
            if (spritePointerBuffer == null)
                build();
            return spritePointerBuffer;
        }

        public short getSpriteIndex(Identifier sprite) {
            if (spritePointerBuffer == null) {
                build();
            }
            return sprite2index.getOrDefault(sprite, (short) -1);
        }
    }


}
