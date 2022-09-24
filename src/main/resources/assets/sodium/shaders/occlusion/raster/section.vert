#version 450 core
#extension GL_ARB_shader_draw_parameters : require
#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>


layout(std430, binding = 1) restrict readonly buffer SectionMetaData {
    SectionMeta sections[];
};

#define SECTION_ID (gl_InstanceID)

//NOTE: this is SO SO SO SO HACKY it uses gl_BaseVertex to pass in the section start of the render batch
//NOTE: can also make it as gl_VertexID/8 or gl_VertexID>>3
#define GLOBAL_SECTION_ID (SECTION_ID + (gl_BaseVertexARB))

#define SECTION sections[GLOBAL_SECTION_ID]


vec4 getBoxCorner(int corner) {
    return fma(vec4((corner&1), ((corner>>2)&1), ((corner>>1)&1), 0), SECTION.aabb.size, SECTION.aabb.offset);
}


layout(std430, binding = 2) restrict writeonly buffer VisibilityBuffer {
    uint visiblity[];
};
flat out uint ID;
void main() {
    ID = gl_InstanceID + gl_BaseInstanceARB;
    /*
    //TODO: only maybe do this
    if (SECTION.id != SECTION_ID) {
        gl_Position = vec4(-2,-2,-2,1);
        return;
    }*/


    gl_Position = (MVP*getBoxCorner(gl_VertexID));
    gl_Position.z -= 0.0005;//Bias the depth to be closer to the camera, this is to reduce flicker
}