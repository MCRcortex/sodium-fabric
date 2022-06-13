#version 460 core

#import <sodium:cull/datatypes.h>

layout(std430, binding = 2) writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

flat in uint ID;
out vec4 colour;
void main() {
    visiblity[ID] = 1;
    colour.xyz = vec3(1,1,0);
}