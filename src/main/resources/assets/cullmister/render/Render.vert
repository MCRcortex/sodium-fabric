#version 460
#extension GL_NV_command_list : enable
#extension GL_NV_shader_buffer_load : enable
#extension GL_NV_gpu_shader5 : enable
layout(commandBindableNV) uniform;
#import <DataTypes.h>

layout(std140, binding=0) uniform __scene {
    SceneData scene;
};

layout(location = 0) in vec3 Offset;
layout(location = 1) in vec3 Pos;
layout(location = 2) in vec4 colourA;
layout(location = 3) in vec2 textpos;
layout(location = 4) in vec2 lightcourd;



out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_LightCoord;
//out vec2 v_TexScalar;
void main(){
    gl_Position = scene.pvmt*vec4((Pos*16 + 8) + Offset, 1.0);
    v_TexCoord = textpos;
    v_Color = colourA;
    v_LightCoord = lightcourd;
}