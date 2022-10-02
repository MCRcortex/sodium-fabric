package net.caffeinemc.sodium.render.chunk.region;


import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlImmutableBuffer;
import net.caffeinemc.gfx.util.buffer.BufferPool;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.AsyncArenaBuffer;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkGlBuffer;

import java.util.Set;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.vulkan.VK10.*;

public class GlobalSingleBufferProvider implements IVertexBufferProvider {
    private final RenderDevice device;
    private final StreamingBuffer stagingBuffer;
    private final TerrainVertexType vertexType;
    private final BufferPool<ImmutableBuffer> bufferPool;
    private final ArenaBuffer buffer;

    public static class GlVkImmutableBuffer extends GlImmutableBuffer {
        public SVkGlBuffer buffer;
        public GlVkImmutableBuffer(SVkGlBuffer buffer, long capacity, Set<ImmutableBufferFlags> flags) {
            super(buffer.glId, capacity, flags);
            this.buffer = buffer;
        }
    }
    public GlobalSingleBufferProvider(RenderDevice device, StreamingBuffer stagingBuffer, TerrainVertexType vertexType) {
        this.device = device;
        this.stagingBuffer = stagingBuffer;
        this.vertexType = vertexType;
        this.bufferPool = new BufferPool<>(
                device,
                RenderRegionManager.PRUNE_SAMPLE_SIZE,
                c -> {
                    SVkGlBuffer buffer = SVkDevice.INSTANCE.m_alloc_e.createVkGlBuffer(c,
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                            VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, 1);
                    return new GlVkImmutableBuffer(buffer, c, Set.of());
                }
        );
        buffer = new AsyncArenaBuffer(
                device,
                stagingBuffer,
                bufferPool,
                RenderRegion.REGION_SIZE * 756 * 10, // 756 is the average-ish amount of vertices in a section
                .25f, // add 25% each resize
                vertexType.getBufferVertexFormat().stride()
        );
    }

    @Override
    public ArenaBuffer provide() {
        return buffer;
    }

    @Override
    public void remove(ArenaBuffer vertexBuffer) {
        //vertexBuffer.delete();
    }

    @Override
    public void destroy() {
        buffer.delete();
        bufferPool.delete();
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return buffer.getDeviceAllocatedMemory() + bufferPool.getDeviceAllocatedMemory();
    }

    @Override
    public long getDeviceUsedMemory() {
        return buffer.getDeviceUsedMemory();
    }

    @Override
    public int getDeviceBufferObjects() {
        return bufferPool.getDeviceBufferObjects() + 1;
    }

    @Override
    public void prune(float prunePercentModifier) {
        bufferPool.prune(prunePercentModifier);
    }

    @Override
    public String getName() {
        return "Single";
    }
}

