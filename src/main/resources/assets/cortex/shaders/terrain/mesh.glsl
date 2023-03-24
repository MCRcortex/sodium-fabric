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

//originAndBaseData.w is in quad count space, so is endIdx
taskNV in Task {
    vec4 originAndBaseData;
    uint quadCount;
};

layout(location=1) out Interpolants {
    vec2 uv;
} OUT[];


vec3 decodeVertex(Vertex v) {
    return vec3(v.a,v.b,v.c)*(32.0f/65535)-8.0f;
}

/*
vec3 decodeVertex(Vertex v) {
    return (vec3(v.a>>16,(v.a<<16)>>16,v.b>>16)/32767)*16.0f+8.0f;
}
*/

//TODO: extra per meshlet culling here (hell even per quad culling)
void main() {
    if ((gl_GlobalInvocationID.x>>1)>=quadCount) { //If its over the quad count, dont render
        return;
    }
    //Each pair of meshlet invokations emits 2 vertices each and 1 primative each
    uint id = (floatBitsToUint(originAndBaseData.w)<<2) + (gl_GlobalInvocationID.x<<1);//mul by 2 since there are 2 threads per quad each thread needs to process 2 vertices

    Vertex A = terrainData[id];
    Vertex B = terrainData[id|1];

    //TODO: OPTIMIZE
    uint primId = gl_LocalInvocationID.x*3;
    uint idxBase = (gl_LocalInvocationID.x>>1)<<2;
    gl_MeshVerticesNV[(gl_LocalInvocationID.x<<1)].gl_Position   = MVP*vec4(decodeVertex(A)+originAndBaseData.xyz,1.0);
    gl_MeshVerticesNV[(gl_LocalInvocationID.x<<1)|1].gl_Position = MVP*vec4(decodeVertex(B)+originAndBaseData.xyz,1.0);
    //TODO: see if ternary or array is faster
    bool isA = (gl_LocalInvocationID.x&1)==0;
    gl_PrimitiveIndicesNV[primId]   = (isA?0:2)+idxBase;
    gl_PrimitiveIndicesNV[primId+1] = (isA?1:3)+idxBase;
    gl_PrimitiveIndicesNV[primId+2] = (isA?2:0)+idxBase;

    OUT[(gl_LocalInvocationID.x<<1)|0].uv = (isA?vec2(A.g,A.h):vec2(A.g,A.h))/65535;
    OUT[(gl_LocalInvocationID.x<<1)|1].uv = (isA?vec2(B.g,B.h):vec2(B.g,B.h))/65535;

    gl_MeshPrimitivesNV[gl_LocalInvocationID.x].gl_PrimitiveID = int(gl_GlobalInvocationID.x>>1);

    if (gl_LocalInvocationID.x == 0) {
        //Remaining quads in workgroup
        gl_PrimitiveCountNV = min(uint(int(quadCount)-int(gl_WorkGroupID.x<<4))<<1, 32);//2 primatives per quad
    }
}