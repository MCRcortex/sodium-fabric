layout(std140, binding = 0) uniform SceneData {
    mat4 MVP;
    mat4 MV;
    vec4 camera;
    ivec4 cameraSection;
    uint frameId;
    uint regionCount;
};