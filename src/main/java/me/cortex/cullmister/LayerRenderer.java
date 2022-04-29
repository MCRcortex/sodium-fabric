package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.commandListStuff.CommandList;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.Section;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import net.caffeinemc.gfx.api.pipeline.state.BlendFunc;
import net.caffeinemc.gfx.opengl.GlEnum;
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
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import static net.caffeinemc.gfx.api.array.attribute.VertexAttributeFormat.UNSIGNED_INT;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.ARBDrawIndirect.glDrawElementsIndirect;
import static org.lwjgl.opengl.ARBDrawIndirect.nglDrawElementsIndirect;
import static org.lwjgl.opengl.ARBIndirectParameters.*;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDrawElements;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30C.GL_QUERY_WAIT;
import static org.lwjgl.opengl.GL32C.glDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL33.glSamplerParameteri;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42C.GL_ATOMIC_COUNTER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.nglMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.NVBindlessMultiDrawIndirectCount.nglMultiDrawElementsIndirectBindlessCountNV;

public class LayerRenderer {
    Shader debugdrawer;
    int mipsampler = glGenSamplers();
    int texsampler = glGenSamplers();
    int lightsampler = glGenSamplers();
    CommandList listTest;
    public LayerRenderer() {
        glSamplerParameteri(mipsampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(mipsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);

        glSamplerParameteri(texsampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        glSamplerParameteri(texsampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);

        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        debugdrawer = new Shader("""
                #version 460
                #extension GL_NV_command_list : enable
                layout(commandBindableNV) uniform;
                //uniform mat4 viewModelProjectionTranslate;
                
                layout(std140,binding=0) uniform scenemat {
                    mat4 viewModelProjectionTranslate;
                    //sampler2D texColor;
                };
                
                layout(location = 0) in vec3 Offset;
                layout(location = 1) in vec3 Pos;
                layout(location = 2) in vec4 colourA;
                layout(location = 3) in vec2 textpos;
                layout(location = 4) in vec2 lightcourd;
                                                     
                                                     
                                    
                out vec4 v_Color;
                out vec2 v_TexCoord;
                out vec2 v_LightCoord;
                //out vec2 v_TexScalar;
                void main(){
                    gl_Position = viewModelProjectionTranslate*vec4((Pos*16 + 8)+Offset
                    , 1.0);
                    v_TexCoord = textpos;
                    v_Color = colourA;
                    v_LightCoord = lightcourd;
                }
                """, """
                #version 460
                #extension GL_NV_command_list : enable
                layout(commandBindableNV) uniform;
                
                layout(std140,binding=0) uniform scenemat {
                    mat4 viewModelProjectionTranslate;
                    //sampler2D texColor;
                };
                
                layout(location=0,index=0) out vec4 colour;
                in vec4 v_Color; // The interpolated vertex color
                in vec2 v_TexCoord; // The interpolated block texture coordinates
                in vec2 v_LightCoord; // The interpolated light map texture coordinates
                //in vec2 v_TexScalar;//Texture scale factor
                                
                layout(binding = 0) uniform sampler2D u_BlockTex; // The block texture sampler
                layout(binding = 1) uniform sampler2D u_LightTex; // The light map texture sampler
                                
                void main() {
                    colour = texture(u_BlockTex, v_TexCoord);
                    return;
                    colour.rgb = vec3(0,1,1);
                    return;
                    //colour = v_Color;return;
                    vec4 c = texture(u_BlockTex, v_TexCoord);
                    if (c.a < 0.5)
                        discard;
                    vec4 light = texture(u_LightTex, v_LightCoord);
                    colour = vec4((c.rgb * light.rgb) * v_Color.rgb * v_Color.a, c.a);
                }
                """);
    }

    public void being(RenderLayer layer) {
        MinecraftClient.getInstance().getProfiler().push("bind");
        debugdrawer.bind();
        RenderSystem.enableTexture();
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        GL45C.glBindTextureUnit(0, tm.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId());
        LightmapTextureManagerAccessor lightmapTextureManager =
                ((LightmapTextureManagerAccessor) MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager());
        GL45C.glBindTextureUnit(1, lightmapTextureManager.getTexture().getGlId());
        GL45C.glBindSampler(1, lightsampler);
        glEnable(GL_DEPTH_TEST);
        sectioncount = 0;
        MinecraftClient.getInstance().getProfiler().pop();
    }

    public void end() {
        debugdrawer.unbind();
        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
        RenderSystem.disableTexture();
    }

    long sectioncount;
    public void superdebugtestrender(int pass, Region region, ChunkRenderMatrices renderMatrices, Vector3f pos) {
        /*
        if (listTest == null)
            listTest = new CommandList();;
        //glDrawElements(GL_TRIANGLES, 6*1000, GL_UNSIGNED_SHORT, 0);
        GL45C.glBindSampler(0, mipsampler);
        listTest.draw(RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS, 4).getId(), region.vertexData.id, region.drawData.drawMeta.id, new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView()).translate(new Vector3f().set(pos).negate()));


        if (pass == 1) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlEnum.from(BlendFunc.SrcFactor.SRC_ALPHA), GlEnum.from(BlendFunc.DstFactor.ONE_MINUS_SRC_ALPHA), GlEnum.from(BlendFunc.SrcFactor.ONE), GlEnum.from(BlendFunc.DstFactor.ONE_MINUS_SRC_ALPHA));
            //nglMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, 5 * 4 * 3 * 10000, 4 * 3, (int) (region.sectionCount*2), 0);
            //nglMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_INT, 5 * 4 * 3 * 10000, 4 * 3, region.cacheDrawCounts[3], 0);
            nglMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, 5 * 4 * 3 * 10000, 4 * 3,  region.cacheDrawCounts[3], 0);//(int) (region.sectionCount*2);
            RenderSystem.disableBlend();
        }
         */

        /*
        long ptr = region.drawData.drawCounts.mappedNamedPtr(GL_READ_ONLY);
        int count = MemoryUtil.memGetInt(ptr);
        region.drawData.drawCounts.unmapNamed();
        if (count != 0)
            nglMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, count, 0);

         */

        MinecraftClient.getInstance().getProfiler().pop();
        //glEndConditionalRender();
        //region.drawData.drawCounts.unmapNamed();
    }
}
