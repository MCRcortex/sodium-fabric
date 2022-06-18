package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.shader.BufferBlock;
import net.caffeinemc.gfx.api.shader.BufferBlockType;
import net.caffeinemc.gfx.api.shader.ShaderBindingContext;

public class CommandGeneratorInterface {
    public final BufferBlock scene;
    public final BufferBlock meta;
    public final BufferBlock visbuff;

    public final BufferBlock cpuvisbuff;

    public final BufferBlock counter;

    public final BufferBlock instancedata;
    public final BufferBlock[] cmdbuffs = new BufferBlock[3];
    public final BufferBlock transSort;

    public CommandGeneratorInterface(ShaderBindingContext context) {
        scene = context.bindBufferBlock(BufferBlockType.STORAGE, 0);

        meta = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
        visbuff = context.bindBufferBlock(BufferBlockType.STORAGE, 2);

        cpuvisbuff = context.bindBufferBlock(BufferBlockType.STORAGE, 3);

        counter = context.bindBufferBlock(BufferBlockType.STORAGE, 4);

        instancedata = context.bindBufferBlock(BufferBlockType.STORAGE, 5);
        for (int i = 0; i < 3; i++) {
            cmdbuffs[i] = context.bindBufferBlock(BufferBlockType.STORAGE, 6+i);
        }

        transSort = context.bindBufferBlock(BufferBlockType.STORAGE, 9);
    }
}
