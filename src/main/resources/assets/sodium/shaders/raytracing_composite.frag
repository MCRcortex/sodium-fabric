#version 460 core
#extension GL_EXT_ray_query : enable
#extension GL_EXT_buffer_reference : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

layout(location = 0) in vec3 pos;
layout(std140, binding = 0) uniform CameraInfo {
    vec3 corners[4];
    mat4 viewInverse;
} cam;


struct Quad {
    vec2 uvs[4];
    vec4 normal;
};

layout(buffer_reference) buffer Quads {Quad quads[]; };

layout(binding = 2) buffer BlasDataAddresses { uint64_t address[]; } quadBlobs;

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


vec2 ray2uvCoQu(rayQueryEXT ray, Quad quad) {
    bool isSideA = (rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)&1)==0;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(ray, true);

    vec2 t0 = quad.uvs[0];
    vec2 t2 = isSideA?quad.uvs[2]:quad.uvs[3];
    vec2 t1 = isSideA?quad.uvs[1]:quad.uvs[2];

    vec3 barys = vec3(1.0f - barry.x - barry.y, barry.x, barry.y);
    vec2 texCoords = t0 * barys.x + t1 * barys.y + t2 * barys.z;
    return texCoords;
}

Quad getRayQuad(rayQueryEXT ray) {
    int blasBlob = rayQueryGetIntersectionInstanceCustomIndexEXT(ray, true);
    return Quads(quadBlobs.address[blasBlob]).quads[rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)>>1];
}


void trace(in rayQueryEXT rayQuery, vec3 origin, vec3 dir, float max) {
    rayQueryInitializeEXT(rayQuery,
        acc,
        gl_RayFlagsOpaqueEXT,
        0xFF,
        origin,
        0.001,
        dir,
        max);
    while(rayQueryProceedEXT(rayQuery));
}
vec3 uniformSampleHemisphere(const float r1, const float r2)
{
    // cos(theta) = r1 = y
    // cos^2(theta) + sin^2(theta) = 1 -> sin(theta) = srtf(1 - cos^2(theta))
    float sinTheta = sqrt(1 - r1 * r1);
    float phi = 2 * (3.14159265358f) * r2;
    float x = sinTheta * cos(phi);
    float z = sinTheta * sin(phi);
    return vec3(x, r1, z);
}


uint state;

uint rand() {
    state = (state << 13U) ^ state;
    state = state * (state * state * 15731U + 789221U) + 1376312589U;
    return state;
}
float randFloat() {
    return float(rand() & uvec3(0x7fffffffU)) / float(0x7fffffff);
}

vec2 randVec2() {
    return vec2(randFloat(), randFloat());
}

vec3 randomDirection(vec3 normal) {
    vec2 v = randVec2();
    float angle = 2.0 * (3.14159265) * v.x;
    float u = 2.0 * v.y - 1.0;

    vec3 directionOffset = vec3(sqrt(1.0 - u * u) * vec2(cos(angle), sin(angle)), u);
    return normalize(normal + directionOffset);
}

void main(void) {
    vec2  p         = pos.xy;
    vec3  origin    = cam.viewInverse[3].xyz;
    vec3  target    = mix(mix(cam.corners[0], cam.corners[2], p.y), mix(cam.corners[1], cam.corners[3], p.y), p.x);
    vec4  direction = cam.viewInverse * vec4(normalize(target.xyz), 0.0);

    state = floatBitsToUint(p.x);
    rand();
    state ^= floatBitsToUint(p.y);
    rand();

    rayQueryEXT rayQuery;
    trace(rayQuery, origin, direction.xyz, 1024.0);
    float d = rayQueryGetIntersectionTEXT(rayQuery, true);


    state ^= floatBitsToUint(d);
    rand();

    vec3 hitPos = origin + direction.xyz*d;

    rayQueryEXT rayQuery2;
    trace(rayQuery2, hitPos+vec3(0.0,0.01,0), vec3(0.7,0.5,0.1), 1024.0);

    d = rayQueryGetIntersectionTEXT(rayQuery2, true);

    Quad quad = getRayQuad(rayQuery);
    //color = texture(blockTex, mix(quad.uvs[0],vec2(quad.uvs[3].x, quad.uvs[1].y),ray2uvCo(rayQuery)));
    color = texture(blockTex, ray2uvCoQu(rayQuery, quad));
    //color = vec4(ray2uvCo(rayQuery),0,1);
    if (d<1000) {
        color*=0.5;
        Quad quad2 = getRayQuad(rayQuery2);
        vec4 shadowHitColour = texture(blockTex, ray2uvCoQu(rayQuery2, quad2));
        //color += shadowHitColour*0.25;
    }


    float ao = 1;
    for (int i = 0; i < 32; i++) {
        state ^= i<<4;
        rand();
        rayQueryEXT rayQuery3;
        trace(rayQuery3, hitPos, randomDirection(quad.normal.xyz), 0.5);
        float dist = rayQueryGetIntersectionTEXT(rayQuery3, true);
        if (dist<0.49) {
            ao += (0.5-dist)*5/32.0;
        }
    }
    color *= 1.0/ao;
    //color = quad.normal/2+0.5;

    //color = vec4(((r>>0)&15)/15.0, ((r>>4)&15)/15.0,((r>>8)&15)/15.0,1)*((d>500)?1:0.25);
}
