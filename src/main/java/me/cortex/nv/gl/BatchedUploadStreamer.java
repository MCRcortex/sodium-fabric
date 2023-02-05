package me.cortex.nv.gl;

public class BatchedUploadStreamer {

    public void commit() {
        //TODO: Should flush then do GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT and then
        // GL_BUFFER_UPDATE_BARRIER_BIT after all glCopyNamedBufferSubData has been committed
    }
}
