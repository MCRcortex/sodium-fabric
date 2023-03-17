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

#define MESH_WORKLOAD_PER_INVOCATION 16

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

taskNV out Task {
    vec4 originAndBaseData;
    uint endIdx;
};
uvec4 offsetData;
uint32_t extractOffset(uint idx) {
    if (idx == 0) {
        return 0;
    }
    return (uint16_t)(offsetData[idx>>1]>>((idx&1)*4));
}


void main() {
    uint sectionId = ((gl_WorkGroupID.x)&~(0x7<<29));
    uint side = (gl_WorkGroupID.x>>29)&7;//Dont need the &
    if (sectionVisibility[sectionId]!=frameId || (((uint)sectionFaceVisibility[sectionId])&(1<<side))==0) {
        //Early exit if the section isnt visible
        //gl_TaskCountNV = 0;
        return;
    }
    //gl_WorkGroupID.x is also the section node
    //ivec4 header = sectionData[gl_WorkGroupID.x];
    //Emit enough mesh shaders such that max(gl_GlobalInvocationID.x)>=quadCount
    ivec4 header = sectionData[sectionId].header;
    offsetData = (uvec4)sectionData[sectionId].renderRanges;
    uint baseDataOffset = (uint)header.w;
    uint a = extractOffset(side);
    uint b = extractOffset(side+1);
    uint quadCount = (b-a);
    originAndBaseData.w = uintBitsToFloat(a+baseDataOffset);
    endIdx = (b+baseDataOffset);
    gl_TaskCountNV = quadCount/MESH_WORKLOAD_PER_INVOCATION;
}
