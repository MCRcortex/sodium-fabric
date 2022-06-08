#extension GL_ARB_gpu_shader_int64 : enable
#extension GL_ARB_gpu_shader5 : enable

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
    Range TRANSLUCENT[7];
};


vec3 Vec3ItoVec3(Vec3I vec) {
    return vec3(vec.x, vec.y, vec.z);
}

vec3 Vec3FtoVec3(Vec3F vec) {
    return vec3(vec.x, vec.y, vec.z);
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