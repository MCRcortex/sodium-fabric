#version 460 core
#extension GL_EXT_ray_query : enable
#extension GL_EXT_buffer_reference : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

layout(location = 0) in vec3 pos;
layout(std140, binding = 0) uniform CameraInfo {
    vec3 corners[4];
    mat4 viewInverse;
} cam;


struct Vertex {
    float x;
    float y;
    float z;
    uint data;
};
layout(buffer_reference) buffer Verticies {Vertex verts[]; };

layout(binding = 2) buffer BlasVertexAddresses { uint64_t address[]; } vertexBlobs;

layout(binding = 1) uniform accelerationStructureEXT acc;

layout(binding = 3) uniform  sampler2D blockTex;

layout(location=0) out vec4 color;


vec2 ray2uvCo(rayQueryEXT ray) {
    bool isSideA = (rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)&1)==0;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(ray, true);

    vec2 t0 = vec2(0,0);
    vec2 t2 = isSideA?vec2(1,1):vec2(1,0);
    vec2 t1 = isSideA?vec2(0,1):vec2(1,1);

    vec3 barys = vec3(1.0f - barry.x - barry.y, barry.x, barry.y);
    vec2 texCoords = t0 * barys.x + t1 * barys.y + t2 * barys.z;
    return texCoords;
}
void main(void) {
    vec2  p         = pos.xy;
    vec3  origin    = cam.viewInverse[3].xyz;
    vec3  target    = mix(mix(cam.corners[0], cam.corners[2], p.y), mix(cam.corners[1], cam.corners[3], p.y), p.x);
    vec4  direction = cam.viewInverse * vec4(normalize(target.xyz), 0.0);

    rayQueryEXT rayQuery;
    rayQueryInitializeEXT(rayQuery,
    acc,
    gl_RayFlagsOpaqueEXT|gl_RayFlagsCullBackFacingTrianglesEXT,
    0xFF,
    origin,
    0.001,
    direction.xyz,
    512.0);
    while(rayQueryProceedEXT(rayQuery)) {}
    float d = rayQueryGetIntersectionTEXT(rayQuery, true);
    int t = rayQueryGetIntersectionPrimitiveIndexEXT(rayQuery, true);


    vec3 hitPos = origin + direction.xyz*d;

    rayQueryEXT rayQuery2;
    rayQueryInitializeEXT(rayQuery2,
    acc,
    gl_RayFlagsOpaqueEXT|gl_RayFlagsCullBackFacingTrianglesEXT,
    0xFF,
    hitPos+vec3(0,0.001,0),
    0.001,
    vec3(0.5,0.5,0.5),
    512.0);
    while(rayQueryProceedEXT(rayQuery2)) {}
    d = rayQueryGetIntersectionTEXT(rayQuery2, true);

    t>>=1;

    int blasBlob = rayQueryGetIntersectionInstanceCustomIndexEXT(rayQuery, true);
    Verticies vertexData = Verticies(vertexBlobs.address[blasBlob]);
    uint r = vertexData.verts[t*4].data;

    color = texture(blockTex, ray2uvCo(rayQuery));
    if (d<500) {
        color*=0.5;
        vec4 shadowHitColour = texture(blockTex, ray2uvCo(rayQuery2));
        color += shadowHitColour*0.25;
    }

    //color = vec4(((r>>0)&15)/15.0, ((r>>4)&15)/15.0,((r>>8)&15)/15.0,1)*((d>500)?1:0.25);
}
