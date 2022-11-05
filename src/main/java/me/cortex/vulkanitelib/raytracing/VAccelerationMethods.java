package me.cortex.vulkanitelib.raytracing;

import me.cortex.vulkanitelib.Pair;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import org.joml.Matrix4x3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.create;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class VAccelerationMethods {
    private final VVkDevice device;

    public VAccelerationMethods(VVkDevice device) {
        this.device = device;
    }

    public static Pair<VkAccelerationStructureGeometryDataKHR, VkAccelerationStructureGeometryInstancesDataKHR> setupInstances(MemoryStack stack, IGeometryConsumer<VkAccelerationStructureGeometryInstancesDataKHR> consumer) {
        var asgid =VkAccelerationStructureGeometryInstancesDataKHR
                .calloc(stack)
                .sType$Default();
        consumer.fill(stack, asgid);
        var asgd = VkAccelerationStructureGeometryDataKHR
                .calloc(stack)
                .instances(asgid);
        return new Pair<>(asgd, asgid);
    }
    public static Pair<VkAccelerationStructureGeometryDataKHR, VkAccelerationStructureGeometryTrianglesDataKHR> setupTriangles(MemoryStack stack, IGeometryConsumer<VkAccelerationStructureGeometryTrianglesDataKHR> consumer) {
        var asgid = VkAccelerationStructureGeometryTrianglesDataKHR
                .calloc(stack)
                .sType$Default();
        consumer.fill(stack, asgid);//.flags(VK_GEOMETRY_OPAQUE_BIT_KHR)
        var asgd = VkAccelerationStructureGeometryDataKHR
                .calloc(stack)
                .triangles(asgid);
        return new Pair<>(asgd, asgid);
    }

    public interface IGeometryConsumer<T>{void fill(MemoryStack stack, T structure);}
    public interface IGeometrySetup<T>{Pair<VkAccelerationStructureGeometryDataKHR, T> setup(MemoryStack stack, IGeometryConsumer<T> consumer);}
    public <T>  Pair<VVkAccelerationStructure, VVkBuffer> fillCreationData(MemoryStack stack, VkAccelerationStructureBuildGeometryInfoKHR pInfo, int type, int buildFlags, int geoType, int[] maxPrimitives, IGeometrySetup<T> settuper, List<IGeometryConsumer<T>> geometries) {
        VkAccelerationStructureGeometryKHR.Buffer geos = VkAccelerationStructureGeometryKHR
                .calloc(geometries.size(), stack);
        for (var consumer : geometries) {
            VkAccelerationStructureGeometryKHR asg = geos.get();
            var data = settuper.setup(stack, consumer);
            asg.sType$Default()
                    .geometryType(geoType) // <- VUID-VkAccelerationStructureBuildGeometryInfoKHR-type-03790
                    .geometry(data.a) // <- VUID-vkCmdBuildAccelerationStructuresKHR-pInfos-03715
                    ;
        }
        geos.rewind();
        // Create the build geometry info holding a single geometry for all instances

        pInfo.sType$Default()
                .type(type)
                .flags(buildFlags)
                .pGeometries(geos)
                .geometryCount(geometries.size())
        ;

        // Query necessary sizes for the acceleration structure buffer and for the scratch buffer
        VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                .calloc(stack)
                .sType$Default();

        vkGetAccelerationStructureBuildSizesKHR(
                device.device,
                VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                pInfo,
                stack.ints(maxPrimitives),
                buildSizesInfo);

        VVkBuffer accelerationBuffer = device.allocator.createBuffer(buildSizesInfo.accelerationStructureSize(),256,
                VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0
        );

        LongBuffer pAccelerationStructure = stack.mallocLong(1);
        _CHECK_(vkCreateAccelerationStructureKHR(device.device, VkAccelerationStructureCreateInfoKHR
                        .calloc(stack)
                        .sType$Default()
                        .type(type)
                        .size(buildSizesInfo.accelerationStructureSize())
                        .buffer(accelerationBuffer.buffer), null, pAccelerationStructure),
                "Failed to create acceleration acceleration structure");

        // Create a scratch buffer for the build
        VVkBuffer scratchBuffer = device.allocator.createBuffer(buildSizesInfo.buildScratchSize(), 256,// TODO: actually dynamically get this minAccelerationStructureScratchOffsetAlignment
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0
        );

        // fill missing/remaining info into the build geometry info
        pInfo.scratchData(scratchBuffer.deviceAddress(stack, 0))
                .dstAccelerationStructure(pAccelerationStructure.get(0));
        return new Pair<>(new VVkAccelerationStructure(device, pAccelerationStructure.get(0), accelerationBuffer), scratchBuffer);
    }


    public record TriangleGeometry(int indexType, int maxIndex, int primitiveCount, int vertexFormat,  int vertexStride,
                                   VkDeviceOrHostAddressConstKHR indices, VkDeviceOrHostAddressConstKHR vertices) {}
    public record BLASBuildData(int flags, List<TriangleGeometry> geometries) {}

    public List<VVkAccelerationStructure> createBLASs(List<BLASBuildData> buildList) {
        return createBLASs(buildList, null);
    }
    public synchronized List<VVkAccelerationStructure> createBLASs(List<BLASBuildData> buildList, Runnable fence) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(buildList.size(), stack);
            PointerBuffer buildRanges = stack.mallocPointer(buildList.size());
            List<VVkBuffer> scratchBuffers = new LinkedList<>();
            List<VVkAccelerationStructure> retAcceleration = new LinkedList<>();
            for (var buildData : buildList) {
                VkAccelerationStructureBuildRangeInfoKHR.Buffer rangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(buildData.geometries.size(), stack);
                var data = fillCreationData(stack, buildInfos.get(), VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR,
                        buildData.flags, VK_GEOMETRY_TYPE_TRIANGLES_KHR,
                        buildData.geometries.stream().mapToInt(a->a.primitiveCount).toArray(),
                        VAccelerationMethods::setupTriangles,
                        buildData.geometries.stream().map(geo-> (IGeometryConsumer<VkAccelerationStructureGeometryTrianglesDataKHR>)(stack2, structure) -> {
                            structure.vertexFormat(geo.vertexFormat)
                                    .vertexData(geo.vertices)
                                    .vertexStride(geo.vertexStride)
                                    .maxVertex(geo.maxIndex)
                                    .indexType(geo.indexType)
                                    .indexData(geo.indices);
                        }).collect(Collectors.toList())
                );
                scratchBuffers.add(data.b);
                retAcceleration.add(data.a);
                buildData.geometries.forEach(geo->rangeInfo.get().primitiveCount(geo.primitiveCount));
                rangeInfo.rewind();
                buildRanges.put(rangeInfo);
            }
            buildInfos.rewind();
            buildRanges.rewind();
            device.singleTimeCommand(cmd -> {
                vkCmdPipelineBarrier(cmd.buffer,
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
                        cmd.buffer,
                        buildInfos,
                        buildRanges);

                // insert barrier to let tracing wait for the TLAS build
                vkCmdPipelineBarrier(cmd.buffer,
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
            }, ()->{
                scratchBuffers.forEach(VVkBuffer::free);//Free scratch buffers
                if (fence != null)
                    fence.run();
            });
            return retAcceleration;
        }
    }

    public record InstanceData(VVkAccelerationStructure blas, int mask, int index, int flags, Matrix4x3f transform){}
    public record TLASBuildData(int flags, List<InstanceData> instances) {}

    public VVkAccelerationStructure createTLAS(TLASBuildData buildData) {//TODO: be able to build multiple tlas's at once?
        return createTLAS(buildData, null);
    }

    private static final MemoryStack bigStack = MemoryStack.create(50000000);
    public synchronized VVkAccelerationStructure createTLAS(TLASBuildData buildData, Runnable fence) {//TODO: be able to build multiple tlas's at once?
        try (MemoryStack stack = bigStack.push()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);

            VkAccelerationStructureInstanceKHR.Buffer instanceData =  VkAccelerationStructureInstanceKHR.calloc(buildData.instances.size(), stack);
            for (var data : buildData.instances) {
                instanceData.get()
                        .accelerationStructureReference(data.blas.deviceAddress())
                        .mask(data.mask) // <- we do not want to mask-away any geometry, so use 0b11111111
                        .flags(data.flags)
                        .instanceCustomIndex(data.index)
                        .transform(VkTransformMatrixKHR
                                .calloc(stack)
                                .matrix(data.transform.getTransposed(stack.mallocFloat(12))));
            }
            instanceData.rewind();

            VVkBuffer instanceDataBuffer = device.allocator.createBuffer(memByteBuffer(instanceData), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);


            var data = fillCreationData(stack, buildInfos.get(), VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR,
                    buildData.flags, VK_GEOMETRY_TYPE_INSTANCES_KHR,
                    new int[]{buildData.instances.size()},
                    VAccelerationMethods::setupInstances,
                    List.of((stack2, structure) ->structure.data(instanceDataBuffer.deviceAddressConst(stack)))
            );
            buildInfos.rewind();

            device.singleTimeCommand(cmd -> {
                vkCmdPipelineBarrier(cmd.buffer,
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
                        cmd.buffer,
                        buildInfos,
                        stack.pointers(VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack).primitiveCount(buildData.instances.size()))//TODO: CHECK THIS IS CORRECT
                );

                // insert barrier to let tracing wait for the TLAS build
                vkCmdPipelineBarrier(cmd.buffer,
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
            }, ()->{
                data.b.free();//Free scratch buffer
                instanceDataBuffer.free();//TODO: maybe make this so it doesnt have to be free if want an update thing, dont need to constantly remake the buffer
                if (fence != null)
                    fence.run();
            });
            return data.a;
        }
    }


}
