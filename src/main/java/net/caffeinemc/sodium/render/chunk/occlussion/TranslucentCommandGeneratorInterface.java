package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.shader.BufferBlock;
import net.caffeinemc.gfx.api.shader.BufferBlockType;
import net.caffeinemc.gfx.api.shader.ShaderBindingContext;

public class TranslucentCommandGeneratorInterface {
    public final BufferBlock meta;
    public final BufferBlock counter;
    public final BufferBlock transSort;
    public final BufferBlock command;
    public final BufferBlock id2inst;

    public TranslucentCommandGeneratorInterface(ShaderBindingContext context) {
        meta = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
        counter = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
        transSort = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
        command = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
        id2inst = context.bindBufferBlock(BufferBlockType.STORAGE, 5);
    }
}
