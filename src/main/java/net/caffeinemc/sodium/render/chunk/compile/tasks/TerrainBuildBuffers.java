package net.caffeinemc.sodium.render.chunk.compile.tasks;

import java.util.Arrays;
import net.caffeinemc.sodium.render.buffer.VertexData;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.chunk.compile.buffers.ChunkMeshBuilder;
import net.caffeinemc.sodium.render.chunk.compile.buffers.DefaultChunkMeshBuilder;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.state.BuiltChunkGeometry;
import net.caffeinemc.sodium.render.chunk.state.ChunkPassModel;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderData;
import net.caffeinemc.sodium.render.terrain.format.AccelerationBufferSink;
import net.caffeinemc.sodium.render.terrain.format.AccelerationSink;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.render.terrain.quad.properties.ChunkMeshFace;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferBuilder;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.minecraft.client.render.RenderLayer;
import org.lwjgl.system.MemoryUtil;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class TerrainBuildBuffers {
    private final ChunkMeshBuilder[] delegates;
    private final VertexBufferBuilder[][] vertexBuffers;
    public final AccelerationBufferSink accelerationSink;
    
    private final TerrainVertexType vertexType;
    
    private final ChunkRenderPassManager renderPassManager;
    
    public TerrainBuildBuffers(TerrainVertexType vertexType, ChunkRenderPassManager renderPassManager) {
        this.vertexType = vertexType;
        this.renderPassManager = renderPassManager;
        
        int renderPassCount = renderPassManager.getRenderPassCount();
        this.delegates = new ChunkMeshBuilder[renderPassCount];
        this.vertexBuffers = new VertexBufferBuilder[renderPassCount][];
        
        for (var renderPass : renderPassManager.getAllRenderPasses()) {
            var vertexBuffers = new VertexBufferBuilder[ChunkMeshFace.COUNT];
            
            for (int i = 0; i < vertexBuffers.length; i++) {
                vertexBuffers[i] = new VertexBufferBuilder(this.vertexType.getBufferVertexFormat(), 512 * 1024);
            }
            
            this.vertexBuffers[renderPass.getId()] = vertexBuffers;
        }
        accelerationSink = new AccelerationBufferSink();
    }
    
    public void init(ChunkRenderData.Builder renderData) {
        accelerationSink.reset();
        for (var renderPass : this.renderPassManager.getAllRenderPasses()) {
            int passId = renderPass.getId();
            var buffers = this.vertexBuffers[passId];
            var sinks = new TerrainVertexSink[buffers.length];

            for (int i = 0; i < sinks.length; i++) {
                var buffer = buffers[i];
                buffer.reset();
                
                sinks[i] = this.vertexType.createBufferWriter(buffer);
                if (sinks[i] instanceof AccelerationSink as) {
                    as.setAccelerationBuffer(accelerationSink);
                }
            }

            this.delegates[passId] = new DefaultChunkMeshBuilder(sinks, renderData);
        }
    }
    
    /**
     * Return the {@link ChunkMeshBuilder} for the given {@link RenderLayer} as mapped by the
     * {@link ChunkRenderPassManager} for this render context.
     */
    public ChunkMeshBuilder get(RenderLayer layer) {
        return this.delegates[this.renderPassManager.getRenderPassForLayer(layer).getId()];
    }
    
    public BuiltChunkGeometry buildGeometry() {
        VertexBufferBuilder[][] buffers = this.vertexBuffers;
        
        var capacity = Arrays.stream(buffers)
                             .flatMapToInt((vertexBuffers) -> Arrays.stream(vertexBuffers)
                                                                    .mapToInt(VertexBufferBuilder::getCount))
                             .sum();
        
        if (capacity <= 0) {
            return BuiltChunkGeometry.empty();
        }
        
        var vertexFormat = this.vertexType.getCustomVertexFormat();
        var totalVertexCount = 0;
        
        NativeBuffer chunkVertexBuffer = null;
        int chunkVertexBufferPosition = 0;
        
        var models = new ChunkPassModel[buffers.length];
        
        for (int i = 0; i < buffers.length; i++) {
            VertexBufferBuilder[] sidedBuffers = buffers[i];
    
            long[] modelPartRanges = new long[ChunkMeshFace.COUNT];
            boolean isEmpty = true;
            
            for (ChunkMeshFace facing : ChunkMeshFace.VALUES) {
                var index = facing.ordinal();
                
                var sidedVertexBuffer = sidedBuffers[index];
                var sidedVertexCount = sidedVertexBuffer.getCount();
                
                if (sidedVertexCount == 0) {
                    modelPartRanges[index] = BufferSegment.INVALID;
                    continue;
                }
                
                if (chunkVertexBuffer == null) {
                    // lazy allocation
                    chunkVertexBuffer = new NativeBuffer(capacity * vertexFormat.stride());
                }
                
                int length = sidedVertexBuffer.getWriterPosition();
                MemoryUtil.memCopy(
                        MemoryUtil.memAddress(sidedVertexBuffer.getDirectBuffer()),
                        chunkVertexBuffer.getAddress() + chunkVertexBufferPosition,
                        length
                );
                chunkVertexBufferPosition += length;
                
                modelPartRanges[index] = BufferSegment.createKey(sidedVertexCount, totalVertexCount);
                isEmpty = false;
                
                totalVertexCount += sidedVertexCount;
            }
            
            if (!isEmpty) {
                models[i] = new ChunkPassModel(modelPartRanges);
            }
        }
        
        if (chunkVertexBuffer != null) {
            // if the buffer is there, there's at least one model entry that's non-null
            return new BuiltChunkGeometry(new VertexData(vertexFormat, chunkVertexBuffer), models, accelerationSink.bake());
        }
        
        return BuiltChunkGeometry.empty();
    }
    
    public void destroy() {
        for (VertexBufferBuilder[] builders : this.vertexBuffers) {
            for (VertexBufferBuilder builder : builders) {
                builder.destroy();
            }
        }
        for (var as : this.delegates) {
            for(var t : ChunkMeshFace.values()) {
                if (as!=null && as.getVertexSink(t) instanceof AccelerationSink as2) {
                    as2.setAccelerationBuffer(null);
                }
            }
        }
        //accelerationSink.free();//TODO: FIX THIS: IDK WHY THIS CAUSES A DOUBLE FREE
    }
}
