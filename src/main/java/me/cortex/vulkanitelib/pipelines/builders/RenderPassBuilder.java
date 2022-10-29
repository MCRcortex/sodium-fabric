package me.cortex.vulkanitelib.pipelines.builders;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.util.LinkedList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class RenderPassBuilder {
    record AttachmentEntry(
            int flags,
            int format,
            int samples,
            int loadOp,
            int storeOp,
            int stencilLoadOp,
            int stencilStoreOp,
            int initialLayout,
            int finalLayout) {
    }

    record SubpassEntry(
            int flags,
            int bindingPoint,
            int[] colour,
            int depthStencil,
            int[] preserve) {//TODO: will need to add subpass dependency probably

    }

    private List<AttachmentEntry> attachmentEntryList = new LinkedList<>();
    public RenderPassBuilder attachment(int format, int imageFinalLayout) {
        return attachment(format, imageFinalLayout, VK_ATTACHMENT_LOAD_OP_CLEAR);
    }
    public RenderPassBuilder attachment(int format, int imageFinalLayout, int loadOp) {
        attachmentEntryList.add(new AttachmentEntry(0,
                format,
                VK_SAMPLE_COUNT_1_BIT,
                loadOp,
                VK_ATTACHMENT_STORE_OP_STORE,
                VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                VK_ATTACHMENT_STORE_OP_DONT_CARE,
                VK_IMAGE_LAYOUT_UNDEFINED,
                imageFinalLayout));
        return this;
    }

    private List<SubpassEntry> subpasses = new LinkedList<>();
    public RenderPassBuilder subpass(int bindingPoint, int depthStencil, int... colours) {
        subpasses.add(new SubpassEntry(0, bindingPoint, colours, depthStencil, new int[]{}));
        return this;
    }

    public int attachmentCount() {
        return attachmentEntryList.size();
    }

    public VkRenderPassCreateInfo generate(MemoryStack stack) {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentEntryList.size(), stack);
        for (AttachmentEntry entry : attachmentEntryList) {
            attachments.apply(struct-> {
                struct.flags(entry.flags)
                        .format(entry.format)
                        .samples(entry.samples)
                        .loadOp(entry.loadOp)
                        .storeOp(entry.storeOp)
                        .stencilLoadOp(entry.stencilLoadOp)
                        .stencilStoreOp(entry.stencilStoreOp)
                        .initialLayout(entry.initialLayout)
                        .finalLayout(entry.finalLayout);
            });
        }
        attachments.rewind();
        VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(this.subpasses.size(), stack);
        for (SubpassEntry subpass : this.subpasses) {
            subpasses.apply(struct-> {
                VkAttachmentReference.Buffer colourAttachments = VkAttachmentReference.calloc(subpass.colour.length);
                for (int colour : subpass.colour) {
                    colourAttachments.apply(ca->ca.attachment(colour).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL));
                }
                colourAttachments.rewind();
                VkAttachmentReference.Buffer preserveAttachments = VkAttachmentReference.calloc(subpass.preserve.length);
                for (int preserve : subpass.preserve) {
                    preserveAttachments.apply(ca->ca.attachment(preserve).layout(VK_IMAGE_LAYOUT_UNDEFINED));
                }
                preserveAttachments.rewind();
                VkAttachmentReference depthStencil = null;
                if (subpass.depthStencil != -1) {
                    depthStencil = VkAttachmentReference.calloc(stack)
                            .attachment(subpass.depthStencil)
                            .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                }
                struct.flags(subpass.flags)
                        .pipelineBindPoint(subpass.bindingPoint)
                        .pDepthStencilAttachment(depthStencil)
                        .colorAttachmentCount(colourAttachments.remaining())
                        .pColorAttachments(colourAttachments);
            });
        }
        subpasses.rewind();

        return VkRenderPassCreateInfo.calloc(stack)
                .sType$Default()
                .pSubpasses(subpasses)
                .pAttachments(attachments);
    }
}
