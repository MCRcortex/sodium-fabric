package me.cortex.cullmister.chunkBuilder;

import com.ibm.icu.impl.Pair;
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



    QuadClusterizer clusters = new QuadClusterizer();
    private void onQuadFilled() {
        Quad quad = new Quad(corners, quadSprite);
        clusters.add(quad);
    }


    //TODO: check if face and stuff is needed, always called
    /**
     * Called after all data has been written to the sink
     */
    public void finish(RenderLayer layer, ChunkMeshFace face) {
        if (fill != 0)
            throw new IllegalStateException("Quad sink was not filled with quad");

        if (clusters.isEmpty())
            return;


        BindlessTextureManager.Atlas atlas = BindlessTextureManager.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        //Emit results
        OptimizedTerrainVertexBufferWriterUnsafe writer = new OptimizedTerrainVertexBufferWriterUnsafe(outputBuffer);
        //writer.ensureCapacity(clusters.unique.size()*4);
        for (Quad quad : QuadOptimizer.optimize(clusters)) {
            for (Vert corner : quad.corners) {
                writer.writeVertex(corner.posX(), corner.posY(), corner.posZ(), corner.color(), Math.max(corner.u()-0.0001f, 0), Math.max(corner.v()-0.0001f, 0), corner.light(),
                        atlas.getSpriteIndex(quad.sprite.getId()));
            }
        }
        writer.flush();

    }

}
