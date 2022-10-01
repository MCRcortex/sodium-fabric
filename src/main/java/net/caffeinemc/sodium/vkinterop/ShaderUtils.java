package net.caffeinemc.sodium.vkinterop;

import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NativeResource;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK10.*;

public class ShaderUtils {

    public static SPIRV compileShader(String filename, String source, ShaderType type) {
        return compileShader(filename, source, ShaderKind.values()[type.ordinal()]);
    }
    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {

        long compiler = shaderc_compiler_initialize();

        if(compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        long result = shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", NULL);

        if(result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + "into SPIR-V:\n " + shaderc_result_get_error_message(result));
        }

        shaderc_compiler_release(compiler);

        return new SPIRV(result, shaderc_result_get_bytes(result));
    }

    public enum ShaderKind {
        VERTEX_SHADER(shaderc_glsl_vertex_shader),
        FRAGMENT_SHADER(shaderc_glsl_fragment_shader),
        GEOMETRY_SHADER(shaderc_glsl_geometry_shader),
        COMPUTE_SHADER(shaderc_glsl_compute_shader),
        TESSELLATION_CONTROL_SHADER(shaderc_glsl_tess_control_shader),
        TESSELLATION_EVALUATION_SHADER(shaderc_glsl_tess_evaluation_shader);

        private final int kind;

        ShaderKind(int kind) {
            this.kind = kind;
        }
    }

    public static final class SPIRV implements NativeResource {

        private final long handle;
        private ByteBuffer bytecode;

        public SPIRV(long handle, ByteBuffer bytecode) {
            this.handle = handle;
            this.bytecode = bytecode;
        }

        public ByteBuffer bytecode() {
            return bytecode;
        }

        @Override
        public void free() {
            shaderc_result_release(handle);
            bytecode = null; // Help the GC
        }

        public long createShaderModule(SVkDevice device) {
            return ShaderUtils.createShaderModule(bytecode, device);
        }
    }


    public static long createShaderModule(ByteBuffer spirvCode, SVkDevice device) {

        try(MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if(vkCreateShaderModule(device.device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }
}
