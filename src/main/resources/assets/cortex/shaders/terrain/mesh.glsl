#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_vote : require

#import <cortex:occlusion/scene.glsl>

layout(local_size_x = 8) in;
layout(triangles, max_vertices=8, max_primitives=12) out;

taskNV in Task {
    vec3 origin;

};

void main() {
}