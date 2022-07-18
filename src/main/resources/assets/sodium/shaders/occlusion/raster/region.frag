#version 450 core
layout(early_fragment_tests) in;

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>

layout(std430, binding = 3) restrict writeonly buffer VisibilityBuffer {
    uint visibility[];
};

flat in uint ID;
void main() {
    visibility[ID] = frameId;
}