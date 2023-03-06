#version 460
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require
#extension GL_NV_fragment_shader_barycentric : enable


layout(location = 0) out vec4 colour;
void main() {
    colour = vec4(gl_BaryCoordNV, 1.0);
}