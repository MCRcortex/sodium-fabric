package net.caffeinemc.gfx.opengl.buffer;

import net.caffeinemc.gfx.api.buffer.DynamicBufferFlags;
import net.caffeinemc.gfx.api.buffer.DynamicSparseBuffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;
import net.caffeinemc.gfx.api.buffer.ImmutableSparseBuffer;

import java.util.Set;

public class GLDynamicSparseBuffer extends GlDynamicBuffer implements DynamicSparseBuffer {
    public GLDynamicSparseBuffer(int handle, long capacity, Set<DynamicBufferFlags> flags) {
        super(handle, capacity, flags);
    }
}
