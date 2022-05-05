#version 460
#extension GL_NV_bindless_texture : enable
#extension GL_NV_command_list : enable
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
layout(commandBindableNV) uniform;
#import <DataTypes.h>

layout(std140, binding=0) uniform __scene {
    SceneData scene;
};

layout(location=9) uniform sampler2D *samplers2;

layout(location = 0) in vec3 Offset;
layout(location = 1) in vec3 Pos;
layout(location = 2) in vec4 colourA;
layout(location = 3) in vec2 textpos;
layout(location = 4) in vec2 lightcourd;
layout(location = 5) in uint16_t textureID;


//TODO: see if its faster to pass the blocktextuer sampler here or if passing textureID and doing the lut in the
// fragment shader is better
out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_LightCoord;
flat out sampler2D v_BlockTex;
void main(){
    v_BlockTex = samplers2[textureID];
    gl_Position = scene.pvmt*vec4((Pos*16 + 8) + Offset, 1.0);
    v_TexCoord = textpos;
    v_Color = colourA;
    v_LightCoord = lightcourd;
}