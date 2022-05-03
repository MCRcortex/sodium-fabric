#version 460
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
#import <DataTypes.h>
layout(early_fragment_tests) in;
layout(location = 2) uniform uint8_t *visiblity;
flat in uint32_t ID;
//out vec4 colour;
void main() {
    visiblity[ID] = uint8_t(1);
    /*
    float n = 1.0;
    float f = 5000.0;
    float c = (2) / (10000-10000*gl_FragCoord.z);
    colour.rgb = vec3(c);
    */
}