package me.cortex.nv.gl;

public abstract class GlObject implements IResource {
    protected final int id;

    protected GlObject(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
