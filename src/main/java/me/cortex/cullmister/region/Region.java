package me.cortex.cullmister.region;

public class Region {
    //Contains chunk vertex data, chunk meta data, draw call shit etc etc

    //Region size should be like 16x6x16? or like 32x6x32
    // could do like a pre pass filter on them too with hiz and indirectcomputedispatch
    final RegionPos pos;

    public Region(RegionPos pos) {
        this.pos = pos;
    }
}
