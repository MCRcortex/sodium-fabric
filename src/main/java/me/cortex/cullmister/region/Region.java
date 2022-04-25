package me.cortex.cullmister.region;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.*;
import me.cortex.cullmister.utils.VAO;
import me.cortex.cullmister.utils.VBO;
import me.cortex.cullmister.utils.arena.GLSparse;
import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.chunk.RenderSectionManager;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.DefaultRenderPasses;
import net.caffeinemc.sodium.render.chunk.state.ChunkModel;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.ARBDirectStateAccess.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45.*;

public class Region {
    public int[] cacheDrawCounts = new int[6];
    public static final int HEIGHT = 5;
    public static final int WIDTH_BITS = 4;
    public static class DrawData {
        public VBO drawCommands = new VBO();
        public VBO drawMeta = new VBO();
        public VBO drawCounts = new VBO();
        //TODO: merge this into the count above
        public VBO drawMetaCount = new VBO();
    }

    public GLSparse vertexData = new GLSparse();
    public VAO vao = new VAO();
    public VBO chunkMeta = new VBO();
    public DrawData drawData = new DrawData();
    public int query = glGenQueries();
    public VBO drawCountsMemCpyAccess = new VBO();

    public int sectionCount = 0;
    public Int2ObjectOpenHashMap<Section> sections = new Int2ObjectOpenHashMap<>();
    Int2IntOpenHashMap pos2id = new Int2IntOpenHashMap();
    IntAVLTreeSet freeIds = new IntAVLTreeSet();

    //Contains chunk vertex data, chunk meta data, draw call shit etc etc
    //Region size should be like 16x6x16? or like 32x6x32
    // could do like a pre pass filter on them too with hiz and indirectcomputedispatch
    public final RegionPos pos;

