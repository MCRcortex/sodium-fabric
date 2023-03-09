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

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

taskNV out Task {
    vec3 origin;

};

void main() {
    if (sectionVisibility[gl_WorkGroupID.x] != frameId) return;//Early exit if the section isnt visible
    //gl_WorkGroupID.x is also the section node
    //ivec4 header = sectionData[gl_WorkGroupID.x];
    gl_TaskCountNV = 0;
}
