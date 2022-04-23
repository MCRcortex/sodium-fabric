package me.cortex.cullmister.region;

import me.cortex.cullmister.utils.arena.GLSparseRange;
import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

public class Section {
    public static final int SIZE = 4 * (1+1+3*3+1+4*2);
    public final SectionPos pos;
    public final int id;
    public GLSparseRange vertexDataPosition;
    public Vector3f size;
    public Vector3f offset;
    public VertexRange[] SOLID;
    public VertexRange[] CUTOUT_MIPPED;
    public VertexRange[] CUTOUT;
    public VertexRange[] TRANSLUCENT;

    //Contains GLSparseRange to each layer range, also holds directional ranges for each layer range

    public Section(SectionPos pos, int id) {
        this.pos = pos;
        this.id = id;
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
            new Vector2i(0, 0).getToAddress(ptr);
        } else {
            int o = Integer.MAX_VALUE;
            int s = 0;
            for (VertexRange r : SOLID) {
                if (r==null)continue;
                o=Math.min(r.firstVertex(), o);
                s+=(r.vertexCount()/4)*6;
            }
            if (o == Integer.MAX_VALUE)
                new Vector2i(0, 0).getToAddress(ptr);
            else
                new Vector2i((int) (o+(vertexDataPosition.offset/20)),s).getToAddress(ptr);
        }
        ptr += 8;
        if (CUTOUT_MIPPED == null) {
            new Vector2i(0, 0).getToAddress(ptr);
        } else {
            int o = Integer.MAX_VALUE;
            int s = 0;
            for (VertexRange r : CUTOUT_MIPPED) {
                if (r==null)continue;
                o=Math.min(r.firstVertex(), o);
                s+=(r.vertexCount()/4)*6;
            }
            if (o == Integer.MAX_VALUE)
                new Vector2i(0, 0).getToAddress(ptr);
            else
                new Vector2i((int) (o+(vertexDataPosition.offset/20)),s).getToAddress(ptr);
        }
        ptr += 8;
        if (CUTOUT == null) {
            new Vector2i(0, 0).getToAddress(ptr);
        } else {
            int o = Integer.MAX_VALUE;
            int s = 0;
            for (VertexRange r : CUTOUT) {
                if (r==null)continue;
                o=Math.min(r.firstVertex(), o);
                s+=(r.vertexCount()/4)*6;
            }
            if (o == Integer.MAX_VALUE)
                new Vector2i(0, 0).getToAddress(ptr);
            else
                new Vector2i((int) (o+(vertexDataPosition.offset/20)),s).getToAddress(ptr);
        }
        ptr += 8;
        if (TRANSLUCENT == null) {
            new Vector2i(0, 0).getToAddress(ptr);
        } else {
            int o = Integer.MAX_VALUE;
            int s = 0;
            for (VertexRange r : TRANSLUCENT) {
                if (r==null)continue;
                o=Math.min(r.firstVertex(), o);
                s+=(r.vertexCount()/4)*6;
            }
            if (o == Integer.MAX_VALUE)
                new Vector2i(0, 0).getToAddress(ptr);
            else
                new Vector2i((int) (o+(vertexDataPosition.offset/20)),s).getToAddress(ptr);
        }

    }


    //Also contains method to write to client memory

}
