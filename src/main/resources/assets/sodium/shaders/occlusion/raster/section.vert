#version 450 core

#import <sodium:occlusion/datatypes.h>
#import <sodium:occlusion/scene_data.glsl>


layout(std430, binding = 1) restrict readonly buffer SectionMetaData {
    SectionMeta sections[];
};
#define SECTION sections[gl_InstanceID]

vec4 getBoxCorner(int corner) {
    return vec4(SECTION.bb.offset.xyz + vec3((corner&1), ((corner>>2)&1), ((corner>>1)&1))*SECTION.bb.size.xyz, 1);
}


flat out uint ID;
void main() {
    ID = gl_InstanceID;
    if (SECTION.id != gl_InstanceID) {
        gl_Position = vec4(-2,-2,-2,1);
        return;
    }
    gl_Position = (MVP*getBoxCorner(gl_VertexID));
    gl_Position.z -= 0.005;//Bias the depth to be closer to the camera, this is to reduce flicker
}