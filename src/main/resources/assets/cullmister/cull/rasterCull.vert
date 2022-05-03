#version 460
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
#import <DataTypes.h>

layout(location = 0) uniform SceneData *scene;
layout(location = 1) uniform SubChunk *subchunks;
layout(location = 2) uniform uint8_t* visiblity;

vec3 base;
vec3 size;
vec4 getBoxCorner(int corner) {
    return vec4(base + vec3((corner&1), ((corner>>1)&1), ((corner>>2)&1))*size, 1);
}


flat out uint32_t ID;
void main() {
    ID = gl_InstanceID;
    if (subchunks[gl_InstanceID].id != uint16_t(gl_InstanceID)) {
        gl_Position = vec4(-2,-2,-2,1);
        return;
    }
    base = Vec3FtoVec3(subchunks[gl_InstanceID].bboxOffset);
    size = Vec3FtoVec3(subchunks[gl_InstanceID].bboxSize);
    gl_Position = scene->pvmt*getBoxCorner(gl_VertexID);
    //gl_Position = vec4(-2,-2,-2,1);
}