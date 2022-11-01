#version 460 core
#extension GL_EXT_ray_query : enable

#import <sodium:include/terrain_fog.frag>
#import <sodium:include/terrain_buffers.frag>
#import <sodium:include/terrain_textures.glsl>
#import <sodium:terrain/terrain_opaque.glsl>

layout(location = 0) in VertexOutput vs_out;
//layout(binding = 10, set = 0) uniform accelerationStructureEXT acc;

void main() {
    vec4 frag_diffuse = texture(tex_diffuse, vs_out.tex_diffuse_coord);

#ifdef ALPHA_CUTOFF
    if (frag_diffuse.a < ALPHA_CUTOFF) {
        discard;
    }
#endif

    vec4 frag_mixed = vec4(frag_diffuse.rgb * vs_out.color_shade.rgb * vs_out.color_shade.a, frag_diffuse.a);
    /*
    rayQueryEXT rayQuery;
    rayQueryInitializeEXT(rayQuery,
        acc,
        gl_RayFlagsOpaqueEXT,
        0xFF,
        vec3(0,0,0),
        0.1,
        vec3(0,0,0),
        100.0);
    while(rayQueryProceedEXT(rayQuery)) {}
    float t = rayQueryGetIntersectionTEXT(rayQuery, true);*/
    frag_final = _apply_fog(frag_mixed, vs_out.fog_depth, fog_color, fog_start, fog_end);//*t
}