package me.cortex.nv.structs;

import org.joml.Vector3i;

public class RegionStruct {//128 bytes
    //All header
    int id;
    int section_count;
    Vector3i startChunk;
    Vector3i sizeInChunks;
    int baseTerrainGeometryOffset;
    //Payload
    RenderLayerStruct renderLayers;
}
