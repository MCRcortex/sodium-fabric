package me.cortex.nv.gl;

import org.lwjgl.system.MemoryUtil;

import java.lang.ref.PhantomReference;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class UploadingBufferStream {
    private final SegmentedManager segments = new SegmentedManager();
    private long baseAddr;

    private long currentAddr;//Used to try and expand an allocation
    private long currentOffset;

    private ByteBuffer buffer;

    private final List<Batch> batchedCopies = new LinkedList<>();
    private record Batch(int dest, long destOffset, long sourceOffset, long size) { }


    public UploadingBufferStream(int frames) {

    }

    public ByteBuffer getUpload(int size) {
        long addr = -1;
        if (currentAddr == -1) {
            currentAddr = segments.alloc(size);
            currentOffset = size;
        } else {
            if (segments.expand(currentAddr, size)) {
                addr = currentAddr + currentOffset;
                currentOffset += size;
            } else {
                //TODO: need to record currentAddr with the current frame so that it can be freed
            }
        }


        return buffer;
    }

    //Uploads the last requested buffer into the destination
    public void upload(int destBuffer, long destOffset) {
        batchedCopies.add(new Batch(destBuffer, destOffset, ));

        buffer = null;
    }

    public void commit() {

    }

    public void delete() {

    }

    private void tick() {

    }

    //NOTE: THIS MUST BE TICKED ELSE IT DOES NOT WORK
    public static void TickAllUploadingStreams() {

    }
}
