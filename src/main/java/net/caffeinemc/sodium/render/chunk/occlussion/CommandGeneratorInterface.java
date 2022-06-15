package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.shader.BufferBlock;
import net.caffeinemc.gfx.api.shader.BufferBlockType;
import net.caffeinemc.gfx.api.shader.ShaderBindingContext;

public class CommandGeneratorInterface {
    public final BufferBlock scene;
    public final BufferBlock meta;
    public final BufferBlock visbuff;

    public final BufferBlock counter;

    public final BufferBlock instancedata;
    public final BufferBlock[] cmdbuffs = new BufferBlock[4];

    public CommandGeneratorInterface(ShaderBindingContext context) {
        scene = context.bindBufferBlock(BufferBlockType.UNIFORM, 0);
        meta = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
        visbuff = context.bindBufferBlock(BufferBlockType.STORAGE, 2);

        counter = context.bindBufferBlock(BufferBlockType.STORAGE, 3);

        instancedata = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
        for (int i = 0; i < 4; i++) {
            cmdbuffs[i] = context.bindBufferBlock(BufferBlockType.STORAGE, 5+i);
        }
    }
}
