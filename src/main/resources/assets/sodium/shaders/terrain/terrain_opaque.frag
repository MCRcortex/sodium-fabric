#version 450 core

#import <sodium:include/terrain_fog.frag>
#import <sodium:include/terrain_buffers.frag>
#import <sodium:include/terrain_textures.glsl>
#import <sodium:terrain/terrain_opaque.glsl>

layout(location = 0) in VertexOutput vs_out;
#ifdef ALPHA_CUTOFF
layout (depth_greater) out float gl_FragDepth;
#endif
void main() {
    vec4 frag_diffuse = texture(tex_diffuse, vs_out.tex_diffuse_coord);


    #ifdef ALPHA_CUTOFF
    if (frag_diffuse.a < ALPHA_CUTOFF) {
        gl_FragDepth = 1.1;
        return;
    }
    gl_FragDepth = gl_FragCoord.z;
    #endif

    vec4 frag_mixed = vec4(frag_diffuse.rgb * vs_out.color_shade.rgb * vs_out.color_shade.a, frag_diffuse.a);

    frag_final = _apply_fog(frag_mixed, vs_out.fog_depth, fog_color, fog_start, fog_end);//frag_mixed+0.0001*
}
