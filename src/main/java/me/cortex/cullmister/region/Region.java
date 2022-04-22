package me.cortex.cullmister.region;

import it.unimi.dsi.fastutil.ints.*;
import me.cortex.cullmister.utils.VBO;
import me.cortex.cullmister.utils.arena.GLSparse;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.PriorityQueue;

public class Region {
    public static class DrawData {
        GLSparse drawCommands;
        VBO drawMeta;
        VBO drawCounts;
    }

    GLSparse vertexData;
    VBO chunkMeta;
    DrawData drawData = new DrawData();

    public int sectionCount = 0;
    Int2ObjectOpenHashMap<Section> sections = new Int2ObjectOpenHashMap<>();
    Int2IntOpenHashMap pos2id = new Int2IntOpenHashMap();
    IntAVLTreeSet freeIds = new IntAVLTreeSet();

    //Contains chunk vertex data, chunk meta data, draw call shit etc etc
    //Region size should be like 16x6x16? or like 32x6x32
    // could do like a pre pass filter on them too with hiz and indirectcomputedispatch
    final RegionPos pos;

    public Region(RegionPos pos) {
        this.pos = pos;
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
            return sections.get(pos2id.get(pid));
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
            throw new IllegalStateException();
        }
    }

    public void updateMeshes(TerrainBuildResult result) {
        Section section = getOrCreate(result.pos());
        //System.out.println(section.id);
    }
}
