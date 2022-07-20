package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.device.RenderDevice;

//TODO: maybe do via dispatch indirect or something that is set via the RasterSection compute shader
// simply add 1 to x dim and atomic max the y dim
public class CreateTerrainCommandsComputeShader {
    public CreateTerrainCommandsComputeShader(RenderDevice device) {

    }
}
