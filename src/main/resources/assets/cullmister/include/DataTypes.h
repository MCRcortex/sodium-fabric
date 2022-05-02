struct Vec3F {
    float x;
    float y;
    float z;
};

struct Vec3I {
    int x;
    int y;
    int z;
};

struct Range {
    uint offset;
    uint count;
};
//There are 4 (technically 5 but fuck tripwires) layers that we need to render
// however transparency layer is funky so.. eh, i think data is still the same
// each layer has like the normal 6 faces and then unassigned which is non face alligned quads
// each of these is defined by a range pointing into the chunkdata sparse buffer
//Fornow ignore all of the face culling and just dump the whole thing into the draw call if count != 0

struct SubChunk {
    uint16_t id;
    uint16_t lastRenderFrame;
    Vec3F bboxOffset;
    Vec3F bboxSize;
    Vec3F pos;

    uint64_t VBO;

    Range SOLID[7];
    Range CUTOUT_MIPPED[7];
    Range CUTOUT[7];
    Range TRANSLUCENT[7];
};

struct DrawInstancedData {
    Vec3F offset;
};

struct SceneData {
    mat4 pvmt;
    Vec3F cam;
    int instanceCounter;
    int layerCounters[4];
    DrawInstancedData* instanceData;
    void* commandListLayer[4];
};

vec3 Vec3ItoVec3(Vec3I vec) {
    return vec3(vec.x, vec.y, vec.z);
}

vec3 Vec3FtoVec3(Vec3F vec) {
    return vec3(vec.x, vec.y, vec.z);
}


