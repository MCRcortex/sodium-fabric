package net.caffeinemc.sodium.render.terrain.format.merging;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.light.data.QuadLightData;
import net.caffeinemc.sodium.render.terrain.quad.ModelQuadView;
import net.caffeinemc.sodium.render.terrain.quad.properties.ModelQuadOrientation;
import net.caffeinemc.sodium.util.packed.ColorABGR;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;

public class MergingTerrainVertexSink implements TerrainVertexSink {

    private record Quad(int mergability, Vert[] verts, UV[] uvs, Vector3f shape, int[] originalIndex) {

    }

    private record Vert(float x, float y, float z, int colour, int lm) implements Comparable<Vert> {

        @Override
        public int compareTo(@NotNull Vert o) {
            int c = Float.compare(o.x, x);
            if (c != 0) return c;
            c = Float.compare(o.z, z);
            if (c != 0) return c;
            c = Float.compare(o.y, y);
            if (c != 0) return c;
            c = Integer.compare(o.colour, colour);
            if (c != 0) return c;
            return Integer.compare(o.lm, lm);
        }
    }

    private record QuadVertPair(int vertIdx, Quad quad) {

    }

    private record UV(float u, float v) {

    }

    private final Object2ObjectOpenHashMap<Vert, List<QuadVertPair>> mapping = new Object2ObjectOpenHashMap<>(4096);

    private boolean canMergeOnAxis(Vert a, Vert b) {
        if (false)
            return true;
        //TODO: check that uv maps to an edge of the texture, else you cant merge it
        return a.colour == b.colour && a.lm == b.lm;
    }

    private static final int[][] LUT = new int[][] {
            {-1,3,3,-1},
            {2,-1,-1,2},
            {1,-1,-1,1},
            {-1,0,0,-1}
    };
    private final ObjectOpenHashSet<Quad> quads = new ObjectOpenHashSet<>();

    private Quad addOrMerge(Quad quad) {
        outer:
        for (int i = 0; i < 4; i++) {
            Vert cv = quad.verts[i];
            List<QuadVertPair> verts1 = mapping.get(cv);
            if (verts1 == null) {
                mapping.computeIfAbsent(cv, (a) -> new LinkedList<>()).add(new QuadVertPair(i, quad));
            } else {
                Iterator<QuadVertPair> iter =  verts1.iterator();
                while (iter.hasNext()) {
                    QuadVertPair qvp = iter.next();
                    if (qvp.quad == quad)//FIXME SEE WHY OR HOW THIS IS POSSIBLE
                        continue;
                    int merg = (qvp.quad.mergability&quad.mergability);
                    if (merg == 0/* || !quad.shape.equals(qvp.quad.shape)*/)
                        continue;

                    if (merg != 3) {
                        if (merg == 1) {
                            if (Math.abs(qvp.vertIdx-i) != 2)
                                continue;
                        } else if (merg == 2) {
                            if (Math.abs(qvp.vertIdx-i) != 1)
                                continue;
                        }
                    }

                    /*
                    if (!Arrays.equals(quad.originalIndex, qvp.quad.originalIndex)) {
                        continue;
                    }*/
                    if (Math.abs(qvp.vertIdx-i)>2)
                        continue;
                    if (!qvp.quad.uvs[qvp.vertIdx].equals(quad.uvs[qvp.vertIdx]))
                        continue;
                    if (!qvp.quad.uvs[i].equals(quad.uvs[i]))
                        continue;
                    int remIdSelf = LUT[qvp.vertIdx][i];
                    if (remIdSelf == -1)
                        continue;
                    int remIdOther = LUT[i][qvp.vertIdx];
                    if (!qvp.quad.verts[remIdOther].equals(quad.verts[remIdSelf]))
                        continue;
                    if (!qvp.quad.uvs[remIdOther].equals(quad.uvs[remIdOther]))
                        continue;
                    if (!qvp.quad.uvs[remIdSelf].equals(quad.uvs[remIdSelf]))
                        continue;

                    //Remove everything cause can merge the quads
                    iter.remove();
                    for (int k = 0; k < 4; k++) {
                        var a = mapping.get(qvp.quad.verts[k]);
                        if (a != null)
                            a.remove(new QuadVertPair(k, qvp.quad));
                    }

                    for (int k = 0; k < i; k++) {
                        var a = mapping.get(quad.verts[k]);
                        if (a != null)
                            a.remove(new QuadVertPair(k, quad));
                    }
                    quads.remove(quad);
                    quads.remove(qvp.quad);
                    UV[] uvs2 = new UV[4];
                    uvs2[qvp.vertIdx] = quad.uvs[qvp.vertIdx];
                    uvs2[i] = qvp.quad.uvs[i];
                    uvs2[remIdOther] = quad.uvs[remIdOther];
                    uvs2[remIdSelf] = qvp.quad.uvs[remIdSelf];
                    Vert[] verts2 = new Vert[4];
                    verts2[remIdSelf] = qvp.quad.verts[remIdSelf];
                    verts2[remIdOther] = quad.verts[remIdOther];
                    verts2[i] = qvp.quad.verts[i];
                    verts2[qvp.vertIdx] = quad.verts[qvp.vertIdx];
                    return new Quad(quad.mergability&qvp.quad.mergability, verts2, uvs2, new Vector3f(verts2[3].x-verts2[0].x,verts2[3].y-verts2[0].y,verts2[3].z-verts2[0].z).normalize(), quad.originalIndex);
                    //break outer;
                }
                verts1.add(new QuadVertPair(i, quad));
            }
        }
        return null;
    }


