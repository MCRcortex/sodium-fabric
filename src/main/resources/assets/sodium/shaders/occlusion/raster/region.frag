#version 450 core
layout(early_fragment_tests) in;

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>


layout(std430, binding = 3) restrict writeonly buffer VisibilityBuffer {
    uint visibility[];
};
/*
layout(std430, binding = 1) restrict readonly buffer RegionArrayData {
    int[] regionList;
};
out vec4 diffuseColor;
*/
flat in uint ID;
void main() {
    visibility[ID] = frameId;
    //diffuseColor.xyz = vec3(float(regionList[ID]%7)/7,float(regionList[ID]%13)/13,float(regionList[ID]%5)/5);
}