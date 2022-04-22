package me.cortex.cullmister;

import me.cortex.cullmister.region.Region;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3f;

import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL45.glNamedFramebufferTexture;

public class CoreRenderer {
    public RegionManager regionManager;
    public ComputeCullInterface culler;
    LayerRenderer debugLayer;
    HiZ hiZ;

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

    public void debugRender(ChunkRenderMatrices renderMatrices, Vector3f pos) {
        MinecraftClient.getInstance().getProfiler().push("hiz resize");
        hiZ.resize(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().viewportHeight);
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_ATTACHMENT, hiZ.mipDepthTex, 0);
        glClear(GL_DEPTH_BUFFER_BIT);
        MinecraftClient.getInstance().getProfiler().swap("region");
        debugLayer.being(null);
        if (!regionManager.regions.isEmpty()) {
            for (Region r : regionManager.regions.values().stream()//.limit(1)
                    .toList()) {
                debugLayer.superdebugtestrender(r, renderMatrices, pos.sub(r.pos.x()<<9, r.pos.y()*5*16, r.pos.z()<<9,new Vector3f()));
            }
        }
        debugLayer.end();
        MinecraftClient.getInstance().getProfiler().swap("hiz build");
        hiZ.buildMips();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        //hiZ.debugBlit(4);


        glNamedFramebufferTexture(MinecraftClient.getInstance().getFramebuffer().fbo, GL_DEPTH_ATTACHMENT, MinecraftClient.getInstance().getFramebuffer().getDepthAttachment(), 0);
        MinecraftClient.getInstance().getProfiler().pop();
    }

    public void tick() {
        regionManager.tick(0);
    }
}
