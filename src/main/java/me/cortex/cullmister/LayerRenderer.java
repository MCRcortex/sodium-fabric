package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.Section;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import net.caffeinemc.sodium.interop.vanilla.mixin.LightmapTextureManagerAccessor;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import org.joml.Matrix4f;
import org.joml.Random;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.nglMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL32C.glDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL33.glSamplerParameteri;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42C.GL_ATOMIC_COUNTER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_COMMAND_BARRIER_BIT;

public class LayerRenderer {
    Shader debugdrawer;
    int mipsampler = glGenSamplers();
    int lightsampler = glGenSamplers();
    public LayerRenderer() {
        glSamplerParameteri(mipsampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(mipsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);


        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        debugdrawer = new Shader("""
                #version 460
                uniform mat4 viewModelProjectionTranslate;
                layout(location = 0) in vec3 Offset;
                layout(location = 1) in vec3 Pos;
                layout(location = 2) in vec4 colourA;
                layout(location = 3) in vec2 textpos;
                layout(location = 4) in vec2 lightcourd;
                                                     
                                                     
                                    
                out vec4 v_Color;
                out vec2 v_TexCoord;
                out vec2 v_LightCoord;
                void main(){
                    gl_Position = viewModelProjectionTranslate*vec4((Pos*16 + 8)+Offset, 1.0);
                    v_TexCoord = textpos;
                    v_Color = colourA;
                    v_LightCoord = lightcourd;
                }
                """, """
                #version 460
                out vec4 colour;
                in vec4 v_Color; // The interpolated vertex color
                in vec2 v_TexCoord; // The interpolated block texture coordinates
                in vec2 v_LightCoord; // The interpolated light map texture coordinates
                                
                layout(binding = 0) uniform sampler2D u_BlockTex; // The block texture sampler
                layout(binding = 1) uniform sampler2D u_LightTex; // The light map texture sampler
                                
                void main() {
                    vec4 c = texture(u_BlockTex, v_TexCoord);
                    if (c.a < 0.5)
                        discard;
                    vec4 sampleLightTex = texture(u_LightTex, v_LightCoord);
                
                    vec4 diffuseColor = (c * sampleLightTex);
                    diffuseColor *= v_Color;
                    colour = diffuseColor;
                }
                """);
    }

    public void being(RenderLayer layer) {
        debugdrawer.bind();
        RenderSystem.enableTexture();
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        GL45C.glBindTextureUnit(0, tm.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId());
        LightmapTextureManagerAccessor lightmapTextureManager =
                ((LightmapTextureManagerAccessor) MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager());
        GL45C.glBindTextureUnit(1, lightmapTextureManager.getTexture().getGlId());
        GL45C.glBindSampler(0, mipsampler);
        GL45C.glBindSampler(1, lightsampler);
        glEnable(GL_DEPTH_TEST);
        sectioncount = 0;
    }

    public void end() {
        debugdrawer.unbind();
        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
        RenderSystem.disableTexture();
    }

    long sectioncount;
    public void superdebugtestrender(int renderId, Region region, ChunkRenderMatrices renderMatrices, Vector3f pos) {
        MinecraftClient.getInstance().getProfiler().push("bind");
        region.vao.bind();
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, region.drawData.drawCounts.id);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, region.drawData.drawCommands.id);
        debugdrawer.setUniform("viewModelProjectionTranslate", new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView()).translate(new Vector3f().set(pos).negate()));
        //long ptr = region.drawData.drawCounts.mappedNamedPtr(GL15C.GL_READ_ONLY);
        //System.err.println(MemoryUtil.memGetInt(ptr));
        //region.drawData.drawCounts.unmapNamed();

        MinecraftClient.getInstance().getProfiler().swap("draw");
        //TODO: NOTE: This command is EXTREAMLY SLOW in the graphics pipeline EVEN IF NOT DRAWING ANY TRIANGLES
        //  This i think is due to the max draw count thing
        // TODO: DO CONDITIONAL RENDERING FOR REGIONS
        nglMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_INT,0,0,32*32*Region.HEIGHT,0);

        MinecraftClient.getInstance().getProfiler().pop();
        region.vao.unbind();
    }
}
