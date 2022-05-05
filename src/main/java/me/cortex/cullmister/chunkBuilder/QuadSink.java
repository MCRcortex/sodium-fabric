package me.cortex.cullmister.chunkBuilder;

import me.cortex.cullmister.textures.BindlessTextureManager;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexBufferWriterUnsafe;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexType;
import net.caffeinemc.sodium.render.terrain.quad.ModelQuadView;
import net.caffeinemc.sodium.render.terrain.quad.properties.ChunkMeshFace;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferView;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;

import java.util.*;

//NOTE: THIS is a very dodgy solution
// TODO: maybe make a post transformer system that pumps through the native buffer result of the chunk
//  cause mods could easily break this
public class QuadSink implements TerrainVertexSink {
    Sprite quadSprite;
    int fill;
    Vert[] corners = new Vert[4];

    VertexBufferView outputBuffer;
    public QuadSink(VertexBufferView outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    public void setFollowingQuadView(ModelQuadView quad) {
        if (quadSprite != null)
            throw new IllegalStateException("Already building quad");
        quadSprite = quad.getSprite();
    }

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light) {
        if (quadSprite == null)
            throw new IllegalStateException("quad sprite not set");
        //TODO: need to verify the ranges of the input u,v are within the quadsprite bounds
        float adjTexU = u - quadSprite.getMinU();
        float adjTexV = v - quadSprite.getMinV();
        adjTexU = Math.abs(adjTexU) < 0.0001?0:adjTexU;
        adjTexV = Math.abs(adjTexV) < 0.0001?0:adjTexV;
        float texUScale = 1/(quadSprite.getMaxU() - quadSprite.getMinU());
        float texVScale = 1/(quadSprite.getMaxV() - quadSprite.getMinV());
        adjTexU *= texUScale;
        adjTexV *= texVScale;
        //TODO: NOTE setting this too 1 will break CompactTerrainFormat thing cause the CompactTerrainVertexType.encodeBlockTexture(1.0f) returns 0 which is wrong
        adjTexU = Math.abs(adjTexU) > 0.998?1:adjTexU;
        adjTexV = Math.abs(adjTexV) > 0.998?1:adjTexV;
        corners[fill++] = new Vert(posX, posY, posZ, color, adjTexU, adjTexV, light);
        if (fill == 4) {
            onQuadFilled();
            quadSprite = null;
            fill = 0;
        }
    }

    @Override
    public void ensureCapacity(int count) {
        if ((count%4)!=0) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void flush() {

    }




    //TODO: FIND A BETTER DATASTRUCTURE
    ArrayList<Quad> quads = new ArrayList<>();
    private void onQuadFilled() {
        Quad quad = new Quad(corners, quadSprite);
        quads.add(quad);
    }


    //TODO: check if face and stuff is needed, always called
    /**
     * Called after all data has been written to the sink
     */
    public void finish(RenderLayer layer, ChunkMeshFace face) {
        if (quads.isEmpty())
            return;
        if (fill != 0)
            throw new IllegalStateException("Quad sink was not filled with quad");

        //TODO: segment it one more time based on the position with respect to face, e.g. only those on the same 2d grid
        // are grouped
        //TODO: this grid needs to be with respect to face thus, no filter can be applied if face == UNASSIGNED
        List<List<Quad>> compatibiltySets = new LinkedList<>();
        outer:
        for (Quad quad : quads) {
            for (List<Quad> set : compatibiltySets) {
                if (set.get(0).looselyCompatibleWith(quad)) {
                    set.add(quad);
                    continue outer;
                }
            }
            compatibiltySets.add(new LinkedList<>(List.of(quad)));
        }

        List<Quad> outputQuads = new LinkedList<>();


        /*
        Iterator<List<Quad>> iterator = compatibiltySets.iterator();
        while (iterator.hasNext()) {
            List<Quad> set = iterator.next();
            if (set.size() == 1) {
                outputQuads.add(set.get(0));
                iterator.remove();
            }
        }
         */

        for(List<Quad> set : compatibiltySets) {
            if (set.size() == 1)
                outputQuads.add(set.get(0));
            else
                mesh(set, outputQuads, layer, face);
        }


        BindlessTextureManager.Atlas atlas = BindlessTextureManager.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        //Emit results
        OptimizedTerrainVertexBufferWriterUnsafe writer = new OptimizedTerrainVertexBufferWriterUnsafe(outputBuffer);
        writer.ensureCapacity(outputQuads.size()*4);
        for (Quad quad : outputQuads) {
            for (Vert corner : quad.corners) {
                writer.writeVertex(corner.posX(), corner.posY(), corner.posZ(), corner.color(), Math.max(corner.u()-0.0001f, 0), Math.max(corner.v()-0.0001f, 0), corner.light(),
                        atlas.getSpriteIndex(quad.sprite.getId()));
            }
        }
        writer.flush();

        outputQuads.size();
    }


    private void mesh(List<Quad> set, List<Quad> output, RenderLayer layer, ChunkMeshFace face) {
        //TODO: Try merge incorrect facing faces
        if (face == ChunkMeshFace.UNASSIGNED)
            return;

        //Do double directional greedy mesh
        //TODO: Find a better way to do this

        Set<Quad> merged = new HashSet<>();

        do {
            List<Quad> newQuads = new LinkedList<>();
            merged.clear();
            for (int i = 0; i < set.size() - 1; i++) {
                if (merged.contains(set.get(i)))
                    continue;
                for (int j = i + 1; j < set.size(); j++) {
                    if (merged.contains(set.get(j)))
                        continue;
                    if (set.get(i).connectingVerticies(set.get(j)) > 1) {
                        //System.out.println("QUADMERGE");
                        if (set.get(i).connectingVerticies(set.get(j))!=2) {
                            System.out.println("WHAT");
                        }
                        merged.add(set.get(i));
                        merged.add(set.get(j));
                        boolean[] U = new boolean[4];
                        boolean[] V = new boolean[4];
                        for (int A = 0; A < 4; A++) {
                            for (int B = 0; B < 4; B++) {
                                if (set.get(i).corners[A].isSamePos(set.get(j).corners[B])) {
                                    U[A] = true;
                                    V[B] = true;
                                }
                            }
                        }
                        int v = 0;
                        Vert[] c = new Vert[4];
                        for (int A = 0; A < 4; A++) {
                            if (!U[A]) {
                                c[v++] = set.get(i).corners[A];
                            }
                            if (!V[A]) {
                                c[v++] = set.get(j).corners[A];
                            }
                        }
                        newQuads.add(new Quad(c, set.get(i).sprite));
                        break;
                    }
                }
            }
            set.removeAll(merged);
            set.addAll(newQuads);
        } while (merged.size() != 0);
        //No meshing yet
        output.addAll(set);
    }
}
