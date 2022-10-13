package net.caffeinemc.sodium.render.chunk.raytrace;

import net.caffeinemc.sodium.render.chunk.draw.IndexBufferVK;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.cq.SVkCommandBuffer;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkBuffer;
import net.caffeinemc.sodium.vkinterop.vk.raytracing.SVkAccelerationStructure;
import net.caffeinemc.sodium.vkinterop.vk.raytracing.SVkBLAS;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4x3f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static net.caffeinemc.sodium.vkinterop.VkUtils.pointersOfElements;
import static org.lwjgl.system.MemoryStack.create;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;


//TODO: TRY USE VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR or something to not need to do a full rebuild of tlas every time
public class AccelerationSystem {
    SVkDevice device;
    IndexBufferVK indexBuffer = new IndexBufferVK(1000000, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR);

    public void chunkBuilt(int x, int y, int z, long mem, long quadCount) {
        FloatBuffer verts = MemoryUtil.memCallocFloat((int) (quadCount*4*3));
        for (long i = 0; i < quadCount*4*3; i++) {
            verts.put(MemoryUtil.memGetFloat(mem+i*4));
        }
        verts.rewind();
        BlasBuildData bbd = new BlasBuildData(device.m_alloc.createBuffer(4*3*4*quadCount,
                VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR,
                0,
                Float.BYTES, MemoryUtil.memAddress(verts)), new Vector3i(x,y,z));
        rebuilQueue.add(bbd);
    }

    public static class BlasBuildData {
        SVkBuffer vertexData;//NOTE:Can technically free this after it has been built since blas is self containing
        int quadCount;
        Vector3i sectionPos;
        BlasBuildData(SVkBuffer data, Vector3i pos) {
            if (data.size %(4*3*4) !=0) {
                throw new IllegalStateException("Data not modulo quad size ");
            }
            quadCount = (int) (data.size/(4*3*4));
            vertexData = data;
            sectionPos = pos;
        }
    }

    public static class BLAS {
        Vector3i sectionPos;
        SVkAccelerationStructure structure;
        Matrix4x3f transform;

        public BLAS(SVkAccelerationStructure blas, Vector3i sectionPos) {
            this.structure = blas;
            this.sectionPos = sectionPos;
            transform = new Matrix4x3f().translate(new Vector3f(sectionPos).mul(16));
        }
    }

    SVkBuffer scratchBuffer;//TODO: GET RID OF THIS AS IT CREATES RACE CONDITIONS AMOUG MANY OTHER ISSUES

    Map<Vector3i, BLAS> blass = new HashMap<>();
    Queue<BlasBuildData> rebuilQueue = new LinkedBlockingDeque<>();

    public AccelerationSystem(SVkDevice device) {
        this.device = device;

        //buildBlass();
        //rebuildTLAS();
    }

    public void tick() {
        if (rebuilQueue.size() == 0)
            return;
        buildBlass();
        rebuildTLAS();
    }

    private SVkBuffer makeBLASBuffer(long size) {
        return device.m_alloc.createBuffer(size,
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                256);
    }

    private void removeSection(Vector3i section) {
        if (blass.containsKey(section)) {
            BLAS old = blass.get(section);
            blass.remove(section);
            //TODO: FREE old
        }
    }
    static final MemoryStack innerStack = MemoryStack.create(MemoryUtil.memAlloc(1024*1024*1024));
    private void buildBlass() {
        try (MemoryStack stack = innerStack.push()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer pInfos =
                    VkAccelerationStructureBuildGeometryInfoKHR
                            .calloc(rebuilQueue.size(), stack);
            VkAccelerationStructureBuildRangeInfoKHR.Buffer pBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR
                    .calloc(rebuilQueue.size(), stack);
            LongBuffer pAccelerationStructures = stack.mallocLong(rebuilQueue.size());
            long scratchSize = 0;
            int i = -1;
            List<BLAS> newBlass = new LinkedList<>();
            for (BlasBuildData build : rebuilQueue) {
                i++;
                pBuildRangeInfo.position(i).primitiveCount();
                pInfos.position(i)
                        .sType$Default()
                        .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                        .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)//VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR and VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_KHR
                        .geometryCount(1)
                        .pGeometries(VkAccelerationStructureGeometryKHR
                                .calloc(1, stack)
                                .sType$Default()
                                .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
                                .geometry(VkAccelerationStructureGeometryDataKHR.calloc(stack)
                                        .triangles(VkAccelerationStructureGeometryTrianglesDataKHR
                                                .calloc(stack)
                                                .sType$Default()
                                                .vertexFormat(VK_FORMAT_R32G32B32_SFLOAT)
                                                .vertexData(build.vertexData.deviceAddressConst(stack, Float.BYTES))
                                                .vertexStride(Float.BYTES*3)
                                                .maxVertex(build.quadCount * 4)
                                                .indexType(VK_INDEX_TYPE_UINT32)
                                                .indexData(indexBuffer.indexBuffer.deviceAddressConst(stack, Integer.BYTES))))
                                .flags(VK_GEOMETRY_OPAQUE_BIT_KHR));
                VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                        .malloc(stack)
                        .sType$Default()
                        .pNext(NULL);
                vkGetAccelerationStructureBuildSizesKHR(
                        device.device,
                        VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                        pInfos.get(i),
                        stack.ints(build.quadCount * 2),
                        buildSizesInfo);
                scratchSize += buildSizesInfo.buildScratchSize();



                SVkBuffer blasBuffer = makeBLASBuffer(buildSizesInfo.accelerationStructureSize());
                _CHECK_(vkCreateAccelerationStructureKHR(device.device,
                                VkAccelerationStructureCreateInfoKHR
                                .calloc(stack)
                                .sType$Default()
                                .buffer(blasBuffer.buffer)//TODO: Allocate a buffer either from a pool or a fresh buffer or something
                                .offset(0)//If allocated from shared buffer, do this
                                .size(buildSizesInfo.accelerationStructureSize())
                                .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR), null,
                                pAccelerationStructures.position(i)),//TODO: CHECK IF POSITION IS WHAT
                        "Failed to create bottom-level acceleration structure");
                newBlass.add(new BLAS(new SVkAccelerationStructure(device, pAccelerationStructures.get(i), blasBuffer), build.sectionPos));
            }
            pInfos.rewind();

