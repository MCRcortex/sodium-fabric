package me.cortex.nv.gl;

import org.lwjgl.system.MemoryUtil;

import java.lang.ref.PhantomReference;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class UploadingBufferStream {
    private long basePtr;
    private long size;
    private long offset;

    public UploadingBufferStream(int frames) {
    }

    public void delete() {

    }

    private void tick() {

    }

    private boolean checkIsFree(long offset, long size) {

    }

    public ByteBuffer getUpload(int size) {
        if ((offset+size)>this.size) {//Need to loop around to either basePtr or grow uploading stream
            if (checkIsFree(0, size)) {

            }
        } else {
            if (!checkIsFree(offset, size)) {
                //need to resize buffer
            }
            long ptr = offset + basePtr;
        }
    }

    /*
    //Returns a buffer that may be used for uploading
    public ByteBuffer getUpload(int size) {
        if (size > remainingSize) {
            //TODO: need to do resize
        }
        ByteBuffer buffer = MemoryUtil.memByteBuffer(currentPtr, size);
        currentPtr += size;
        remainingSize -= size;
        return buffer;
    }*/

    //Uploads the last requested buffer into the destination
    public void upload(int destBuffer, long destOffset) {
        /*
        ByteBuffer source = uploadContents;
        uploadContents = null;
        source.rewind();
        glFlushMappedNamedBufferRange(glId, MemoryUtil.memAddress(source), source.capacity());
        glCopyNamedBufferSubData(glId, destBuffer, MemoryUtil.memAddress(source) - mappedPtr, destOffset, source.capacity());
         */
    }


    //NOTE: THIS MUST BE TICKED ELSE IT DOES NOT WORK
    public static void TickAllUploadingStreams() {

    }
}
