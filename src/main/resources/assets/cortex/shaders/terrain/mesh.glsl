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
layout(triangles, max_vertices=128, max_primitives=64) out;

//originAndBaseData.w is in quad count space, so is endIdx
taskNV in Task {
    vec4 originAndBaseData;
    uint quadCount;
};

layout(location=1) out Interpolants {
    vec3 tint;
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

    vec3 origin = vec3((quad.x>>48), ((quad.x>>32)&0xFFFF), ((quad.x>>16)&0xFFFF))*(32.0/65535.0)-8;
    vec3 vA = vec3(((quad.x>>8)&0xFF),(quad.x&0xFF),((quad.y>>56)&0xFF))*(2.0f/255)-1;
    vec3 vB = vec3(((quad.y>>48)&0xFF),((quad.y>>40)&0xFF),((quad.y>>32)&0xFF))*(2.0f/255)-1;

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

    vec2 base = vec2((quad.y>>16)&0xFFFF,quad.y&0xFFFF)*(1.0f/65536);
    vec2 delta = vec2((quad.z>>56)&0xFF,(quad.z>>48)&0xFF)*(1.0f/256);
    OUT[vertexId|0].uv = base;
    OUT[vertexId|1].uv = base+vec2(delta.x,0);
    OUT[vertexId|2].uv = base+delta;
    OUT[vertexId|3].uv = base+vec2(0,delta.y);

    vec3 baseColour = vec3((quad.z>>40)&0xFF, (quad.z>>32)&0xFF, (quad.z>>24)&0xFF)/255;
    //baseColour = vec3(1,1,1);

    OUT[vertexId|0].tint = baseColour;
    OUT[vertexId|1].tint = baseColour;
    OUT[vertexId|2].tint = baseColour;
    OUT[vertexId|3].tint = baseColour;


    if (gl_LocalInvocationID.x == 0) {
        //Remaining quads in workgroup
        gl_PrimitiveCountNV = min(uint(int(quadCount)-int(gl_WorkGroupID.x<<5)), 32)<<1;//2 primatives per quad
    }
}