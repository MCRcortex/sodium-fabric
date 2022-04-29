package me.cortex.cullmister;

public class CullSystem {


    //Plan, dual pass system:
    //visibility test with raster and NV_representative_fragment_test, this atomically sets a data buffer
    // with visible side flags
    //Then compute shader is executed which builds render commands
    //Then execute commands

    //The visibility raster command list is handled when a chunk is added and set to the nop instruction for that
    // sector when the section is freed, the raster command list is a list of GL_DRAW_ELEMENTS or something

    //NOTE: the reset of the data for the flags buffer is done in the compute shader

    //The compute shader generates render commands to a stream

}
