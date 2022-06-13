package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.sodium.render.buffer.StreamingBuffer;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SectionMeta {
    public static final int SIZE = 4+4*3*3+4+4*2*7*4;


    private record Range(int offset, int count){}

    private final int id;
    private Vector3f AABBOffset = new Vector3f();
    private Vector3f AABBSize = new Vector3f();
    private Vector3f pos = new Vector3f();
    private int lmsk;
    private Range[] SOLID = new Range[7];
    private Range[] CUTOUT_MIPPED = new Range[7];
    private Range[] CUTOUT = new Range[7];
    private Range[] TRANSLUCENT = new Range[7];

    private final StreamingBuffer streamingBuffer;


    public SectionMeta(int id, StreamingBuffer streamingBuffer) {
        this.id = id;
        this.streamingBuffer = streamingBuffer;
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
        buffer.putInt(4 + 4*3*3, lmsk);
        section.flushFull();
    }

    public void setPos(Vector3f pos) {
        this.pos = pos;
    }

    public void setAABB(Vector3f offset, Vector3f size) {
        this.AABBOffset = offset;
        this.AABBSize = size;
    }

}
