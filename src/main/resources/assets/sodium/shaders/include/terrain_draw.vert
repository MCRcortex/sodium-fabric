#extension GL_ARB_shader_draw_parameters : require

struct ModelTransform {
    // Translation of the model in world-space
    vec3 translation;
};
#ifdef SSBO_MODEL_TRANSFORM
layout(std140, binding = 1) restrict readonly buffer ModelTransforms {
    ModelTransform[] transforms;
};
#else
layout(std140, binding = 1) uniform ModelTransforms {
    ModelTransform transforms[MAX_BATCH_SIZE];
};
#endif

vec3 _apply_view_transform(vec3 position) {
    #ifdef BASE_INSTANCE_INDEX
    ModelTransform transform = transforms[gl_BaseInstanceARB];
    #else
    ModelTransform transform = transforms[gl_DrawIDARB];
    #endif
    return transform.translation + position;
}