package me.cortex.cullmister.chunkBuilder;

import net.caffeinemc.gfx.api.array.attribute.VertexFormat;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexType;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferView;
import net.minecraft.client.render.VertexConsumer;

public class QuadOptimizerTerrainVertexType implements TerrainVertexType {
    //NOTE: it is very much copied from compactTerrainVertexType
    @Override
    public float getVertexRange() {
        return 16.0f;
    }

    @Override
    public TerrainVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return new QuadSink(buffer);
    }

    @Override
    public VertexFormat<TerrainMeshAttribute> getCustomVertexFormat() {
        //TODO: replace with own format
        return CompactTerrainVertexType.VERTEX_FORMAT;
    }

    @Override
    public TerrainVertexSink createFallbackWriter(VertexConsumer consumer) {
        throw new UnsupportedOperationException();
    }
}
