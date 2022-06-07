package net.caffeinemc.sodium.render.chunk.occlussion;

public class SectionMeta {
    public static final int SIZE = 1;
    //NOTE: in all cases of vec3, this is a non default struct that has tightly packed x,y,z floats
    //uint32 id;
    //vec3 origin;
    //vec3 minBB;
    //vec3 maxBB;
    //uint8_t layerMSK[4];  // this is a visibility mask for the direction per layer
    //The following are for the layer definitions, where a range consists of a uint32 offset and a uint32 count
    //Range SOLID[7];
    //Range CUTOUT_MIPPED[7];
    //Range CUTOUT[7];
    //Range TRANSLUCENT[7];
}
