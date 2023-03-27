package me.cortex.nv.mesher;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
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
        private record FullFacedQuad(float x, float y, float z, Material material, BakedQuadView quadView, int[] colors, float[] br, int[] lm) {
            public FullFacedQuad(float x, float y, float z, Material material, BakedQuadView quadView, int[] colors, float[] br, int[] lm) {
                this.x = x;
                this.y = y;
                this.z = z;
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
        final Plane2DMerger<FullFacedQuad>[] alignedPlaneMerger = new Plane2DMerger[17];
        public DirectionalMerger(ModelQuadFacing direction) {
            this.direction = direction;
            axis = direction.ordinal()>>1;
        }

        void addQuad(float x, float y, float z, Material material, BakedQuadView quad, int[] colors, float[] br, int[] lm) {
            if (quad.getFlags()>>3 == 3) {//
                float axisValue = (axis==0?y:(axis==1?x:z)) + (axis==0?quad.getY(0):(axis==1?quad.getX(0):quad.getZ(0)));
                if (Math.abs(axisValue-(int)axisValue)<0.0001f) {
                    int axisv = Math.round(axisValue);
                    Plane2DMerger<FullFacedQuad> merger = alignedPlaneMerger[axisv];
                    if (merger == null) merger = alignedPlaneMerger[axisv] = new Plane2DMerger(FullFacedQuad.class);
                    int axisA = (int)(axis==0?x:(axis==1?y:x));
                    int axisB = (int)(axis==0?z:(axis==1?z:y));

                    merger.setQuad(axisA, axisB, new FullFacedQuad(x,y,z,material, quad, colors, br, lm));
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

        void mesh(ChunkBuildBuffers buffers) {
            int p = 0;
            for (var plane : alignedPlaneMerger) {
                int finalP = p;
                p++;
                if (plane == null) continue;
                plane.merge(a->{
                    var buf = buffers.get(a.material).getVertexBuffer(direction);
                    buf.push(writeGeometry(a.x,a.y,a.z, a.quadView, a.colors, a.br, a.lm), a.material);
                }, b->{
                    if (true) {
                        for (int i = 0; i < b.bounds().count(); i++) {
                            var q = b.quads()[i];
                            var buf = buffers.get(q.material).getVertexBuffer(direction);
                            buf.push(writeGeometry(q.x, q.y, q.z, q.quadView, q.colors, q.br, q.lm), q.material);
                            var k = b.quads()[i].colors;
                            if (k != null) {
                                var l = k[0];
                                for (int m : k) {
                                    if (m != l) {
                                        //System.out.println(m+" "+l);
                                    }
                                }
                            }
                        }
                    } else {

                        var vertices = new ChunkVertexEncoder.Vertex[4];

                        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(b.quads()[0].br);
                        for (int i = 0; i < 4; i++) {
                            int dstIndex = orientation.getVertexIndex(i);
                            var out = vertices[dstIndex] = new ChunkVertexEncoder.Vertex();
                            out.u = b.quads()[0].quadView.getTexU(dstIndex);
                            out.v = b.quads()[0].quadView.getTexV(dstIndex);
                            if (b.quads()[i].colors!=null) {
                                System.out.println(b.quads()[i].colors);
                            }

                            float sx = (i == 2 || i == 3) ? b.bounds().minx() : (b.bounds().maxx() + 1);
                            float sy = (i == 1 || i == 2) ? b.bounds().miny() : (b.bounds().maxy() + 1);
                            out.x = (axis == 0 ? sx : (axis == 1 ? finalP : sx));
                            out.y = (axis == 0 ? finalP : (axis == 1 ? sx : sy));
                            out.z = (axis == 0 ? sy : (axis == 1 ? sy : finalP));
                        }
                        var buf = buffers.get(DefaultMaterials.SOLID).getVertexBuffer(direction);
                        buf.push(vertices, DefaultMaterials.SOLID);
                    }
                });
            }
        }
    }

    private static ChunkVertexEncoder.Vertex[] writeGeometry(
            float x,
            float y,
            float z,
            BakedQuadView quad,
            int[] colors,
            float[] brightness,
            int[] lightmap)
    {
        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(brightness);
        var vertices = new ChunkVertexEncoder.Vertex[4];

        ModelQuadFacing normalFace = quad.getNormalFace();

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex] = new ChunkVertexEncoder.Vertex();
            out.x = x + quad.getX(srcIndex);
            out.y = y + quad.getY(srcIndex);
            out.z = z + quad.getZ(srcIndex);

            out.color = ColorABGR.withAlpha(colors != null ? colors[srcIndex] : 0xFFFFFFFF, brightness[srcIndex]);

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            out.light = lightmap[srcIndex];
        }

        return vertices;
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

    public void mesh(ChunkBuildBuffers buffers) {
        for (var face : faces) {
            if (face == null) continue;
            face.mesh(buffers);
        }
    }
}
