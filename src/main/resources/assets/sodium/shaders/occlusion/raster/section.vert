#version 450 core
#extension GL_ARB_shader_draw_parameters : enable
#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>


layout(std430, binding = 1) restrict readonly buffer SectionMetaData {
    SectionMeta sections[];
};

#define SECTION_ID (gl_InstanceID & REGION_SECTION_SIZE_MASK)

//NOTE: this is SO SO SO SO HACKY it uses gl_BaseVertex to pass in the section start of the render batch
//NOTE: can also make it as gl_VertexID/8 or gl_VertexID>>3
#define GLOBAL_SECTION_ID (SECTION_ID + gl_BaseVertexARB)

#define SECTION sections[GLOBAL_SECTION_ID]


vec4 getBoxCorner(int corner) {
    return vec4(SECTION.aabb.offset.xyz + vec3((corner&1), ((corner>>2)&1), ((corner>>1)&1))*SECTION.aabb.size.xyz, 1);
}


flat out uint ID;
void main() {
    ID = gl_InstanceID;
    if (SECTION.id != SECTION_ID) {
        gl_Position = vec4(-2,-2,-2,1);
        return;
    }
    gl_Position = (MVP*getBoxCorner(gl_VertexID));
    gl_Position.z -= 0.0005;//Bias the depth to be closer to the camera, this is to reduce flicker
}