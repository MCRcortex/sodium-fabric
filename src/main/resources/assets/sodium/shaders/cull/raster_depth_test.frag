#version 460 core

#import <sodium:cull/datatypes.h>

layout(early_fragment_tests) in;
layout(std430, binding = 2) restrict writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

/*
layout(std430, binding = 3) restrict writeonly buffer ComputeDispatchBuffer {
    uint num_groups_x;
    uint num_groups_y;
    uint num_groups_z;
};*/

flat in uint ID;
//out vec4 colour;
void main() {
    visiblity[ID] = 1;
    //num_groups_y = 1;
    //colour.xyz = vec3(1,1,0);
}