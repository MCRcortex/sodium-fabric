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
    uint id;
    uint lastRenderFrame;
    Vec3F bboxOffset;
    Vec3F bboxSize;
    Vec3I pos;

    uint layerMeta;

    Range SOLID;
    Range CUTOUT_MIPPED;
    Range CUTOUT;
    Range TRANSLUCENT;
};

struct DrawInstancedData {
    Vec3F offset;
};

struct DrawCommand {
    uint  count;
    uint  instanceCount;
    uint  firstIndex;
    uint  baseVertex;
    uint  baseInstance;
};

vec3 Vec3ItoVec3(Vec3I vec) {
    return vec3(vec.x, vec.y, vec.z);
}

vec3 Vec3FtoVec3(Vec3F vec) {
    return vec3(vec.x, vec.y, vec.z);
}