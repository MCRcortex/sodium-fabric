#version 460 core

#import <sodium:cull/datatypes.h>

vec3 base;
vec3 size;
vec4 getBoxCorner(int corner) {
    return vec4(base + vec3((corner&1), ((corner>>2)&1), ((corner>>1)&1))*size, 1);
}

layout(std140, binding = 0) uniform SceneData {
    mat4 mat_modelviewproj;
    Vec3F negativeCameraPosRegionRelative;
};

layout(std430, binding = 1) readonly buffer MetaData {
    SectionMeta sections[];
};
#define SECTION sections[gl_InstanceID]

//TODO: Could technically inject extra culling in from the frustum
// culling cpu side and map that to like bit 0 in this, that way can easily early exit saving extra time
// unsure if this is worth the extra complexity on the host tho

//TODO: could also do extra preculling by not rendering the faces that dont have any vertex data for the entire subchunk
// (note tho if the OTHER layer bit is set must draw all sides)
//TODO: add backplane culling to all the chunks in the region, e.g. use the region to determin the face visibility of all chunks within
// then only render those
//TODO: furthur optimize by not rendering a face or the entire thing if a/the sections faces is covered or encased by another sections face
flat out uint ID;
void main() {
    ID = gl_InstanceID;
    if (SECTION.id != gl_InstanceID) {
        gl_Position = vec4(-2,-2,-2,1);
        return;
    }
    base = Vec3FtoVec3(SECTION.bboxOffset);
    size = Vec3FtoVec3(SECTION.bboxSize);
    gl_Position = (mat_modelviewproj*getBoxCorner(gl_VertexID));
    gl_Position.z -= 0.0015;//Bias the depth to be closer to the camera, this is to reduce flicker
}