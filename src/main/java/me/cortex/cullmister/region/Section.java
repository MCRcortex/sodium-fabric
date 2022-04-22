package me.cortex.cullmister.region;

import me.cortex.cullmister.utils.arena.GLSparseRange;

public class Section {
    public final SectionPos pos;
    final int id;
    public GLSparseRange vertexDataPosition;

    //Contains GLSparseRange to each layer range, also holds directional ranges for each layer range

    public Section(SectionPos pos, int id) {
        this.pos = pos;
        this.id = id;
    }



    //Also contains method to write to client memory

}
