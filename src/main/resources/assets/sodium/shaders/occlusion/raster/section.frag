#version 450 core
layout(early_fragment_tests) in;

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>

layout(std430, binding = 2) restrict writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

flat in uint ID;
//out vec4 diffuseColor;
void main() {
    //diffuseColor.xyz = vec3(float(ID%7)/7,float(ID%13)/13,float(ID%5)/5);
    visiblity[ID] = frameId;
}