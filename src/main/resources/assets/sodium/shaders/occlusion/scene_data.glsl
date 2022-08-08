layout(std140, binding = 0) uniform SceneData {
    mat4 MVP;
    mat4 MV;
    vec4 camera;
    uint frameId;
    uint regionCount;
};