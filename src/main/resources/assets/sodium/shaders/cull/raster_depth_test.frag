#version 460 core

#import <sodium:cull/datatypes.h>

layout(early_fragment_tests) in;
layout(std430, binding = 2) restrict writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

layout(std430, binding = 0) restrict readonly buffer SceneData {
    mat4 mat_modelviewproj;
    Vec3F negativeCameraPosRegionRelative;
    uint maxtrans;
    uint frameid;
};

flat in uint ID;
void main() {
    visiblity[ID] = frameid;
}