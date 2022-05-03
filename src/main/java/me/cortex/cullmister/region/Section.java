package me.cortex.cullmister.region;

import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;

//TODO: Maybe change vertexRanges to be like shorts
public class Section {
    public static final int SIZE = 2 + 2 + 4*3 + 4*3 + 4*3 + 8
            + 4 * 4*2*7;

    public final SectionPos pos;
    public final short id;
    public BindlessBuffer vertexData;
    public Vector3f size;
    public Vector3f offset;
    public VertexRange[] SOLID;
    public VertexRange[] CUTOUT_MIPPED;
    public VertexRange[] CUTOUT;
    public VertexRange[] TRANSLUCENT;
    private final Region regionIn;

    public Section(Region in, SectionPos pos, short id) {
        this.pos = pos;
        this.id = id;
        regionIn = in;
    }

    public void write(long ptr) {
        MemoryUtil.memPutShort(ptr, id);
        ptr += 4;
        offset.add(pos.x() * 16, pos.y() * 16, pos.z() * 16, new Vector3f()).getToAddress(ptr);
        ptr += 12;
        size.getToAddress(ptr);
        ptr += 12;
        new Vector3f(pos.x() * 16, pos.y() * 16, pos.z() * 16).getToAddress(ptr);
        ptr += 12;
        MemoryUtil.memPutAddress(ptr, vertexData.addr);
        ptr += 8;

        writeRenderRanges(ptr);
        //TODO: NOT THIS IS JUST FOR TESTING
        //MemoryUtil.memPutInt(ptr+4, (int) (((vertexData.size/20)/4)*6));
    }

    private long writeRenderRanges(long ptr) {
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
        return ptr + 7*8;
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
        glClearNamedBufferSubData(regionIn.draw.chunkMeta.id, GL_R32UI, SIZE * id, 4, GL_RED, GL_UNSIGNED_INT, new int[]{-1});
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

        MinecraftClient.getInstance().getProfiler().swap("update meta");
        long ptrM = MemoryUtil.nmemAlloc(SIZE);
        write(ptrM);
        MinecraftClient.getInstance().getProfiler().swap("set meta");
        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        //This absolutly shits the fps so maybe do it via transfer buffer or something???
        if (true) {
            long ptr = nglMapNamedBufferRange(regionIn.draw.chunkMeta.id, SIZE * id, SIZE, GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT);
            MinecraftClient.getInstance().getProfiler().swap("innter meta");
            MemoryUtil.memCopy(ptrM, ptr, SIZE);
            MinecraftClient.getInstance().getProfiler().swap("op meta");
            glUnmapNamedBuffer(regionIn.draw.chunkMeta.id);
        }

        MemoryUtil.nmemFree(ptrM);
        MinecraftClient.getInstance().getProfiler().pop();

    }
}
