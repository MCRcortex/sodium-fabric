package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.shader.BufferBlock;
import net.caffeinemc.gfx.api.shader.BufferBlockType;
import net.caffeinemc.gfx.api.shader.ShaderBindingContext;

public class RasterCullerInterface {
    public final BufferBlock scene;
    public final BufferBlock meta;
    public final BufferBlock visbuff;

    public RasterCullerInterface(ShaderBindingContext context) {
        scene = context.bindBufferBlock(BufferBlockType.UNIFORM, 0);
        meta = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
        visbuff = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
    }
}
