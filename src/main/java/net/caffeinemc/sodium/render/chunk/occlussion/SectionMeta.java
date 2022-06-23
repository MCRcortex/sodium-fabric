package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SectionMeta {
    public static final int SIZE = 4+4*3*3+4+4*2*7*4;

    private final int id;
    private Vector3f AABBOffset = new Vector3f();
    private Vector3f AABBSize = new Vector3f();
    private Vector3f pos = new Vector3f();
    private int lmsk;
    public VertexRange[] SOLID = new VertexRange[7];
    public VertexRange[] CUTOUT_MIPPED = new VertexRange[7];
    public VertexRange[] CUTOUT = new VertexRange[7];
    public VertexRange[] TRANSLUCENT = new VertexRange[7];

    private final StreamingBuffer streamingBuffer;
    public final RenderSection theSection;

    public SectionMeta(int id, StreamingBuffer streamingBuffer, RenderSection section) {
        this.id = id;
        this.streamingBuffer = streamingBuffer;
        theSection = section;
    }

    public void flush() {
        //FIXME: if already written to a section, need to clear that id if its different from this id
        StreamingBuffer.WritableSection section = streamingBuffer.getSection(id);
        ByteBuffer buffer = section.getView().order(ByteOrder.nativeOrder());
        long buffAddr = MemoryUtil.memAddress(buffer);
        buffer.putInt(0, id);
        AABBOffset.getToAddress(buffAddr + 4);
        AABBSize.getToAddress(buffAddr + 4 + 4*3);
        pos.getToAddress(buffAddr + 4 + 4*3*2);
        computelmsk();
        buffer.position(4 + 4*3*3);
        buffer.putInt(lmsk);
        writeRenderRanges(buffer);
        buffer.rewind();
        section.flushFull();
    }

    public void delete() {
        StreamingBuffer.WritableSection section = streamingBuffer.getSection(id);
        ByteBuffer buffer = section.getView().order(ByteOrder.nativeOrder());
        buffer.putInt(0, -1);
        buffer.rewind();
        section.flushFull();
    }

    public void setPos(Vector3f pos) {
        this.pos = pos;
    }

    public void setAABB(Vector3f offset, Vector3f size) {
        this.AABBOffset = offset;
        this.AABBSize = size;
    }


    private void computelmsk() {
        int vismsk = 0;
        if (SOLID != null) {
            vismsk |= Byte.toUnsignedInt(computeVis(SOLID));
        }
        if (CUTOUT_MIPPED != null) {
            vismsk |= Byte.toUnsignedInt(computeVis(CUTOUT_MIPPED))<<8;
        }
        if (CUTOUT != null) {
            vismsk |= Byte.toUnsignedInt(computeVis(CUTOUT))<<16;
        }
        if (TRANSLUCENT != null) {
            vismsk |= (computeVis(TRANSLUCENT)!=0?1:0)<<24;
        }
        lmsk = vismsk;
    }

    private byte computeVis(VertexRange[] ranges) {
        byte vis = 0;
        for (int i = 0; i<ranges.length; i++) {
            if (ranges[i] != null && ranges[i].vertexCount()!=0) {
                vis |= (1)<<i;
            }
        }
        return vis;
    }


    private void writeRenderRanges(ByteBuffer buffer) {
        if (SOLID == null) {
            buffer.put(new byte[2*4*7]);
        } else {
            writeRangeBlock(buffer, SOLID);
        }
        if (CUTOUT_MIPPED == null) {
            buffer.put(new byte[2*4*7]);
        } else {
            writeRangeBlock(buffer, CUTOUT_MIPPED);
        }
        if (CUTOUT == null) {
            buffer.put(new byte[2*4*7]);
        } else {
            writeRangeBlock(buffer, CUTOUT);
        }
        if (TRANSLUCENT == null) {
            buffer.put(new byte[2*4*7]);
        } else {
            writeRangeBlock(buffer, TRANSLUCENT);
        }
    }

    private void writeRangeBlock(ByteBuffer buffer, VertexRange[] ranges) {
        for (VertexRange r : ranges) {
            if (r == null) {
                buffer.putInt(0);
                buffer.putInt(0);
            } else {
                buffer.putInt(r.firstVertex());
                buffer.putInt(r.vertexCount());//Assume already in (/4)*6 form
            }
        }
    }

    public void reset() {
        SOLID = new VertexRange[SOLID.length];
        CUTOUT = new VertexRange[CUTOUT.length];
        CUTOUT_MIPPED = new VertexRange[CUTOUT_MIPPED.length];
        TRANSLUCENT = new VertexRange[TRANSLUCENT.length];
    }
}
