package me.cortex.nv.mesher;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.List;
import java.util.Set;

//Merges along a 2D axis
public class Plane2DMerger {
    private record Quad() {

    }

    private final Int2ObjectOpenHashMap<Set<Quad>> quads = new Int2ObjectOpenHashMap<>();

    public static void main(String[] args) {
        Plane2DMerger m = new Plane2DMerger();

    }
}
