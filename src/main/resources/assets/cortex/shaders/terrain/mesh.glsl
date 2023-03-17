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

layout(local_size_x = 32) in;
layout(triangles, max_vertices=64, max_primitives=32) out;

taskNV in Task {
    vec4 originAndBaseData;
    uint endIdx;
};


//TODO: extra per meshlet culling here (hell even per quad culling)
void main() {
    uint id = gl_GlobalInvocationID.x>>1;
    if (id>=endIdx) {//If its over the invocation id, dont render
        return;
    }
    //Each pair of meshlet invokations emits 2 vertices each and 1 primative each
    id += floatBitsToUint(originAndBaseData.w);
    gl_PrimitiveCountNV = 32;
}