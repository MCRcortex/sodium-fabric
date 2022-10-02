package net.caffeinemc.sodium.render.chunk.draw;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class IndexBufferVK {
    SVkBuffer indexBuffer;
    public IndexBufferVK(int quadCount) {
        ByteBuffer buffer = genQuadIdxs(quadCount*4);
        indexBuffer = SVkDevice.INSTANCE.m_alloc.createBuffer(buffer.capacity(),
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                4,
                MemoryUtil.memAddress(buffer));
    }

    public static ByteBuffer genQuadIdxs(int vertexCount) {
        //short[] idxs = {0, 1, 2, 0, 2, 3};

        int indexCount = vertexCount * 3 / 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Integer.BYTES);
        IntBuffer idxs = buffer.asIntBuffer();
        //short[] idxs = new short[indexCount];

        int j = 0;
        for(int i = 0; i < vertexCount; i += 4) {

            idxs.put(j, i);
            idxs.put(j + 1, (i + 1));
            idxs.put(j + 2, (i + 2));
            idxs.put(j + 3, (i));
            idxs.put(j + 4, (i + 2));
            idxs.put(j + 5, (i + 3));

            j += 6;
        }

        return buffer;
    }
}
