package net.caffeinemc.sodium.vkinterop.vk.pipeline;

//Note this should not be specific to graphics pipeline, raytracing or compute should also work via this object
// maybe make a builder or subtype thing that generates these pipelines
// or create subclasses for the different pipelines
public class SVkPipeline {
    SVkPipelineLayout pipelineLayout;
    long pipeline;

    public SVkPipeline(long pipeline, SVkPipelineLayout pipelineLayout) {
        this.pipeline = pipeline;
        this.pipelineLayout = pipelineLayout;
    }
}
