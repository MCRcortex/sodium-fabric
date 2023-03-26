package me.cortex.nv.mesher;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChunkMesher {
    private final ChunkGeometryOutputBuffer buffer;
    public ChunkMesher(ChunkGeometryOutputBuffer gob) {
        buffer = gob;
    }
    //2 parts, meta quad 16 bytes, inner quad 8 bytes
    // Meta Quad:
    //      Quad position 3x2 shorts
    //      Inner quad offset 1 short
    //
    // Inner Quad:
    //      Defered texture, 1 short
    //

    private static final class DirectionalMerger {
        private record FullFacedQuad(Material material, BakedQuadView quadView, int[] colors, float[] br, int[] lm) {
            public FullFacedQuad(Material material, BakedQuadView quadView, int[] colors, float[] br, int[] lm) {
                this.material = material;
                this.quadView = quadView;
                if (colors != null) {
                    this.colors = new int[4];
                } else {
                    this.colors = null;
                }
                this.br = new float[4];
                this.lm = new int[4];
                for (int i = 0; i < 4; i++) {
                    if (colors != null) {
                        this.colors[i] = colors[i];
                    }
                    this.br[i] = br[i];
                    this.lm[i] = lm[i];
                }
            }
        }

        final ModelQuadFacing direction;
        final int axis;
        final Plane2DMerger<FullFacedQuad>[] alignedPlaneMerger = new Plane2DMerger[16];
        public DirectionalMerger(ModelQuadFacing direction) {
            this.direction = direction;
            axis = direction.ordinal()>>1;
        }

        void addQuad(float x, float y, float z, Material material, BakedQuadView quad, int[] colors, float[] br, int[] lm) {
            if (quad.getFlags()>>3 == 3) {//
                float axisValue = axis==0?y:(axis==1?z:x);
                if (Math.abs(axisValue-(int)axisValue)<0.0001f) {
                    int axisv = (int)axisValue;
                    Plane2DMerger<FullFacedQuad> merger = alignedPlaneMerger[axisv];
                    if (merger == null) merger = alignedPlaneMerger[axisv] = new Plane2DMerger(FullFacedQuad.class);
                    int axisA = (int)(axis==0?x:(axis==1?x:y));
                    int axisB = (int)(axis==0?z:(axis==1?y:z));

                    merger.setQuad(axisA, axisB, new FullFacedQuad(material, quad, colors, br, lm));
                } else {
                    //TODO: this, its not block aligned

                }
            } else {
                //TODO: THIS, it is not a full quad merging opertunity

                float axisValue = axis==0?y:(axis==1?z:x);
                if (Math.abs(axisValue-(int)axisValue)<0.0001f) {

                } else {

                }
            }
        }

        void mesh() {
            for (var plane : alignedPlaneMerger) {
                if (plane == null) continue;
                plane.merge(a->{

                }, b->{

                });
            }
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

    public void mesh() {
        for (var face : faces) {
            if (face == null) continue;
            face.mesh();
        }
    }
}
