package me.cortex.nv.format;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
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
                                     ChunkQuadGeometryBuffer geometry) {

        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(brightness);

        float bx = (float) (ctx.origin().x() + offset.getX());
        float by = (float) (ctx.origin().y() + offset.getX());
        float bz = (float) (ctx.origin().z() + offset.getX());

        //Rotate the quad so that vertex 0 is the min of x,y,z


        //Position data is packed into 12 bytes
        // 6 for origin, 3 for vecA 3 for vecB
        // vecC is vecA+vecB
        //4 for base uv, 2 for offset u, v


        float orx =  quad.getX(0);
        float ory =  quad.getY(0);
        float orz =  quad.getZ(0);
        float vax =  quad.getX(1) - orx;
        float vay =  quad.getY(1) - ory;
        float vaz =  quad.getZ(1) - orz;
        float vbx =  quad.getX(3) - orx;
        float vby =  quad.getY(3) - ory;
        float vbz =  quad.getZ(3) - orz;
        orx += bx;
        ory += by;
        orz += bz;

        //Pack position
        long A = Short.toUnsignedLong(packBase(orx));//2
        A <<= 16;
        A |= Short.toUnsignedLong(packBase(ory));//2
        A <<= 16;
        A |= Short.toUnsignedLong(packBase(orz));//2
        A <<= 8;
        A |= Byte.toUnsignedLong(packOffset(vax));//1
        A <<= 8;
        A |= Byte.toUnsignedLong(packOffset(vay));//1
        long B = Byte.toUnsignedLong(packOffset(vaz));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffset(vbx));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffset(vby));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffset(vbz));//1
        B <<=  32;

        //Pack texture.... later

        geometry.get(quad.getNormalFace().ordinal()).push(A, B, 0, 0);
    }

    private static short packBase(float base) {
        return (short) ((base+8.0)*(65536.0f/32.0f));
    }

    private static byte packOffset(float offset) {
        return (byte) Math.round((offset+1)*(255.0/2.0));
    }
}
