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
    visiblity[ID] = uint8_t(1);
}