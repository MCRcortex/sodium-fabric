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
    if (gl_GlobalInvocationID.x>>1>=endIdx) {//If its over the invocation id, dont render
        return;
    }
    //Each pair of meshlet invokations emits 2 vertices each and 1 primative each
    uint id = floatBitsToUint(originAndBaseData.w) + gl_GlobalInvocationID.x;
    id <<= 1;//mul by 2 since there are 2 threads per quad each thread needs to process 2 vertices

    Vertex A = terrainData[id];
    Vertex B = terrainData[id|1];

    //TODO: OPTIMIZE
    uint primId = gl_LocalInvocationID.x*3;
    uint idxBase = (gl_LocalInvocationID.x>>1)<<2;
    gl_MeshVerticesNV[(gl_LocalInvocationID.x<<1)].gl_Position   = vec4(A.a,A.b,A.c,A.d);
    gl_MeshVerticesNV[(gl_LocalInvocationID.x<<1)|1].gl_Position = vec4(B.a,B.b,B.c,B.d);
    //TODO: see if ternary or array is faster
    bool isA = (gl_LocalInvocationID.x&1)==0;
    gl_PrimitiveIndicesNV[primId]   = (isA?0:1)+idxBase;
    gl_PrimitiveIndicesNV[primId+1] = (isA?1:2)+idxBase;
    gl_PrimitiveIndicesNV[primId+2] = (isA?3:3)+idxBase;

    gl_PrimitiveCountNV = 32;
}