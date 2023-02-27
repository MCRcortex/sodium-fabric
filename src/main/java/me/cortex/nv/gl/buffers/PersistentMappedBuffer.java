package me.cortex.nv.gl.buffers;

public class PersistentMappedBuffer implements IClientMappedBuffer {

    @Override
    public long clientAddress() {
        return 0;
    }

    @Override
    public void delete() {

    }
}
