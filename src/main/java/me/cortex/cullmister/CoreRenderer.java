package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.RegionPos;
import me.cortex.cullmister.utils.ShaderPreprocessor;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
//TODO: ALSO ACTUALLY JUST TRY WHAT JELLI DID with writing to buffer then constructing off that
import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.NVHeader;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL45.glNamedFramebufferTexture;
import static org.lwjgl.opengl.NVCommandList.GL_NOP_COMMAND_NV;
import static org.lwjgl.opengl.NVCommandList.GL_TERMINATE_SEQUENCE_COMMAND_NV;
import static org.lwjgl.opengl.NVShaderBufferLoad.glMakeNamedBufferResidentNV;
//TODO: TRY OPTIMIZING WITH CONDITIONAL RENDERING ON THE REGIONS

//TODO: NEED TO TRY SORT THE RENDER SECTIONS TO TAKE MAXIMUM ADVANTAGE OF CULLING
public class CoreRenderer {
    public static CoreRenderer INSTANCE = new CoreRenderer();



    public RegionManager regionManager;
    public CullSystem culler;
    public RenderLayerSystem renderer;
    int frame;

    public CoreRenderer() {
        regionManager = new RegionManager();
        culler = new CullSystem();
        renderer = new RenderLayerSystem();
    }

    public void setWorld(ClientWorld world) {
        regionManager.setWorld(world);
    }

    public void reload() {
        regionManager.reset();
    }

    long last;
    long count;
    //TODO: create a prepass compute shader which hiz tests each region then use glDispatchIndirect
    // tho maybe dont use hiz, could just use a normal elementsInstanced and then have fragment early exit write the count
    public void debugRender(ChunkRenderMatrices renderMatrices, Vector3f pos, Frustum frustum) {
        Matrix4f pmt = new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView()).translate(new Vector3f(pos).negate());
        count++;
        if (last + 1000 < System.currentTimeMillis()) {
            MinecraftClient.getInstance().getWindow().setTitle("FPS: "+((count*1000)/(System.currentTimeMillis()-last)) + " build queue: "+regionManager.builder.inflowWorkQueue.size()+" upload queue: "+regionManager.workResultsLocal.size());
            last = System.currentTimeMillis();
            count = 0;
        }
        frame++;
        List<Region> regions = regionManager.regions.values().stream().filter(p-> {
            Vector3f c = new Vector3f(p.pos.x()<<(Region.WIDTH_BITS+4), p.pos.y()*Region.HEIGHT*16, p.pos.z()<<(Region.WIDTH_BITS+4));
            return frustum.isBoxVisible(c.x, c.y, c.z, c.x+(1<<(Region.WIDTH_BITS+4)), c.y+Region.HEIGHT*16, c.z+(1<<(Region.WIDTH_BITS+4)));
        })//.sorted(Comparator.comparingDouble(a->new Vector3f(a.pos.x(), a.pos.y(), a.pos.y()).distanceSquared(pos.x/(1<<Region.WIDTH_BITS+4), pos.y/Region.HEIGHT,pos.z/(1<<Region.WIDTH_BITS+4))))
                .toList();
        MinecraftClient.getInstance().getProfiler().push("");


        MinecraftClient.getInstance().getProfiler().swap("Depth set");
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        //glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_STENCIL_ATTACHMENT, hiZ.mipDepthTex, 0);
        MinecraftClient.getInstance().getProfiler().swap("Cull Prep");
        if (true) {
            for (Region r : regions) {
                culler.prep(r, renderMatrices, pos);
            }
        }
        MinecraftClient.getInstance().getProfiler().swap("Cull phase 1");
        if (true) {
            culler.begin1();
            regions.forEach(culler::process1);
            culler.end1();
        }
        MinecraftClient.getInstance().getProfiler().swap("Cull phase 2");
        if (true) {
            culler.begin2();
            regions.forEach(culler::process2);
            culler.end2();
        }


        MinecraftClient.getInstance().getProfiler().swap("Depth clear");
        glClear(GL_DEPTH_BUFFER_BIT);
        MinecraftClient.getInstance().getProfiler().swap("Draw");


        renderer.begin();
        for (Region r : regions) {
            renderer.render(r);
        }
        renderer.end();

        MinecraftClient.getInstance().getProfiler().swap("other");
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_ATTACHMENT, MinecraftClient.getInstance().getFramebuffer().getDepthAttachment(), 0);
        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_STENCIL_ATTACHMENT, 0, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        {
            System.err.println("GL Framebuffer not complete");
        }

        MinecraftClient.getInstance().getProfiler().pop();
        if (BufferRenderer.vertexFormat != null)
            glBindVertexArray(BufferRenderer.vertexFormat.getVertexArray());
    }

    public void tick() {
        regionManager.tick(frame);
    }


    public static void OnFlipCallback() {
        //glFinish();
    }
}
