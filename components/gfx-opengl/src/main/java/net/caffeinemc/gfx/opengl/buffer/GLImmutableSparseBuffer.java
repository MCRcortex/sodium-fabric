package net.caffeinemc.gfx.opengl.buffer;

import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;
import net.caffeinemc.gfx.api.buffer.ImmutableSparseBuffer;

import java.util.Set;

public class GLImmutableSparseBuffer extends GlImmutableBuffer implements ImmutableSparseBuffer {
    public GLImmutableSparseBuffer(int handle, long capacity, Set<ImmutableBufferFlags> flags) {
        super(handle, capacity, flags);
    }
}