    public Region(RegionPos pos) {
        this.pos = pos;
        glNamedBufferStorage(chunkMeta.id, (1<<(WIDTH_BITS+4))*HEIGHT*(1<<(WIDTH_BITS+4))*Section.SIZE, GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT);
        glNamedBufferStorage(drawData.drawMeta.id, 3*4*(1<<(WIDTH_BITS+4))*HEIGHT*(1<<(WIDTH_BITS+4)), GL_DYNAMIC_STORAGE_BIT);
        glNamedBufferStorage(drawData.drawCounts.id, 4*4, GL_MAP_READ_BIT);//4 counts
        glNamedBufferStorage(drawCountsMemCpyAccess.id, 4*4, GL_MAP_READ_BIT|GL_MAP_PERSISTENT_BIT);//4 counts
        glNamedBufferData(drawData.drawMetaCount.id, 4, GL_DYNAMIC_DRAW);//1 count
        glNamedBufferStorage(drawData.drawCommands.id, 5*4*10000*4, GL_DYNAMIC_STORAGE_BIT);//TODO: Actually calculate rough max

        nglClearNamedBufferData(drawData.drawCounts.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        nglClearNamedBufferData(drawData.drawMetaCount.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        buildVAO();
    }
    public void buildVAO() {
        RenderSystem.IndexBuffer ib = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS, 100000);
        vao.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ib.getId());
        glBindBuffer(GL_ARRAY_BUFFER, vertexData.id);
        //As defined by TerrainVertexType
        vao.glVertexAttribPointer(1, 3, GL_SHORT, true, 20,0);
        vao.glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, 20,8);
        vao.glVertexAttribPointer(3, 2, GL_UNSIGNED_SHORT, true, 20,12);
        vao.glVertexAttribPointer(4, 2, GL_UNSIGNED_SHORT, true, 20,16);
        glBindBuffer(GL_ARRAY_BUFFER, drawData.drawMeta.id);
        vao.glVertexAttribPointer(0, 3, GL_FLOAT, false, 3*4,0);
        glVertexAttribDivisor(0, 1);
        vao.unbind();

    }


    private int getNewChunkId() {
        if (freeIds.isEmpty()) {
            //TODO: should also resize chunkMeta to fit or like double the size or something
            return sectionCount++;
        }
        int id = freeIds.firstInt();
        freeIds.remove(id);
        return id;
    }

    public Section getOrCreate(ChunkSectionPos pos) {
        int pid = SectionPos.from(pos).hashCode();
        if (pos2id.containsKey(pid)) {
            Section section = sections.get(pos2id.get(pid));
            if (!section.pos.equals(SectionPos.from(pos)))
                throw new IllegalStateException();
            return section;
        }
        int id = getNewChunkId();
        pos2id.put(pid, id);
        Section section = new Section(SectionPos.from(pos), id);
        sections.put(id, section);
        return section;
    }

    public void remove(ChunkSectionPos pos) {
        int pid = SectionPos.from(pos).hashCode();
        if (pos2id.containsKey(pid)) {
            int id = pos2id.get(pid);
            pos2id.remove(pid);
            Section section = sections.remove(id);
            //TODO: Cleanup and release section data, also set metadata id to be zero
            if (section.vertexDataPosition != null) {
                vertexData.free(section.vertexDataPosition);
                section.vertexDataPosition = null;
            }
            long ptr = nglMapNamedBufferRange(chunkMeta.id, (long) Section.SIZE *section.id, 4, GL_MAP_WRITE_BIT);
            MemoryUtil.memPutInt(ptr, -1);
            glUnmapNamedBuffer(chunkMeta.id);

            if (id == sectionCount-1) {
                //Free as many sections as possible
                sectionCount--;
                while ((!freeIds.isEmpty()) && freeIds.lastInt() == sectionCount -1) {
                    freeIds.remove(freeIds.lastInt());
                    sectionCount--;
                }
                if (sectionCount == 0 && !freeIds.isEmpty())
                    throw new IllegalStateException();
            } else {
                //Enqueue id
                freeIds.add(id);
            }
        } else {
            //throw new IllegalStateException();
        }
    }
    //TODO: Optimize sparse buffer update transactions
    public void updateMeshes(TerrainBuildResult result) {
        MinecraftClient.getInstance().getProfiler().push("bufferdata");
        Section section = getOrCreate(result.pos());
        if (section.vertexDataPosition != null) {
            //Free buffer
            vertexData.free(section.vertexDataPosition);
            section.vertexDataPosition = null;
        }

        Map<ChunkRenderPass, VertexRange[]> passMapper = result.geometry().models().stream().collect(Collectors.toMap(ChunkModel::getRenderPass, ChunkModel::getModelRanges));
        if (passMapper.isEmpty())
            throw new IllegalStateException();
        section.CUTOUT = passMapper.getOrDefault(DefaultRenderPasses.CUTOUT, null);
        section.CUTOUT_MIPPED = passMapper.getOrDefault(DefaultRenderPasses.CUTOUT_MIPPED, null);
        section.SOLID = passMapper.getOrDefault(DefaultRenderPasses.SOLID, null);
        section.TRANSLUCENT = passMapper.getOrDefault(DefaultRenderPasses.TRANSLUCENT, null);

        section.size = new Vector3f(result.data().bounds.x2 - result.data().bounds.x1, result.data().bounds.y2 - result.data().bounds.y1, result.data().bounds.z2 - result.data().bounds.z1);
        section.offset = new Vector3f(result.data().bounds.x1 - result.pos().getMinX(), result.data().bounds.y1 - result.pos().getMinY(), result.data().bounds.z1 - result.pos().getMinZ()).add(0.5f, 0.5f, 0.5f);
        //Enqueue data upload, probably via an upload buffer that is mapped, this is because it can be alot faster
        // than buffersubdata as the gl pipeline must stall until copy is complete
        NativeBuffer buffer = result.geometry().vertices().buffer();
        section.vertexDataPosition = vertexData.alloc(buffer.getLength());
        nglNamedBufferSubData(vertexData.id, section.vertexDataPosition.offset, section.vertexDataPosition.size, MemoryUtil.memAddress(buffer.getDirectBuffer()));
        long ptr = nglMapNamedBufferRange(chunkMeta.id, (long) Section.SIZE *section.id, Section.SIZE, GL_MAP_WRITE_BIT);
        section.write(ptr);
        glUnmapNamedBuffer(chunkMeta.id);
        MinecraftClient.getInstance().getProfiler().pop();
        //Rewrite updated meta info to chunk meta
    }


    public void delete() {
        //Cleanup all opengl objects
        vao.delete();
        chunkMeta.delete();
        vertexData.delete();
        drawData.drawCounts.delete();
        drawData.drawMeta.delete();
        drawData.drawCommands.delete();
        drawCountsMemCpyAccess.delete();
        glDeleteQueries(query);
    }
}
