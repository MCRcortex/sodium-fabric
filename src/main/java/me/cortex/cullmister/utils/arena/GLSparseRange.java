package me.cortex.cullmister.utils.arena;

public class GLSparseRange {
    GLSparseRange prev;
    GLSparseRange next;
    boolean committed;
    public long offset;
    public long size;

    GLSparseRange(GLSparseRange prev, GLSparseRange next, long offset, long size, boolean committed) {
        this.prev = prev;
        this.next = next;
        this.offset = offset;
        this.size = size;
        this.committed = committed;
    }
}
