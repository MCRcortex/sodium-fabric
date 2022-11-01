package me.cortex.vulkanitelib.raytracing;

import me.cortex.vulkanitelib.Pair;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.lang.reflect.Member;
import java.nio.LongBuffer;
import java.util.List;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
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
    public <T>  Pair<VVkAccelerationStructure, VVkBuffer> fillCreationData(MemoryStack stack, VkAccelerationStructureBuildGeometryInfoKHR pInfo, int type, int buildFlags, int geoType, int maxPrimatives, IGeometrySetup<T> settuper, List<IGeometryConsumer<T>> geometries) {
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
                stack.ints(maxPrimatives),
                buildSizesInfo);

        VVkBuffer accelerationBuffer = device.allocator.createBuffer(buildSizesInfo.accelerationStructureSize(),
                VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
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
        VVkBuffer scratchBuffer = device.allocator.createBuffer(buildSizesInfo.buildScratchSize(),
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        // fill missing/remaining info into the build geometry info
        pInfo.scratchData(scratchBuffer.deviceAddress(stack, 0))
                .dstAccelerationStructure(pAccelerationStructure.get(0));
        return new Pair<>(new VVkAccelerationStructure(device, pAccelerationStructure.get(0), accelerationBuffer), scratchBuffer);
    }


    public VVkAccelerationStructure createBLAS() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);
            var data = fillCreationData(stack, buildInfos.get(0), VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR,
                    VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR, VK_GEOMETRY_TYPE_TRIANGLES_KHR,
                    1000,
                    VAccelerationMethods::setupTriangles,
                    null
            );
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
                        null); // <- number of instances/BLASes!
//pointersOfElements(stack,
//                                VkAccelerationStructureBuildRangeInfoKHR
//                                        .calloc(1, stack)
//                                        .primitiveCount(chunks.size()))



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
            });
            return data.a;
        }
    }
}
