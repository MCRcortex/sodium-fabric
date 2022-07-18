#version 450 core

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>


layout(std430, binding = 1) restrict readonly buffer RegionArrayData {
    int[] regionLUT;
};
#define REGION_ID regionLUT[gl_InstanceID]

layout(std430, binding = 2) restrict readonly buffer RegionMetaData {
    RegionMeta[] regions;
};
#define REGION regions[gl_InstanceID]

vec4 getBoxCorner(int corner) {
    return vec4(REGION.bb.offset.xyz + vec3((corner&1), ((corner>>2)&1), ((corner>>1)&1))*REGION.bb.size.xyz, 1);
}


flat out uint ID;
void main() {
    ID = gl_InstanceID;
    gl_Position = (MVP*getBoxCorner(REGION_ID));
    gl_Position.z -= 0.005;//Bias the depth to be closer to the camera, this is to reduce flicker
}