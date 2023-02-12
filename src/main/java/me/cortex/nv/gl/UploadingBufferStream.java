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
    private long currentAddr;

    private long baseAddr;

    private boolean batching = false;
    private final List<Batch> batchedCopies = new LinkedList<>();
    private record Batch(int dest, long destOffset, long sourceOffset, long size) { }

    //Tells stream to queue up all the uploads and flush everything at once
    public void beginBatched() {
        if (batching) {
            throw new IllegalStateException("Already batching");
        }
        batching = true;
    }

    //Commits all the uploaded data and copied them to the respective buffers
    //TODO: Should flush then do GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT and then
    // GL_BUFFER_UPDATE_BARRIER_BIT after all glCopyNamedBufferSubData has been committed
    public void endBatched(boolean barrier) {
        if (!batching) {
            throw new IllegalStateException("Not batching");
        }
        batching = false;



    }

    public ByteBuffer getUpload(int size) {


    }

    //Uploads the last requested buffer into the destination
    public void upload(int destBuffer, long destOffset) {

    }

    public void delete() {

    }

    private void tick() {

    }

    public UploadingBufferStream(int frames) {
    }

    //NOTE: THIS MUST BE TICKED ELSE IT DOES NOT WORK
    public static void TickAllUploadingStreams() {

    }
}
