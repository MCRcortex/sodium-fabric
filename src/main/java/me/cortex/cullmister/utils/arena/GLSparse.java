package me.cortex.cullmister.utils.arena;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import static org.lwjgl.opengl.ARBSparseBuffer.*;
import static org.lwjgl.opengl.ARBSparseBuffer.glNamedBufferPageCommitmentARB;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45.glCreateBuffers;
import static org.lwjgl.opengl.GL45.glNamedBufferStorage;
//TODO: MAKE ALOT FASTER
// NOTE: can do this by having maybe a treemap of free ranges based on size
//  then can seriously optimize everything
public class GLSparse {
    public static final int SPARSE_PAGE_SIZE = glGetInteger(GL_SPARSE_BUFFER_PAGE_SIZE_ARB);
    public int id;
    public final long maxSize;
    long currentlyUsed = 0;
    GLSparseRange head;
    Long2IntOpenHashMap committedCount = new Long2IntOpenHashMap();
    public GLSparse() {
        //Just create it with 2GB of uncommited vram
        this(2000000000L);
    }

    public GLSparse(long baseSize) {
        id = glCreateBuffers();
        this.maxSize = SPARSE_PAGE_SIZE*(baseSize/SPARSE_PAGE_SIZE + 1);
        glNamedBufferStorage(id, maxSize,GL_SPARSE_STORAGE_BIT_ARB|GL_DYNAMIC_STORAGE_BIT);
        int e = glGetError();
        if (e != 0) {
            System.err.println("GL_ERROR: "+ e);
        }
        head = new GLSparseRange(null, null, 0, this.maxSize, false);
    }

    private void commitRange(GLSparseRange range) {
        for (long i = range.offset/SPARSE_PAGE_SIZE; i <= (range.offset+range.size)/SPARSE_PAGE_SIZE; i++) {
            if (!committedCount.containsKey(i)) {
                committedCount.put(i, 1);
                glNamedBufferPageCommitmentARB(id, i*SPARSE_PAGE_SIZE, SPARSE_PAGE_SIZE, true);
                int e = glGetError();
                if (e != 0) {
                    System.err.println("GL_ERROR: "+ e);
                }
            } else {
                committedCount.put(i, committedCount.get(i) + 1);
            }
        }

    }

    private void uncommitRange(GLSparseRange range) {
        for (long i = range.offset/SPARSE_PAGE_SIZE; i <= (range.offset+range.size)/SPARSE_PAGE_SIZE; i++) {
            if (!committedCount.containsKey(i))
                throw new IllegalStateException();
            int newCount = committedCount.get(i)- 1;
            if (newCount == 0) {
                glNamedBufferPageCommitmentARB(id, i*SPARSE_PAGE_SIZE, SPARSE_PAGE_SIZE, false);
                committedCount.remove(i);
            } else {
                committedCount.put(i, newCount);
            }
        }
    }

    public GLSparseRange alloc(long size) {
        GLSparseRange ideal = null;
        for (GLSparseRange current = head; current != null; current = current.next) {
            if (current.committed)
                continue;
            if (current.size < size)
                continue;
            if (current.size == size) {
                ideal = current;
                break;
            }
            if (ideal == null)
                ideal = current;
            if (current.size < ideal.size) {
                ideal = current;
            }
        }

        if (ideal == null)
            throw new IllegalStateException("Unable to allocate memory");

        long excess = ideal.size - size;
        if (excess == 0) {
            //Found a perfect fit, just commit and return
            ideal.committed = true;
            currentlyUsed += size;
            commitRange(ideal);
            return ideal;
        }

        GLSparseRange section = new GLSparseRange(ideal.prev, ideal, ideal.offset, size, true);
        currentlyUsed += size;
        commitRange(section);
        //Update free zone
        //Link
        if (ideal.prev == null) {
            head = section;
        } else {
            ideal.prev.next = section;
        }
        ideal.prev = section;

        //Update offset
        ideal.offset += size;
        ideal.size -= size;
        return section;
    }

    public void free(GLSparseRange section) {
        if (!section.committed)
            throw new IllegalArgumentException("Section already freed");

        //Dealloc memory
        uncommitRange(section);
        section.committed = false;
        currentlyUsed -= section.size;

        //Modify the link to merge sectors

        //Merge previous block
        if (section.prev!=null&&!section.prev.committed) {
            section.prev.size += section.size;
            section.prev.next = section.next;
            if (section.next != null) {
                section.next.prev = section.prev;
            }
            section = section.prev;
        }

        //Merge next block
        if (section.next!=null&&!section.next.committed) {
            section.size += section.next.size;
            section.next = section.next.next;
            if (section.next != null) {
                section.next.prev = section;
            }
        }
    }


    public void verify() {
        GLSparseRange node = head;
        GLSparseRange ln = null;
        long coff = 0;
        while (node != null) {
            if (coff != node.offset)
                throw new IllegalStateException();
            coff += node.size;
            ln = node;
            node = node.next;

        }
        if (coff != maxSize) {
            throw new IllegalStateException();
        }

        node = ln;
        while (node != null) {
            if (coff != (node.offset+node.size))
                throw new IllegalStateException();
            coff -= node.size;
            node = node.prev;
        }
        if (coff != 0) {
            throw new IllegalStateException();
        }
    }

    public void delete() {
        glDeleteBuffers(id);
    }
}
