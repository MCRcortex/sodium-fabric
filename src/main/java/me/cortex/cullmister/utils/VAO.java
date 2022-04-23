package me.cortex.cullmister.utils;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;

public class VAO implements IBindable {
    private static VAO current_bound;

    public int id;
    int[] attribute_arrays = new int[0];
    public VAO() {
        id = glGenVertexArrays();
    }

    public void bind() {
        if (current_bound != null) {
            throw new IllegalStateException("Vertex Attribute array already bound");
        }
        glBindVertexArray(id);
        //enableVertexAttribs();
        current_bound = this;
    }

    public void enableVertexAttribs() {
        for (int attr : attribute_arrays) {
            glEnableVertexAttribArray(attr);
        }
    }

    public void disableVertexAttribs() {
        for (int attr : attribute_arrays) {
            glDisableVertexAttribArray(attr);
        }
    }

    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer)  {
        GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
        int[] newIdxs = new int[attribute_arrays.length+1];
        System.arraycopy(attribute_arrays, 0, newIdxs, 0, attribute_arrays.length);
        newIdxs[newIdxs.length - 1] = index;
        attribute_arrays = newIdxs;
    }

    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer)  {
        GL20.glEnableVertexAttribArray(index);
        GL40.glVertexAttribIPointer(index, size, type, stride, pointer);
        int[] newIdxs = new int[attribute_arrays.length+1];
        System.arraycopy(attribute_arrays, 0, newIdxs, 0, attribute_arrays.length);
        newIdxs[newIdxs.length - 1] = index;
        attribute_arrays = newIdxs;
    }

    public void unbind() {
        //disableVertexAttribs();
        glBindVertexArray(0);
        current_bound = null;
    }

    public void glVertexAttribDivisor(int index, int divisor) {
        GL43.glVertexAttribDivisor(index, divisor);
    }

    public void delete() {
        glDeleteVertexArrays(id);
    }
}
