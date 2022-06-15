//FIXME: replace with a #define computed at runtime
const uint MAX_BATCH_SIZE = 16 * 8 * 16;

struct ModelTransform {
    // Translation of the model in world-space
    float x;
    float y;
    float z;
};

layout(std430, binding = 1) restrict readonly buffer ModelTransforms {
    ModelTransform transforms[];
};

vec3 _apply_view_transform(vec3 position) {
    ModelTransform transform = transforms[gl_BaseInstance];
    return vec3(transform.x, transform.y, transform.z) + position;
}