            if (scratchBuffer == null || scratchBuffer.size < scratchSize) {
                //Need to allocate a new scratch buffer
                //TODO:FIXME: free old buffer
                scratchBuffer = device.m_alloc.createBuffer(scratchSize,
                        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR |
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256);
            }

            i = -1;
            long scratchOffset = 0;
            for (BlasBuildData build : rebuilQueue) {
                i++;
                pInfos
                       .position((int) i)
                       .scratchData(scratchBuffer.deviceAddress(stack, scratchOffset, 256))
                       .dstAccelerationStructure(pAccelerationStructures.get(i));
            }
            VkCommandBuffer cmdBuf = device.createAndBeginSingleTimeBuffer();

            //TODO: MAKE BARRIERS AND FENCES INTO OBJECTS

            // Insert barrier to let BLAS build wait for the geometry data transfer from the staging buffer to the GPU
            vkCmdPipelineBarrier(cmdBuf,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, // <- copying of the geometry data from the staging buffer to the GPU buffer
                    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, // <- accessing the buffer for acceleration structure build
                    0, // <- no dependency flags
                    VkMemoryBarrier
                            .calloc(1, stack)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT) // <- GPU buffer was written to during the transfer
                            .dstAccessMask(
                                    VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR | // <- Accesses to the destination acceleration structures, and the scratch buffers
                                            VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR |
                                            VK_ACCESS_SHADER_READ_BIT), // <- Accesses to input buffers for the build (vertex, index, transform, aabb, or instance data)
                    null, null);

            // Issue build command to build all BLASes at once
            vkCmdBuildAccelerationStructuresKHR(
                    cmdBuf,
                    pInfos,
                    pointersOfElements(stack, pBuildRangeInfo));
            for (BLAS blas : newBlass) {
                removeSection(blas.sectionPos);
                blass.put(blas.sectionPos, blas);
            }
            //TODO: SYNCING ON THE FENCES FOR TLAS REBUILD
            long fence = device.submitCommandBuffer(cmdBuf, true, () -> {
                vkFreeCommandBuffers(device.device, device.transientCmdPool.pool, cmdBuf);
                //scratchBuffer.free();//TODO: Free scratch buffer
            });
            rebuilQueue.clear();
        }
    }


    private void rebuildTLAS() {
        try (MemoryStack stack = innerStack.push()) {
            VkAccelerationStructureInstanceKHR.Buffer instances = VkAccelerationStructureInstanceKHR
                    .calloc(blass.size(), stack);
            int i = -1;
            for (BLAS blas : blass.values()) {
                i++;
                // Query the BLAS device address to reference in the TLAS instance
                long blasDeviceAddress = vkGetAccelerationStructureDeviceAddressKHR(device.device,
                        VkAccelerationStructureDeviceAddressInfoKHR
                                .calloc(stack)
                                .sType$Default()
                                .accelerationStructure(blas.structure.structure));
                // set VkAccelerationStructureInstanceKHR properties
                instances
                        .position(i)
                        //.flags(VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR)
                        .accelerationStructureReference(blasDeviceAddress)
                        .mask(~0) // <- we do not want to mask-away any geometry, so use 0b11111111
                        .instanceCustomIndex(i)
                        .transform(VkTransformMatrixKHR
                                .calloc(stack)
                                .matrix(blas.transform.getTransposed(stack.mallocFloat(12))));
            }
            instances.rewind();

            // Create VkBuffer to hold the instance data
            SVkBuffer instanceData = device.m_alloc.createBuffer((long) instances.sizeof() * blass.size(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    16, // <- VUID-vkCmdBuildAccelerationStructuresKHR-pInfos-03715
                    instances.address0());

            // Create the build geometry info holding a single geometry for all instances
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer pInfos =
                    VkAccelerationStructureBuildGeometryInfoKHR
                            .calloc(1, stack)
                            .sType$Default()
                            .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                            .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                            .pGeometries(VkAccelerationStructureGeometryKHR
                                    .calloc(1, stack)
                                    .sType$Default()
                                    .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR) // <- VUID-VkAccelerationStructureBuildGeometryInfoKHR-type-03790
                                    .geometry(VkAccelerationStructureGeometryDataKHR
                                            .calloc(stack)
                                            .instances(VkAccelerationStructureGeometryInstancesDataKHR
                                                    .calloc(stack)
                                                    .sType$Default()
                                                    .data(instanceData.deviceAddressConst(stack, 16)))) // <- VUID-vkCmdBuildAccelerationStructuresKHR-pInfos-03715
                                    .flags(VK_GEOMETRY_OPAQUE_BIT_KHR))
                            .geometryCount(1);

            // Query necessary sizes for the acceleration structure buffer and for the scratch buffer
            VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                    .malloc(stack)
                    .sType$Default()
                    .pNext(NULL);

            vkGetAccelerationStructureBuildSizesKHR(
                    device.device,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    pInfos.get(0),
                    stack.ints(blass.size()),
                    buildSizesInfo);

            // Create a buffer that will hold the final TLAS
            SVkBuffer accelerationStructureBuffer = device.m_alloc.createBuffer(buildSizesInfo.accelerationStructureSize(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    256 // <- VUID-VkAccelerationStructureCreateInfoKHR-offset-03734
                    );

            // Create a TLAS object (not currently built)
            LongBuffer pAccelerationStructure = stack.mallocLong(1);
            _CHECK_(vkCreateAccelerationStructureKHR(device.device, VkAccelerationStructureCreateInfoKHR
                            .calloc(stack)
                            .sType$Default()
                            .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                            .size(buildSizesInfo.accelerationStructureSize())
                            .buffer(accelerationStructureBuffer.buffer), null, pAccelerationStructure),
                    "Failed to create top-level acceleration structure");

            // Create a scratch buffer for the TLAS build
            SVkBuffer scratchBuffer = device.m_alloc.createBuffer(buildSizesInfo.buildScratchSize(),
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR |
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    256//FIXME
            );

            // fill missing/remaining info into the build geometry info to
            // be able to build the TLAS instance.
            pInfos
                    .scratchData(scratchBuffer.deviceAddress(stack, 0, 256))
                    .dstAccelerationStructure(pAccelerationStructure.get(0));
            VkCommandBuffer cmdBuf = device.createAndBeginSingleTimeBuffer();

            // insert barrier to let TLAS build wait for the instance data transfer from the staging buffer to the GPU
            vkCmdPipelineBarrier(cmdBuf,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, // <- copying of the instance data from the staging buffer to the GPU buffer
                    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, // <- accessing the buffer for acceleration structure build
                    0, // <- no dependency flags
                    VkMemoryBarrier
                            .calloc(1, stack)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT) // <- GPU buffer was written to during the transfer
                            .dstAccessMask(
                                    VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR | // <- Accesses to the destination acceleration structures, and the scratch buffers
                                            VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR |
                                            VK_ACCESS_SHADER_READ_BIT), // <- Accesses to input buffers for the build (vertex, index, transform, aabb, or instance data)
                    null, null);

            // issue build command
            vkCmdBuildAccelerationStructuresKHR(
                    cmdBuf,
                    pInfos,
                    pointersOfElements(stack,
                            VkAccelerationStructureBuildRangeInfoKHR
                                    .calloc(1, stack)
                                    .primitiveCount(blass.size()))); // <- number of instances/BLASes!

            // insert barrier to let tracing wait for the TLAS build
            vkCmdPipelineBarrier(cmdBuf,
                    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                    VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
                    0, // <- no dependency flags
                    VkMemoryBarrier
                            .calloc(1, stack)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                            .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR),
                    null,
                    null);

            // finally submit command buffer and register callback when fence signals to
            // dispose of resources
            device.submitCommandBuffer(cmdBuf, true, () -> {
                vkFreeCommandBuffers(device.device, device.transientCmdPool.pool, cmdBuf);
                //scratchBuffer.free();//TODO:FREE
                // the TLAS is completely self-contained after build, so
                // we can free the instance data.
                //instanceData.free();//TODO:FREE
            });

            //return new AccelerationStructure(pAccelerationStructure.get(0), new Rc<>(accelerationStructureBuffer).inc());

        }
    }


}
