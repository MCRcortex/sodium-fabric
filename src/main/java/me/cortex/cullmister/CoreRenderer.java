package me.cortex.cullmister;

import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.RegionPos;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3f;

import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL45.glNamedFramebufferTexture;
//TODO: TRY OPTIMIZING WITH CONDITIONAL RENDERING ON THE REGIONS
public class CoreRenderer {
    public RegionManager regionManager;
    public ComputeCullInterface culler;
    LayerRenderer debugLayer;
    HiZ hiZ;
    int frame;

    public CoreRenderer() {
        regionManager = new RegionManager();
        debugLayer = new LayerRenderer();
        hiZ = new HiZ();
        culler = new ComputeCullInterface(hiZ);
    }

    public void setWorld(ClientWorld world) {
        regionManager.setWorld(world);
    }

    public void reload() {
        regionManager.reset();
    }

    long last;
    long count;
    public void debugRender(ChunkRenderMatrices renderMatrices, Vector3f pos) {
        count++;
        if (last + 1000 < System.currentTimeMillis()) {
            MinecraftClient.getInstance().getWindow().setTitle("FPS: "+((count*1000)/(System.currentTimeMillis()-last)) + " sections: " + debugLayer.sectioncount +" build queue: "+regionManager.builder.inflowWorkQueue.size());
            last = System.currentTimeMillis();
            count = 0;
        }
        frame++;
        if (false)
            return;

        MinecraftClient.getInstance().getProfiler().push("hiz resize");
        hiZ.resize(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().viewportHeight);
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_ATTACHMENT, hiZ.mipDepthTex, 0);
        glClear(GL_DEPTH_BUFFER_BIT);
        MinecraftClient.getInstance().getProfiler().swap("region");
        debugLayer.being(null);

        if (!regionManager.regions.isEmpty() ) {
            for (Region r : regionManager.regions.values().stream()//.limit(1)
                    .toList()) {
                debugLayer.superdebugtestrender(frame, r, renderMatrices, pos.sub(r.pos.x()<<9, r.pos.y()*Region.HEIGHT*16, r.pos.z()<<9,new Vector3f()));
            }
        }
        debugLayer.end();
        MinecraftClient.getInstance().getProfiler().swap("hiz build");
        hiZ.buildMips();
        MinecraftClient.getInstance().getProfiler().swap("cull_test");
        culler.begin(renderMatrices, pos, frame);
        if (!regionManager.regions.isEmpty()) {
            regionManager.regions.values().stream()//.filter(e->e.pos.x()==0&&e.pos.z()==0&&e.pos.y()==2)
                    .forEach(culler::process);
        }
        culler.end();
        MinecraftClient.getInstance().getProfiler().swap("other");

        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        //hiZ.debugBlit(4);


        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_ATTACHMENT, MinecraftClient.getInstance().getFramebuffer().getDepthAttachment(), 0);
        MinecraftClient.getInstance().getProfiler().pop();
    }

    public void tick() {
        regionManager.tick(frame);
    }
}