    /*
    private void writeQuadNormal(BlockPos origin, ModelQuadOrientation orientation, Vec3d blockOffset, ModelQuadView src, int[] colors, QuadLightData light) {
        for (int i = 0; i < 4; i++) {
            int j = orientation.getVertexIndex(i);

            float x = src.getX(j) + (float) blockOffset.getX();
            float y = src.getY(j) + (float) blockOffset.getY();
            float z = src.getZ(j) + (float) blockOffset.getZ();


            int color = ColorABGR.repack(colors != null ? colors[j] : 0xFFFFFFFF, light.br[j]);

            float u = src.getTexU(j);
            float v = src.getTexV(j);

            int lm = light.lm[j];

            delegate.writeVertex(origin, x, y, z, color, u, v, lm);
        }
    }

    private Vert makeVert(int idx, BlockPos origin, ModelQuadOrientation orientation, Vec3d blockOffset, ModelQuadView src, int[] colors, QuadLightData light) {
        int j = orientation.getVertexIndex(idx);

        float x = Math.round((src.getX(j) + (float) blockOffset.getX() + origin.getX())*(1<<21))/(float)(1<<21);
        float y = Math.round((src.getY(j) + (float) blockOffset.getY() + origin.getY())*(1<<21))/(float)(1<<21);
        float z = Math.round((src.getZ(j) + (float) blockOffset.getZ() + origin.getZ())*(1<<21))/(float)(1<<21);
        int color = ColorABGR.repack(colors != null ? colors[j] : 0xFFFFFFFF, light.br[j]);
        int lm = light.lm[j];
        return new Vert(x,y,z,color,lm);
    }

    public void writeQuad(BlockPos origin, ModelQuadOrientation orientation, Vec3d blockOffset, ModelQuadView src, int[] colors, QuadLightData light) {
        Vert[] verts = new Vert[4];
        UV[] uvs = new UV[4];
        int[] originalIdx = new int[4];
        for (int i = 0; i < 4; i++) {
            Vert v = makeVert(i, origin, orientation, blockOffset, src, colors, light);
            int j = orientation.getVertexIndex(i);
            UV uv = new UV(src.getTexU(j), src.getTexV(j));
            int o = i;
            int k = 0;
            for (; k < 4; k++) {
                if (verts[k] == null) {
                    verts[k] = v;
                    uvs[k] = uv;
                    originalIdx[k] = o;
                    break;
                }
                if (verts[k].compareTo(v) < 0) {
                    Vert tv = verts[k];
                    UV tu = uvs[k];
                    int to = originalIdx[k];
                    verts[k] = v;
                    uvs[k] = uv;
                    originalIdx[k] = o;
                    uv = tu;
                    v = tv;
                    o = to;
                }
            }
        }

        int mergability = 0;
        mergability |= (canMergeOnAxis(verts[1], verts[3]) && canMergeOnAxis(verts[0], verts[2]))?1:0;//Horizontal
        mergability |= (canMergeOnAxis(verts[0], verts[1]) && canMergeOnAxis(verts[2], verts[3]))?2:0;//Vertical
        if (mergability == 0) {
            writeQuadNormal(origin, orientation, blockOffset, src, colors, light);
            return;
        }
        int[] idxs = new int[4];
        for (int i = 0; i < 4; i++) {
            idxs[originalIdx[i]] = i;
        }

        Quad quad = new Quad(mergability, verts, uvs, new Vector3f(verts[3].x-verts[0].x,verts[3].y-verts[0].y,verts[3].z-verts[0].z).normalize(), idxs);
        while (true) {
            Quad newQ = addOrMerge(quad);
            if (newQ == null)
                break;
            quad = newQ;
        }
        quads.add(quad);
    }*/

