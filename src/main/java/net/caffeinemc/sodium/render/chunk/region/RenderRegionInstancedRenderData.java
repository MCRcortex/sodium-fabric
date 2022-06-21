package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;

import java.nio.ByteBuffer;
import java.util.Set;

public class RenderRegionInstancedRenderData {
    public final ImmutableBuffer visBuffer;
    public final MappedBuffer cpuSectionVis;
    //public final MappedBuffer computeDispatchIndirectBuffer;//Nice way to not actually call 90% of compute
    public final MappedBuffer sceneBuffer;

    public final MappedBuffer cpuCommandCount;
    public final ImmutableBuffer counterBuffer;
    //public final MappedBuffer counterBuffer;

    //FIXME: can probably move this to be a bigger buffer for all lists, note will need to be 1 PER render layer
    // steal from RenderListBuilder
    /*
    public final ImmutableBuffer countBuffer;
    public final ImmutableBuffer commandBuffer;
     */
    public final ImmutableBuffer instanceBuffer;
    public final ImmutableBuffer id2InstanceBuffer;
    //public final MappedBuffer instanceBuffer;

    //public final MappedBuffer cmd0buff;//just for testing will be moved
    public final ImmutableBuffer cmd0buff;//just for testing will be moved
    public final ImmutableBuffer cmd1buff;//just for testing will be moved
    public final ImmutableBuffer cmd2buff;//just for testing will be moved
    public final ImmutableBuffer trans3;//just for testing will be moved
    public final MappedBuffer cmd3buff;//just for testing will be moved

    private final RenderDevice device;
    public RenderRegionInstancedRenderData(RenderDevice device) {
        this.device = device;
        this.visBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4, Set.of());
        this.cpuSectionVis = device.createMappedBuffer(RenderRegion.REGION_SIZE*4, Set.of(MappedBufferFlags.READ));
        this.sceneBuffer = device.createMappedBuffer(4*4*4+3*4+4, Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        this.counterBuffer = device.createBuffer(5*4, Set.of());
        this.cpuCommandCount = device.createMappedBuffer(5*4, Set.of(MappedBufferFlags.READ));
        this.instanceBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4*3, Set.of());
        this.id2InstanceBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4, Set.of());
        //If empty memory buffer is specified, this fixes it
        this.cmd0buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd1buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd2buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.trans3 = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*4), Set.of());
        //this.cmd3buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd3buff = device.createMappedBuffer(RenderRegion.REGION_SIZE*5*4*6, Set.of(MappedBufferFlags.READ));//FIXME: TUNE BUFFER SIZE

    }
}
