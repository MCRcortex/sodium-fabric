#version 460
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
#import <DataTypes.h>
layout(early_fragment_tests) in;
//layout(location = 2) uniform uint32_t *visiblity;
layout(location = 2) uniform uint8_t *visiblity;
flat in uint32_t ID;
//out vec4 colour;
void main() {
    /*
    if ((visiblity[ID>>5]&(1<<(ID&31))) != 0) {
        discard;
        return;
    }
    if ((atomicOr(visiblity+(ID>>5), 1<<(ID&31))&(1<<(ID&31))) == 0) {

    }*/

    visiblity[ID] = uint8_t(1);
}