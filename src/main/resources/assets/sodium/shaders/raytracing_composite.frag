#version 460 core
#extension GL_EXT_ray_query : enable

layout(location = 0) in vec3 pos;
layout(std140, binding = 0) uniform CameraInfo {
    vec3 corners[4];
    mat4 viewInverse;
} cam;

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
    0.1,
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
    0.1,
    vec3(0.5,0.5,0.5),
    512.0);
    while(rayQueryProceedEXT(rayQuery2)) {}
    d = rayQueryGetIntersectionTEXT(rayQuery2, true);
    //int t = rayQueryGetIntersectionPrimitiveIndexEXT(rayQuery, true);

    //color = vec4(d/512,1,float(t&0xFF)/256.0f,1);

    color = vec4(1,1,1,1)*(1-d/64.0f);
}
