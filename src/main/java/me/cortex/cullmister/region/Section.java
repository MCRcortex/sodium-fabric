package me.cortex.cullmister.region;

public class Section {
    final SectionPos pos;
    final int id;

    //Contains GLSparseRange to each layer range, also holds directional ranges for each layer range

    public Section(SectionPos pos, int id) {
        this.pos = pos;
        this.id = id;
    }


    //Also contains method to write to client memory
}
