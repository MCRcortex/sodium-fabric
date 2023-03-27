#version 460 core
#extension GL_NV_gpu_shader5 : enable

layout(binding = 0) uniform sampler2D inDepth;
layout(pixel_center_integer) in vec4 gl_FragCoord;
void main() {
    vec4 sameple = textureGather(inDepth, gl_FragCoord.xy);
    float localMin = min(min(sameple.x, sameple.y), min(sameple.z, sameple.w));

    gl_FragDepth = localMin;
}