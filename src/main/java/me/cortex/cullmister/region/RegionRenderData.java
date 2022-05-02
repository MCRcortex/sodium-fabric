package me.cortex.cullmister.region;

import me.cortex.cullmister.commandListStuff.BindlessBuffer;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_COPY;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;

public class RegionRenderData {
    public BindlessBuffer visBuffer = new BindlessBuffer((1<<(Region.WIDTH_BITS+1))*Region.HEIGHT*Section.SIZE, GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT);//the GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT are just for testing
    public BindlessBuffer[] drawCommandsList = new BindlessBuffer[4];
    public BindlessBuffer rasterCommands;
    public BindlessBuffer drawMeta = new BindlessBuffer((1<<(Region.WIDTH_BITS+1))*Region.HEIGHT*3*4, 0);

    public BindlessBuffer chunkMeta = new BindlessBuffer((1<<(Region.WIDTH_BITS+1))*Region.HEIGHT*Section.SIZE, GL_MAP_WRITE_BIT|GL_MAP_READ_BIT|GL_DYNAMIC_STORAGE_BIT);
    public BindlessBuffer UBO = new BindlessBuffer(4*(4*4+3) + 4*(1+4), GL_MAP_WRITE_BIT|GL_DYNAMIC_STORAGE_BIT|GL_MAP_READ_BIT);

    public RegionRenderData() {
        for (int i = 0; i < 4; i++) {
            drawCommandsList[i] = new BindlessBuffer(1000000,GL_MAP_READ_BIT);
        }
    }

    public void delete() {
        UBO.delete();
        chunkMeta.delete();
        visBuffer.delete();
        drawMeta.delete();
        for (int i = 0; i < 4; i++) {
            if (drawCommandsList[i] != null)
                drawCommandsList[i].delete();
            drawCommandsList[i] = null;
        }
    }
}
