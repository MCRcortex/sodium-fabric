#version 450 core
layout(early_fragment_tests) in;

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>

layout(std430, binding = 2) restrict writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

flat in uint ID;
void main() {
    visiblity[ID] = frameId;
}