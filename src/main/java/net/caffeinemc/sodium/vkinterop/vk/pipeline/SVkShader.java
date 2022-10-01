package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.vkinterop.ShaderUtils;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

//Basicly all the info to build a VkPipelineShaderStageCreateInfo
public class SVkShader {
    private ShaderUtils.SPIRV compiled;
    private long module;
    private SVkDevice device;
    private int stage;
    private ByteBuffer entry;

    private static int type2vk(ShaderType type) {
        if (type == ShaderType.VERTEX) {
            return VK_SHADER_STAGE_VERTEX_BIT;
        } else if (type == ShaderType.FRAGMENT) {
            return VK_SHADER_STAGE_FRAGMENT_BIT;
        } else if (type == ShaderType.COMPUTE) {
            return VK_SHADER_STAGE_COMPUTE_BIT;
        } else {
            throw new IllegalStateException();
        }
    }

    public SVkShader(SVkDevice device, String source, ShaderType type) {
        this(device, source, "main", type, type2vk(type));
    }

    public SVkShader(SVkDevice device, String source, String entry, ShaderType type, int stage) {
        this.device = device;
        this.compiled = ShaderUtils.compileShader("shader", source, type);
        this.module = this.compiled.createShaderModule(device);
        this.entry = MemoryUtil.memUTF8(entry);
        this.stage = stage;
    }

    public void set(VkPipelineShaderStageCreateInfo shaderStage) {
        shaderStage
                .sType$Default()
                .module(module)
                .pName(entry)
                .stage(stage)
                .flags(0);
    }
}
