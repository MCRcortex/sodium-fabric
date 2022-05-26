package me.cortex.cullmister.chunkBuilder;

import java.util.LinkedList;
import java.util.List;

public class DirectionalQuadCluster {
    public List<Quad> quads = new LinkedList<>();
    public int axis;
    public DirectionalQuadCluster(Quad inital, int axis) {
        this.axis = axis;
        quads.add(inital);
    }

    public boolean isJoinable(Quad quad) {
        //TODO: make it assert join axis is correct
        int a = quads.get(0).connectable(quad);
        int b = quads.get(quads.size()-1).connectable(quad);
        return (a != -1 && Quad.DIRINDEX[a] != axis) || (b != -1 && Quad.DIRINDEX[b] != axis);
    }

    public void add(Quad quad) {
        if (quad.mergeabilityAxis() != axis)
            throw new IllegalStateException();
        //if (quads.size() != 1 && quads.get(0).connectable(quad)!=-1 && quads.get(quads.size()-1).connectable(quad)!=-1)
        //    throw new IllegalStateException();

        if (quads.get(0).connectable(quad)!=-1) {
            quads.add(0, quad);
        } else if (quads.get(quads.size()-1).connectable(quad)!=-1) {
            quads.add( quad);
        } else {
            throw new IllegalStateException();
        }
    }
}
