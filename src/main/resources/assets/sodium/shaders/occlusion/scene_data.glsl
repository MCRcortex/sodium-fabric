layout(std430, binding = 0) restrict readonly buffer SceneData {
    mat4 MVP;
    mat4 MV;
    vec4 camera;
    uint frameId;
    uint regionCount;
};