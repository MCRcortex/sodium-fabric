package net.caffeinemc.sodium.vkinterop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExportSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreGetWin32HandleInfoKHR;

import static org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class Sync {
    public static class GlVkSemaphore {
        int glSemaphore;
        long vkSemaphore;
        public GlVkSemaphore() {
            try (MemoryStack stack = stackPush()){

                glSemaphore = glGenSemaphoresEXT();
                VkExportSemaphoreCreateInfo esci = VkExportSemaphoreCreateInfo.calloc(stack)
                        .sType$Default()
                        .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
                VkSemaphoreCreateInfo sci = VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default()
                        .pNext(esci);
                long[] out = new long[1];
                vkCreateSemaphore(VkContextTEMP.getDevice(), sci, null, out);
                vkSemaphore = out[0];
                PointerBuffer pb = stack.callocPointer(1);
                VkSemaphoreGetWin32HandleInfoKHR sgwhi = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                        .sType$Default()
                        .semaphore(vkSemaphore)
                        .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
                vkGetSemaphoreWin32HandleKHR(VkContextTEMP.getDevice(), sgwhi, pb);
                glImportSemaphoreWin32HandleEXT(glSemaphore, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, pb.get(0));
            }
        }
    }
}
