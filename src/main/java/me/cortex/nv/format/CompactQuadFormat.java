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
        long A = Short.toUnsignedLong(packBasePos(orx));//2
        A <<= 16;
        A |= Short.toUnsignedLong(packBasePos(ory));//2
        A <<= 16;
        A |= Short.toUnsignedLong(packBasePos(orz));//2
        A <<= 8;
        A |= Byte.toUnsignedLong(packOffsetPos(vax));//1
        A <<= 8;
        A |= Byte.toUnsignedLong(packOffsetPos(vay));//1
        long B = Byte.toUnsignedLong(packOffsetPos(vaz));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffsetPos(vbx));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffsetPos(vby));//1
        B <<=  8;
        B |= Byte.toUnsignedLong(packOffsetPos(vbz));//1

        //Do texture packing
        float minU = Math.min(Math.min(quad.getTexU(0), quad.getTexU(1)), Math.min(quad.getTexU(2), quad.getTexU(3)));
        float maxU = Math.max(Math.max(quad.getTexU(0), quad.getTexU(1)), Math.max(quad.getTexU(2), quad.getTexU(3)));

        float minV = Math.min(Math.min(quad.getTexV(0), quad.getTexV(1)), Math.min(quad.getTexV(2), quad.getTexV(3)));
        float maxV = Math.max(Math.max(quad.getTexV(0), quad.getTexV(1)), Math.max(quad.getTexV(2), quad.getTexV(3)));

        float du = maxU - minU;
        float dv = maxV - minV;


        B <<=  16;
        B |= Short.toUnsignedLong(packBaseTex(minU));
        B <<=  16;
        B |= Short.toUnsignedLong(packBaseTex(minV));

        //Pack uv delta texture
        long C = Byte.toUnsignedLong(packDeltaTex(du));
        C <<= 8;
        C |= Byte.toUnsignedLong(packDeltaTex(dv));

        //Pack colour
        C <<= 32;
        C |= Integer.toUnsignedLong(colors==null?-1:colors[0]);
        C <<= 16;

        geometry.get(quad.getNormalFace().ordinal()).push(A, B, C, 0);
    }

    private static short packBaseTex(float base) {
        return (short) (base*65536.0f);
    }

    private static byte packDeltaTex(float delta) {
        return (byte) (delta*256.0f);
    }

    private static short packBasePos(float base) {
        return (short) ((base+8.0)*(65536.0f/32.0f));
    }

    private static byte packOffsetPos(float offset) {
        return (byte) ((byte) (offset*127)+128);
    }
}
