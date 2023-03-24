package me.cortex.nv.mesher;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChunkMesher {
    //2 parts, meta quad 16 bytes, inner quad 8 bytes
    // Meta Quad:
    //      Quad position 3x2 shorts
    //      Inner quad offset 1 short
    //
    // Inner Quad:
    //      Defered texture, 1 short
    //

    private static final class DirectionalMerger {
        final ModelQuadFacing direction;
        final int axis;
        final Plane2DMerger[] alignedPlaneMerger = new Plane2DMerger[16];
        public DirectionalMerger(ModelQuadFacing direction) {
            this.direction = direction;
            axis = direction.ordinal()>>1;
        }

        int inputQuadCount;
        void addQuad(float x, float y, float z, Material material, BakedQuadView quad, int[] colors, float[] br, int[] lm) {
            if (quad.getFlags()>>3 == 3) {//
                float axisValue = axis==0?y:(axis==1?z:x);
                if (Math.abs(axisValue-(int)axisValue)<0.0001f) {
                    int axisv = (int)axisValue;
                    Plane2DMerger merger = alignedPlaneMerger[axisv];
                    if (merger == null) merger = alignedPlaneMerger[axisv] = new Plane2DMerger();
                    int axisA = (int)(axis==0?x:(axis==1?x:y));
                    int axisB = (int)(axis==0?z:(axis==1?y:z));

                    merger.setQuad(axisA, axisB);
                    inputQuadCount++;
                } else {
                    //TODO: this, its not block aligned
                }
            } else {
                //TODO: THIS, it is not a full quad merging opertunity
                int q = 0;
            }
        }

        float mesh() {
            float ratio = 0;
            for (var plane : alignedPlaneMerger) {
                if (plane == null) continue;
                ratio += plane.merge(a->{}, b->{});
            }
            return ratio;
        }
    }

    private final DirectionalMerger[] faces = new DirectionalMerger[6];

    int totalInputQuads = 0;
    public void quad(BlockRenderContext ctx, Vec3d offset, Material material, BakedQuadView quad, int[] colors, float[] br, int[] lm) {
        float x = ctx.origin().x() + (float) offset.getX();
        float y = ctx.origin().y() + (float) offset.getY();
        float z = ctx.origin().z() + (float) offset.getZ();
        totalInputQuads++;
        if (quad.getFlags()>>3 != 0  && quad.getNormalFace() != ModelQuadFacing.UNASSIGNED) {//Cannot merge if it doesnt have any flags or is unassigned
            var holder = faces[quad.getNormalFace().ordinal()];
            if (holder == null) holder = faces[quad.getNormalFace().ordinal()] = new DirectionalMerger(quad.getNormalFace());
            holder.addQuad(x,y,z, material, quad, colors, br, lm);
        }
    }


    volatile static float ctiq;
    volatile static float ctic;
    volatile static float cratio;
    volatile static float cc;
    public void mesh() {
        float ratio = 0;
        float inCount = 0;
        for (var face : faces) {
            if (face == null) continue;
            var r = face.mesh();
            ratio += r;
            inCount += face.inputQuadCount;
        }
        stats(totalInputQuads, inCount, ratio);
    }
    private static synchronized void stats(float a, float b, float c) {
        cc++;
        ctiq += a;
        ctic += b;
        cratio += c;
        //System.out.println("Tin: "+ totalInputQuads+" Mergable: "+ inCount + " merged out: " + ratio + " ratio: "+ (ratio/totalInputQuads));
        System.out.println("CTin: "+ctiq + " CTout: " + (ctiq-ctic+cratio) + " ratio: "+ ((ctiq-ctic+cratio)/ctiq) +" or:" + (cratio/ctic));
    }
}
