#version 450 core

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>

//TODO: MAYBE MERGE regionList and the visibility array into a single buffer that is bitset
layout(std430, binding = 1) restrict readonly buffer RegionArrayData {
    int regionList[MAX_REGIONS];
};

#define REGION_ID regionList[gl_InstanceID]

layout(std430, binding = 2) restrict readonly buffer RegionMetaData {
    RegionMeta[] regions;
};

#define REGION regions[REGION_ID]

vec4 getBoxCorner(int corner) {
    return vec4(REGION.bb.offset.xyz + vec3((corner&1), ((corner>>2)&1), ((corner>>1)&1))*REGION.bb.size.xyz, 1);
}


flat out uint ID;
void main() {
    ID = gl_InstanceID;
    gl_Position = (MVP*getBoxCorner(gl_VertexID));
    gl_Position.z -= 0.0005;//Bias the depth to be closer to the camera, this is to reduce flicker
}