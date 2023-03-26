package me.cortex.nv.mesher;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;

public class ChunkGeometryOutputBuffer {
    public void init() {

    }

    public void destroy() {

    }

    /*
    public void addQuad(
            float x,
            float y,
            float z,
            Material material,
            BakedQuadView quad,
            int[] colors,
            float[] brightness,
            int[] lightmap)
    {
        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(brightness);
        var vertices = this.vertices;

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex];
            out.x = ctx.origin().x() + quad.getX(srcIndex) + (float) offset.getX();
            out.y = ctx.origin().y() + quad.getY(srcIndex) + (float) offset.getY();
            out.z = ctx.origin().z() + quad.getZ(srcIndex) + (float) offset.getZ();

            out.color = ColorABGR.withAlpha(colors != null ? colors[srcIndex] : 0xFFFFFFFF, brightness[srcIndex]);

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            out.light = lightmap[srcIndex];
        }
    }*/

}
