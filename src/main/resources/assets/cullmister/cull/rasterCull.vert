#version 460
#extension GL_NV_command_list : enable
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
layout(commandBindableNV) uniform;
#import <DataTypes.h>

layout(location = 0) uniform SceneData *scene;
layout(location = 1) uniform SubChunk *subchunks;
layout(location = 2) uniform uint8_t *visiblity;

void main() {

}