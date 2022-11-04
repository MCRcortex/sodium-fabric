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

layout(location=0) out vec4 color;

void main(void) {
    vec2  p         = pos.xy;
    vec3  origin    = cam.viewInverse[3].xyz;
    vec3  target    = mix(mix(cam.corners[0], cam.corners[2], p.y), mix(cam.corners[1], cam.corners[3], p.y), p.x);
    vec4  direction = cam.viewInverse * vec4(normalize(target.xyz), 0.0);

    rayQueryEXT rayQuery;
    rayQueryInitializeEXT(rayQuery,
    acc,
    gl_RayFlagsOpaqueEXT,
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
    gl_RayFlagsOpaqueEXT,
    0xFF,
    hitPos+vec3(0,0.001,0),
    0.001,
    vec3(0.5,0.5,0.5),
    512.0);
    while(rayQueryProceedEXT(rayQuery2)) {}
    d = rayQueryGetIntersectionTEXT(rayQuery2, true);
    //int t = rayQueryGetIntersectionPrimitiveIndexEXT(rayQuery, true);

    //color = vec4(d/512,1,float(t&0xFF)/256.0f,1);
    t>>=1;

    int blasBlob = rayQueryGetIntersectionInstanceCustomIndexEXT(rayQuery, true);
    Verticies vertexData = Verticies(vertexBlobs.address[blasBlob]);
    uint r = vertexData.verts[t*4].data;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(rayQuery, true);
    color = vec4(barry.x, barry.y,((r>>8)&15)/15.0,1)*((d>500)?1:0.25);
}
