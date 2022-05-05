package net.caffeinemc.sodium.render.terrain.format;

import me.cortex.cullmister.chunkBuilder.QuadOptimizerTerrainVertexType;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexType;
import net.caffeinemc.sodium.render.terrain.format.standard.StandardTerrainVertexType;

public class TerrainVertexFormats {
    public static final TerrainVertexType STANDARD = new StandardTerrainVertexType();
    public static final TerrainVertexType COMPACT = new CompactTerrainVertexType();
    public static final TerrainVertexType OPTIMIZE = new QuadOptimizerTerrainVertexType();
}
