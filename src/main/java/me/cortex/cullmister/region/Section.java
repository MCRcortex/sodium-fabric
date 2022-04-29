package me.cortex.cullmister.region;

import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;

public class Section {
    public static final int SIZE = 4 * (1+1+3*3+1+4*2*7);

    public final SectionPos pos;
    public final int id;
    public BindlessBuffer vertexData;
    public Vector3f size;
    public Vector3f offset;
    public VertexRange[] SOLID;
    public VertexRange[] CUTOUT_MIPPED;
    public VertexRange[] CUTOUT;
    public VertexRange[] TRANSLUCENT;
    private final Region regionIn;

    public Section(Region in, SectionPos pos, int id) {
        this.pos = pos;
        this.id = id;
        regionIn = in;
    }

    public void write(long ptr) {
        MemoryUtil.memPutInt(ptr, id);
        ptr += 8;
        offset.add(pos.x() * 16, pos.y() * 16, pos.z() * 16, new Vector3f()).getToAddress(ptr);
        ptr += 12;
        size.getToAddress(ptr);
        ptr += 12;
        new Vector3i(pos.x(), pos.y(), pos.z()).getToAddress(ptr);
        ptr += 12;
        ptr += 4;
        //new Vector2i((int) ((vertexDataPosition.offset/20)), (int) (((vertexDataPosition.size/20)/4)*6)).getToAddress(ptr);

        if (SOLID == null) {
            MemoryUtil.memSet(ptr, 0, 8*7);
        } else {
            writeRangeBlock(ptr, SOLID);
        }
        ptr += 8*7;
        if (CUTOUT_MIPPED == null) {
            MemoryUtil.memSet(ptr, 0, 8*7);
        } else {
            writeRangeBlock(ptr, CUTOUT_MIPPED);
        }
        ptr += 8*7;
        if (CUTOUT == null) {
            MemoryUtil.memSet(ptr, 0, 8*7);
        } else {
            writeRangeBlock(ptr, CUTOUT);
        }
        ptr += 8*7;
        if (TRANSLUCENT == null) {
            MemoryUtil.memSet(ptr, 0, 8*7);
        } else {
            writeRangeBlock(ptr, TRANSLUCENT);
        }
    }

    private void writeRangeBlock(long ptr, VertexRange[] ranges) {
        for (VertexRange r : ranges) {
            if (r == null)
                new Vector2i(0,0).getToAddress(ptr);
            else
                new Vector2i(r.firstVertex(), (r.vertexCount()/4)*6).getToAddress(ptr);
            ptr+=8;
        }
    }


    //TODO: Needs to delete vertex data and set chunkMeta for its id to -1
    public void delete() {
        vertexData.delete();
    }

    //TODO: Need to update chunk meta
    public void uploadGeometryAndUpdate(NativeBuffer buffer) {
        //TODO: Maybe just reuse the buffer, the mempointer will change tho so still need to update meta
        if (vertexData != null) {
            vertexData.delete();
            vertexData = null;
        }
        MinecraftClient.getInstance().getProfiler().push("Making buffer");
        vertexData = new BindlessBuffer(buffer.getLength(), GL_STATIC_DRAW, MemoryUtil.memAddress(buffer.getDirectBuffer()));
        MinecraftClient.getInstance().getProfiler().pop();

    }
}
