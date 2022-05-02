#version 460
#extension GL_NV_command_list : enable
layout(commandBindableNV) uniform;

layout(location=0,index=0) out vec4 colour;
in vec4 v_Color; // The interpolated vertex color
in vec2 v_TexCoord; // The interpolated block texture coordinates
in vec2 v_LightCoord; // The interpolated light map texture coordinates
//in vec2 v_TexScalar;//Texture scale factor

layout(binding = 0) uniform sampler2D u_BlockTex; // The block texture sampler
layout(binding = 1) uniform sampler2D u_LightTex; // The light map texture sampler

void main() {
    //colour = v_Color;return;
    vec4 c = texture(u_BlockTex, v_TexCoord);
    if (c.a < 0.5)
        discard;
    vec4 light = texture(u_LightTex, v_LightCoord);
    colour = vec4((c.rgb * light.rgb) * v_Color.rgb * v_Color.a, c.a);
}