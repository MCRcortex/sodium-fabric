package me.cortex.nv.format;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.util.math.Vec3d;

public class CompactQuadFormat {
    //32 bytes

    //8 bytes for position and size
    //byte for material params and face, rotation
    //8 bytes for texture uvs
    //4 colour/shade
    //4 light



    public static void writeAxisAligned
                            (long addr,
                             BlockRenderContext ctx,
                             Vec3d offset,
                             Material material,
                             BakedQuadView quad,
                             int[] colors,
                             float[] brightness,
                             int[] lightmap) {

    }

    public static void writeGeometry(BlockRenderContext ctx,
                                     Vec3d offset,
                                     Material material,
                                     BakedQuadView quad,
                                     int[] colors,
                                     float[] brightness,
                                     int[] lightmap,
                                     ChunkRenderBounds.Builder bounds) {

    }
}
