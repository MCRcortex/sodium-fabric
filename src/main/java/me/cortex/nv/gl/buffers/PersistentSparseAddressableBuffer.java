package me.cortex.nv.gl.buffers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.nv.gl.GlObject;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage;
import static org.lwjgl.opengl.ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB;
import static org.lwjgl.opengl.ARBSparseBuffer.glNamedBufferPageCommitmentARB;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;

public class PersistentSparseAddressableBuffer extends GlObject implements IDeviceMappedBuffer {
    private static long alignUp(long number, long alignment) {
        long delta = number % alignment;
        return delta == 0?number: number + (alignment - delta);
    }

    public final long addr;
    public final long size;
    private final long PAGE_SIZE = 1<<16;
    public PersistentSparseAddressableBuffer(long size) {
        super(glCreateBuffers());
        this.size = alignUp(size, PAGE_SIZE);
        glNamedBufferStorage(id, size, GL_SPARSE_STORAGE_BIT_ARB);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_WRITE);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
    }


    private final Int2IntOpenHashMap allocationCount = new Int2IntOpenHashMap();
    private void allocatePages(int page, int pageCount) {
        glNamedBufferPageCommitmentARB(id, PAGE_SIZE * page, PAGE_SIZE * pageCount, true);
        for (int i = 0; i < pageCount; i++) {
            allocationCount.put(i+page, allocationCount.getOrDefault(i+page, 0)+1);
        }
    }
    private void deallocatePages(int page, int pageCount) {
        for (int i = 0; i < pageCount; i++) {
            int newCount = allocationCount.get(i+page) - 1;
            if (newCount != 0) {
                allocationCount.put(i+page, newCount);
            } else {
                allocationCount.remove(i+page);
                glNamedBufferPageCommitmentARB(id, PAGE_SIZE * page, PAGE_SIZE,false);
            }
        }
    }


    //Auto alloc and dealloc
    public long malloc(long size, long alignment) {
        return 0;
    }

    public void free(long location) {

    }

    @Override
    public long getDeviceAddress() {
        return addr;
    }

    public void delete() {
        glMakeNamedBufferNonResidentNV(id);
        glDeleteBuffers(id);
    }
}