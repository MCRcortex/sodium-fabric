#version 460 core

#import <sodium:cull/datatypes.h>

layout(std430, binding = 2) writeonly buffer VisibilityBuffer {
    uint visiblity[];
};

flat in uint ID;
void main() {
    visiblity[ID] = 1;
}