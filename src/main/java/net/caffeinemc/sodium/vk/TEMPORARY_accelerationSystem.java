package net.caffeinemc.sodium.vk;

import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.raytracing.VAccelerationMethods;
import me.cortex.vulkanitelib.raytracing.VVkAccelerationStructure;
import net.caffeinemc.sodium.render.chunk.draw.VulkanChunkRenderer;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4x3f;
import org.lwjgl.system.MemoryStack;

import java.util.*;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.VK12.*;

public class TEMPORARY_accelerationSystem {
    public VVkAccelerationStructure tlas;
    public VVkBuffer gpuBlasRefBuffer;

    private record BlasData(ChunkSectionPos pos, VVkAccelerationStructure blas, VVkBuffer vertData) {
        public void free() {
            blas.free();
            vertData.free();
        }
    }
    private final Map<ChunkSectionPos, BlasData> loadedSections = new HashMap<>();
    private final Map<ChunkSectionPos, AccelerationData> rebuildQueue = new HashMap<>();
    public void enqueueChunkRebuild(ChunkSectionPos pos, AccelerationData data) {
        synchronized (rebuildQueue) {
            var old = rebuildQueue.put(pos, data.copy());
            if (old != null) old.delete();
        }
    }


    private void tickRebuilds() {
        List<VAccelerationMethods.BLASBuildData> blasBuildData = new LinkedList<>();
        List<VVkBuffer> vertexPositionData = new LinkedList<>();
        List<ChunkSectionPos> positions = new LinkedList<>();
        try (MemoryStack stack = MemoryStack.stackPush()){
            for (var rebuild : rebuildQueue.entrySet()) {
                VVkBuffer triangleData = VulkanContext.device.allocator.createBuffer(rebuild.getValue().buffer.getDirectBuffer(),
                        VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                rebuild.getValue().delete();
                vertexPositionData.add(triangleData);
                blasBuildData.add(new VAccelerationMethods.BLASBuildData(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR, List.of(
                        new VAccelerationMethods.TriangleGeometry(
                                VK_INDEX_TYPE_UINT32,
                                rebuild.getValue().quadCount*4,
                                rebuild.getValue().quadCount*2,
                                VK_FORMAT_R32G32B32_SFLOAT,
                                4*3,
                                VulkanChunkRenderer.indexBuffer.indexBuffer.deviceAddressConst(stack),
                                triangleData.deviceAddressConst(stack)
                        )
                )));
                positions.add(rebuild.getKey());
            }
            rebuildQueue.clear();
            var newBlass = VulkanContext.device.accelerator.createBLASs(blasBuildData, () -> {
                vertexPositionData.forEach(VVkBuffer::free);
            });
            {
                var posIter = positions.iterator();
                var blasIter = newBlass.iterator();
                while (posIter.hasNext()) {
                    var pos = posIter.next();
                    var blas = blasIter.next();
                    var oldBlas = loadedSections.put(pos, new BlasData(pos, blas, null));
                    if (oldBlas != null) oldBlas.free();
                }
            }
        }
    }

    private void rebuildTLAS() {//TODO: maybe make this somehow _update_ the tlas instead of rebuilding the whole thing
        if (tlas != null) {
            tlas.free();
        }

        if (gpuBlasRefBuffer == null || gpuBlasRefBuffer.size<loadedSections.size()* 8L) {
            if (gpuBlasRefBuffer != null)
                gpuBlasRefBuffer.free();
            gpuBlasRefBuffer = VulkanContext.device.allocator.createBuffer(loadedSections.size()* 8L, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT|VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        }
        var ref = gpuBlasRefBuffer.map().asLongBuffer();


        List<VAccelerationMethods.InstanceData> instanceData = new LinkedList<>();
        int i = 0;
        for (var entry : loadedSections.values()) {
            instanceData.add(new VAccelerationMethods.InstanceData(
                    entry.blas,
                    ~0,
                    i++,
                    VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_KHR,
                    new Matrix4x3f().translate(entry.pos.getMinX(),entry.pos.getMinY(),entry.pos.getMinZ())
            ));
            ref.put(entry.vertData.bufferAddress());
        }
        gpuBlasRefBuffer.unmap();
        tlas = VulkanContext.device.accelerator.createTLAS(new VAccelerationMethods.TLASBuildData(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR, instanceData));
    }

    public boolean tick() {
        synchronized (rebuildQueue) {
            if (rebuildQueue.isEmpty())
                return false;
            vkQueueWaitIdle(VulkanContext.device.fetchQueue().queue);
            tickRebuilds();
            rebuildTLAS();

            return true;
        }
    }
}
/*

        synchronized (VulkanContext.device) {
            if (buffers.accelerationSink.position != 0) {
                VVkBuffer triangleData = VulkanContext.device.allocator.createBuffer(buffers.accelerationSink.buffer.getDirectBuffer().limit((int) buffers.accelerationSink.position),
                        VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var acc = VulkanContext.device.accelerator.createBLASs(List.of(new VAccelerationMethods.BLASBuildData(
                            VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR,
                            List.of(
                            new VAccelerationMethods.TriangleGeometry(
                                    VK_INDEX_TYPE_UINT32,
                                    (int) ((buffers.accelerationSink.position / (3 * 4 * 4)) * 4),
                                    (int) ((buffers.accelerationSink.position / (3 * 4 * 4)) * 2),
                                    VK_FORMAT_R32G32B32_SFLOAT,
                                    4 * 3,
                                    VulkanChunkRenderer.indexBuffer.indexBuffer.deviceAddressConst(stack),
                                    triangleData.deviceAddressConst(stack)
                            )))));
                    System.err.println(acc.get(0).buffer.size);
                }
            }
        }

 */