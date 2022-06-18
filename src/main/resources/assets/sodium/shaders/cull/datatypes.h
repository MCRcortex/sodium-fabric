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

//FIXME: need to reduce size of 32 to 16 and or floats to halfs
struct SectionMeta {
    uint id;
    Vec3F bboxOffset;
    Vec3F bboxSize;
    Vec3F pos;

    uint lvis;//combined 4x 8bit (i.e. packed) masks

    Range SOLID[7];
    Range CUTOUT_MIPPED[7];
    Range CUTOUT[7];

    //FIXME: just make 1 range cause all translucents need to be rendered
    Range TRANSLUCENT[7];
    //Range TRANSLUCENT;

    //TODO: maybe move this to its own buffer or something so that the readonly modifier can be applied to the shader
    uint instancedDataId;
};


vec3 Vec3ItoVec3(Vec3I vec) {
    return vec3(vec.x, vec.y, vec.z);
}

vec3 Vec3FtoVec3(Vec3F vec) {
    return vec3(vec.x, vec.y, vec.z);
}

Vec3F Vec3toVec3F(vec3 vec) {
    return Vec3F(vec.x, vec.y, vec.z);
}

struct DrawElementsInstancedCommand {
  uint  count;
  uint  instanceCount;
  uint  firstIndex;
  uint  baseVertex;
  uint  baseInstance;
};

struct InstanceData {
    Vec3F pos;
};