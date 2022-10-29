package me.cortex.vulkanitelib.utils;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.NVRayTracing.*;
import static org.lwjgl.vulkan.VK10.*;

public class ShaderUtils {
    private static int vulkanStageToShadercKind(int stage) {
        switch (stage) {
            case VK_SHADER_STAGE_VERTEX_BIT:
                return shaderc_vertex_shader;
            case VK_SHADER_STAGE_FRAGMENT_BIT:
                return shaderc_fragment_shader;
            case VK_SHADER_STAGE_RAYGEN_BIT_NV:
                return shaderc_raygen_shader;
            case VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV:
                return shaderc_closesthit_shader;
            case VK_SHADER_STAGE_MISS_BIT_NV:
                return shaderc_miss_shader;
            case VK_SHADER_STAGE_ANY_HIT_BIT_NV:
                return shaderc_anyhit_shader;
            case VK_SHADER_STAGE_INTERSECTION_BIT_NV:
                return shaderc_intersection_shader;
            case VK_SHADER_STAGE_COMPUTE_BIT:
                return shaderc_compute_shader;
            default:
                throw new IllegalArgumentException("Stage: " + stage);
        }
    }

    //NOTE: MALLOCS THE SHADER BYTECODE MEMEORY SO MUST FREE
    public static ByteBuffer compileShader(String filename, String source, int vulkanStage) {
       long compiler = shaderc_compiler_initialize();
        if (compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }
        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
        shaderc_compile_options_set_target_spirv(options, shaderc_spirv_version_1_4);
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        long result = shaderc_compile_into_spv(compiler, source, vulkanStageToShadercKind(vulkanStage), filename, "main", options);

        if (result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + "into SPIR-V:\n " + shaderc_result_get_error_message(result));
        }
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
        ByteBuffer code = shaderc_result_get_bytes(result);
        return MemoryUtil.memAlloc(code.capacity()).put(code).rewind();
    }
}