    final TerrainVertexSink delegate;
    public MergingTerrainVertexSink(TerrainVertexSink sink) {
        this.delegate = sink;
    }


    //Current quad building data
    private int cindex = 0;
    private Vert[] verts = new Vert[4];
    private UV[] uvs = new UV[4];
    private int[] originalIdx = new int[4];

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v_, int light) {
        Vert v = new Vert(posX, posY, posZ, color, light);
        UV uv = new UV(u, v_);
        int o = cindex++;

        for (int k = 0; k < 4; k++) {
            if (verts[k] == null) {
                verts[k] = v;
                uvs[k] = uv;
                originalIdx[k] = o;
                break;
            }
            if (verts[k].compareTo(v) < 0) {
                Vert tv = verts[k];
                UV tu = uvs[k];
                int to = originalIdx[k];
                verts[k] = v;
                uvs[k] = uv;
                originalIdx[k] = o;
                uv = tu;
                v = tv;
                o = to;
            }
        }


        if (cindex == 4) {

            int[] idxs = new int[4];
            for (int i = 0; i < 4; i++) {
                idxs[originalIdx[i]] = i;
            }

            int mergability = 0;
            mergability |= (canMergeOnAxis(verts[1], verts[3]) && canMergeOnAxis(verts[0], verts[2]))?1:0;//Horizontal
            mergability |= (canMergeOnAxis(verts[0], verts[1]) && canMergeOnAxis(verts[2], verts[3]))?2:0;//Vertical
            if (mergability == 0) {

                for (int i : idxs) {
                    Vert V = verts[i];
                    UV U = uvs[i];
                    delegate.writeVertex(V.x, V.y, V.z, V.colour, U.u, U.v, V.lm);
                }
                for (int i = 0; i < 4; i++)
                    verts[i] = null;
                cindex = 0;
                return;
            }


            Quad quad = new Quad(mergability, verts, uvs, new Vector3f(verts[3].x-verts[0].x,verts[3].y-verts[0].y,verts[3].z-verts[0].z).normalize(), idxs);
            while (true) {
                Quad newQ = addOrMerge(quad);
                if (newQ == null)
                    break;
                quad = newQ;
            }
            quads.add(quad);
            cindex = 0;
            verts = new Vert[4];
            uvs = new UV[4];
            originalIdx = new int[4];
        }
    }


    @Override
    public void ensureCapacity(int count) {
        delegate.ensureCapacity(count);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    public void finish() {
        delegate.ensureCapacity(quads.size()*4);
        for (Quad q : quads) {
            for (int i : q.originalIndex) {
                Vert v = q.verts[i];
                UV u = q.uvs[i];
                delegate.writeVertex(v.x, v.y, v.z, v.colour, u.u, u.v, v.lm);
            }
        }
        delegate.flush();
        delegate.finish();
    }

    @Override
    public TerrainVertexSink getDelegate() {
        return delegate;
    }
}



/*final Object2ObjectOpenHashMap<Vertex, Quad> vertexMap = new Object2ObjectOpenHashMap<>();


    private record Vertex(float posX, float posY, float posZ, int color, float u, float v, int light) {
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Vertex vertex = (Vertex) o;
            return Float.compare(vertex.posX, posX) == 0 && Float.compare(vertex.posY, posY) == 0 && Float.compare(vertex.posZ, posZ) == 0 && color == vertex.color && Float.compare(vertex.u, u) == 0 && Float.compare(vertex.v, v) == 0 && light == vertex.light;
        }

        @Override
        public int hashCode() {
            return Objects.hash(posX, posY, posZ, color, u, v, light);
        }
    }

 */