package me.cortex.nv.gl.buffers;

public interface IClientMappedBuffer extends Buffer {
    long clientAddress();
}
