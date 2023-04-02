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

layout(local_size_x = 16) in;
layout(triangles, max_vertices=64, max_primitives=32) out;

//originAndBaseData.w is in quad count space, so is endIdx
taskNV in Task {
    vec4 originAndBaseData;
    uint quadCount;
};

layout(location=1) out Interpolants {
    vec2 uv;
} OUT[];


//TODO: TRY 4 INVOCATIONS PER QUAD, duplicate work yes but much less work per thread

//TODO: extra per quad culling
void main() {
    if ((gl_GlobalInvocationID.x)>=quadCount) { //If its over the quad count, dont render
        return;
    }
    //Each mesh invocation emits an entire quad
    //uvec4 quad = terrainData[(floatBitsToUint(originAndBaseData.w)+(gl_GlobalInvocationID.x))<<1];
    u64vec4 quad = terrainData[(floatBitsToUint(originAndBaseData.w)+(gl_GlobalInvocationID.x))];

    vec3 origin = vec3((int16_t)(quad.x>>48), (int16_t)((quad.x>>32)&0xFFFF), (int16_t)((quad.x>>16)&0xFFFF))*(32.0/65536.0)-8;
    vec3 vA = vec3((int8_t)((quad.x>>8)&0xFF),(int8_t)(quad.x&0xFF),(int8_t)((quad.y>>56)&0xFF))*(2.0f/255)-1;
    vec3 vB = vec3((int8_t)((quad.y>>48)&0xFF),(int8_t)((quad.y>>40)&0xFF),(int8_t)((quad.y>>32)&0xFF))*(2.0f/255)-1;

    vec3 offset = originAndBaseData.xyz;
    uint vertexId = gl_LocalInvocationID.x<<2;
    gl_MeshVerticesNV[vertexId|0].gl_Position = MVP*vec4(origin+offset,1);
    gl_MeshVerticesNV[vertexId|1].gl_Position = MVP*vec4(origin+offset+vA,1);
    gl_MeshVerticesNV[vertexId|2].gl_Position = MVP*vec4(origin+offset+vA+vB,1);
    gl_MeshVerticesNV[vertexId|3].gl_Position = MVP*vec4(origin+offset+vB,1);


    uint primId = gl_LocalInvocationID.x * 6;

    gl_PrimitiveIndicesNV[primId]   = vertexId|0;
    gl_PrimitiveIndicesNV[primId+1] = vertexId|1;
    gl_PrimitiveIndicesNV[primId+2] = vertexId|2;

    gl_PrimitiveIndicesNV[primId+3] = vertexId|0;
    gl_PrimitiveIndicesNV[primId+4] = vertexId|2;
    gl_PrimitiveIndicesNV[primId+5] = vertexId|3;


    gl_MeshPrimitivesNV[(gl_LocalInvocationID.x<<1)].gl_PrimitiveID =   int(gl_GlobalInvocationID.x);
    gl_MeshPrimitivesNV[(gl_LocalInvocationID.x<<1)+1].gl_PrimitiveID = int(gl_GlobalInvocationID.x);

    if (gl_LocalInvocationID.x == 0) {
        //Remaining quads in workgroup
        gl_PrimitiveCountNV = min(uint(int(quadCount)-int(gl_WorkGroupID.x<<4))<<1, 32);//2 primatives per quad
    }
}