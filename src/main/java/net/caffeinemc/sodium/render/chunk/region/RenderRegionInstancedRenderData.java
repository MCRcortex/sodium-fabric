package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;

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
    public final ImmutableBuffer cmd3buff;//just for testing will be moved

    private final RenderDevice device;
    public RenderRegionInstancedRenderData(RenderDevice device) {
        this.device = device;
        this.visBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4, Set.of());
        this.cpuSectionVis = device.createMappedBuffer(RenderRegion.REGION_SIZE*4, Set.of(MappedBufferFlags.READ));//MappedBufferFlags.CLIENT_STORAGE
        this.sceneBuffer = device.createMappedBuffer(4*4*4+3*4+4, Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        this.counterBuffer = device.createBuffer(5*4, Set.of());
        this.cpuCommandCount = device.createMappedBuffer(5*4, Set.of(MappedBufferFlags.READ));//, MappedBufferFlags.CLIENT_STORAGE
        this.instanceBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4*3, Set.of());
        this.id2InstanceBuffer = device.createBuffer(RenderRegion.REGION_SIZE*4, Set.of());
        //If empty memory buffer is specified, this fixes it
        this.cmd0buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd1buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd2buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.trans3 = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*4), Set.of());
        this.cmd3buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        //this.cmd3buff = device.createMappedBuffer(RenderRegion.REGION_SIZE*5*4*6, Set.of(MappedBufferFlags.READ));//FIXME: TUNE BUFFER SIZE


        set0Buffer(visBuffer);
        set0Buffer(cpuSectionVis);
        set0Buffer(sceneBuffer);
        set0Buffer(counterBuffer);
        set0Buffer(cpuCommandCount);
        set0Buffer(instanceBuffer);
        set0Buffer(id2InstanceBuffer);
        set0Buffer(cmd0buff);
        set0Buffer(cmd1buff);
        set0Buffer(cmd2buff);
        set0Buffer(cmd3buff);
        set0Buffer(trans3);
        preSetupCommandBuffer(cmd0buff, RenderRegion.REGION_SIZE);
        preSetupCommandBuffer(cmd1buff, RenderRegion.REGION_SIZE);
        preSetupCommandBuffer(cmd2buff, RenderRegion.REGION_SIZE);
        preSetupCommandBuffer(cmd3buff, RenderRegion.REGION_SIZE);

    }

    public void delete() {
        device.deleteBuffer(visBuffer);
        device.deleteBuffer(cpuSectionVis);
        device.deleteBuffer(sceneBuffer);
        device.deleteBuffer(counterBuffer);
        device.deleteBuffer(cpuCommandCount);
        device.deleteBuffer(instanceBuffer);
        device.deleteBuffer(id2InstanceBuffer);
        device.deleteBuffer(cmd0buff);
        device.deleteBuffer(cmd1buff);
        device.deleteBuffer(cmd2buff);
        device.deleteBuffer(cmd3buff);
        device.deleteBuffer(trans3);
    }

    private static void set0Buffer(Buffer buffer) {
        glClearNamedBufferData(GlBuffer.getHandle(buffer),  GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
    }

    private static final Buffer SETUP_BUFF;
    static {
        ByteBuffer buffer =  ByteBuffer.allocateDirect(4*5).order(ByteOrder.nativeOrder());
        IntBuffer ib = buffer.asIntBuffer();
        ib.put(0);
        ib.put(1);
        ib.put(0);
        ib.put(0);
        ib.put(0);
        ib.rewind();
        SETUP_BUFF = SodiumClientMod.DEVICE.createBuffer(buffer, Set.of());
    }
    private static void preSetupCommandBuffer(Buffer buffer, int times) {
        for (int i = 0; i < times; i++) {
            glCopyNamedBufferSubData(GlBuffer.getHandle(SETUP_BUFF), GlBuffer.getHandle(buffer),
                    0, (long) i *5*4, 5*4);
        }
    }
}
