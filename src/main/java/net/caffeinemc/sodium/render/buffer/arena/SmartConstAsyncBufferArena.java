package net.caffeinemc.sodium.render.buffer.arena;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.buffer.streaming.SectionedStreamingBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glFlush;

//TODO: add a fragmented % and a defragment method
//TODO: maybe also swap to a system similar to my nvidia branch with a tree set, should be alot faster
// at finding valid memory sections
public class SmartConstAsyncBufferArena implements ArenaBuffer {
    static final boolean CHECK_ASSERTIONS = false;

    private final int resizeIncrement;

    private final RenderDevice device;
    private final SectionedStreamingBuffer stagingBuffer;
    private Buffer arenaBuffer;

    private BufferSegment head;

    private int capacity;
    private int used;

    private final int stride;

    public SmartConstAsyncBufferArena(RenderDevice device, SectionedStreamingBuffer stagingBuffer, int capacity, int stride) {
        this.device = device;
        this.stagingBuffer = stagingBuffer;
        this.resizeIncrement = capacity / 16;
        this.capacity = capacity;

        this.head = new BufferSegment(this, 0, capacity);
        this.head.setFree(true);

        this.arenaBuffer = device.createBuffer((long) capacity * stride, EnumSet.noneOf(ImmutableBufferFlags.class));
        this.stride = stride;
    }

    private synchronized void resize(int newCapacity) {
        if (this.used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.checkAssertions();



        var srcBufferObj = this.arenaBuffer;
        var dstBufferObj = this.device.createBuffer(this.toBytes(newCapacity),
                EnumSet.noneOf(ImmutableBufferFlags.class));


        this.device.copyBuffer(srcBufferObj, dstBufferObj, this.toBytes(0), this.toBytes(0), this.toBytes(capacity));


        this.device.deleteBuffer(srcBufferObj);

        this.arenaBuffer = dstBufferObj;



        BufferSegment seg = getLastSeg();

        BufferSegment tail = new BufferSegment(this, capacity, newCapacity - capacity);
        tail.setFree(true);

        tail.setPrev(seg);
        tail.setNext(seg.getNext());
        seg.setNext(tail);

        this.capacity = newCapacity;

        if (seg.isFree())
            seg.mergeInto(tail);


        //glFinish();


        this.checkAssertions();
    }


    private BufferSegment getLastSeg() {
        BufferSegment seg = this.head;
        BufferSegment next = seg;
        while (next != null) {
            seg = next;
            next = seg.getNext();
        }

        return seg;
    }


    @Override
    public long getDeviceUsedMemory() {
        return this.toBytes(this.used);
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return this.toBytes(this.capacity);
    }

    private synchronized BufferSegment alloc(int size) {
        BufferSegment a = this.findFree(size);

        if (a == null) {
            return null;
        }

        BufferSegment result;

        if (a.getLength() == size) {
            a.setFree(false);

            result = a;
        } else {
            BufferSegment b = new BufferSegment(this, a.getEnd() - size, size);
            b.setNext(a.getNext());
            b.setPrev(a);

            if (b.getNext() != null) {
                b.getNext()
                        .setPrev(b);
            }

            a.setLength(a.getLength() - size);
            a.setNext(b);

            result = b;
        }

        this.used += result.getLength();
        this.checkAssertions();

        return result;
    }

    //FIXME: so when a best is found, actually check if the remaining free space is enough to store like an extra chunk
    // or something, be smart about it
    private synchronized BufferSegment findFree(int size) {
        BufferSegment entry = this.head;
        BufferSegment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                } else if (entry.getLength() >= size) {
                    if (best == null || best.getLength() > entry.getLength()) {
                        best = entry;
                    }
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    @Override
    public synchronized void free(BufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);

        this.used -= entry.getLength();

        BufferSegment next = entry.getNext();

        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }

        BufferSegment prev = entry.getPrev();

        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }

        this.checkAssertions();
    }

    @Override
    public void delete() {
        this.device.deleteBuffer(this.arenaBuffer);
    }

    @Override
    public boolean isEmpty() {
        return this.used <= 0;
    }

    @Override
    public Buffer getBufferObject() {
        return this.arenaBuffer;
    }

    public synchronized void upload(List<PendingUpload> uploads, int frameIndex) {
        // A linked list is used as we'll be randomly removing elements and want O(1) performance
        var pendingTransfers = new LinkedList<PendingTransfer>();

        var section = this.stagingBuffer.getSection(
                frameIndex,
                uploads.stream().mapToInt(u -> u.data.getLength()).sum(),
                true
        );

        // Write the PendingUploads to the mapped streaming buffer
        // Also create the pending transfers to go from streaming buffer -> arena buffer
        long sectionOffset = section.getOffset() + section.getView().position();
        // this is basically the address of what sectionOffset points to
        long sectionAddress = MemoryUtil.memAddress(section.getView());
        int transferOffset = 0;
        for (var upload : uploads) {
            int length = upload.data.getLength();
            pendingTransfers.add(
                    new PendingTransfer(
                            upload.holder,
                            sectionOffset + transferOffset,
                            length
                    )
            );

            MemoryUtil.memCopy(
                    upload.data.getAddress(),
                    sectionAddress + transferOffset,
                    length
            );

            transferOffset += length;
        }
        section.getView().position(section.getView().position() + transferOffset);
        section.flushPartial();

        var backingStreamingBuffer = this.stagingBuffer.getBufferObject();

        // Try to upload all of the data into free segments first
        pendingTransfers.removeIf(transfer -> this.tryUpload(backingStreamingBuffer, transfer));

        // If we weren't able to upload some buffers, they will have been left behind in the queue
        if (!pendingTransfers.isEmpty()) {
            // Calculate the amount of memory needed for the remaining uploads
            int remainingElements = (int) pendingTransfers
                    .stream()
                    .mapToLong(transfer -> this.toElements(transfer.length()))
                    .sum();

            // Ask the arena to grow to accommodate the remaining uploads
            // This will force a re-allocation and compaction, which will leave us a continuous free segment
            // for the remaining uploads
            this.ensureCapacity(remainingElements);

            // Try again to upload any buffers that failed last time
            pendingTransfers.removeIf(transfer -> this.tryUpload(backingStreamingBuffer, transfer));

            // If we still had failures, something has gone wrong
            if (!pendingTransfers.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }
    }

    private synchronized boolean tryUpload(Buffer streamingBuffer, PendingTransfer transfer) {
        BufferSegment segment = this.alloc((int) this.toElements(transfer.length()));

        if (segment == null) {
            return false;
        }

        // Copy the uploads from the streaming buffer to the arena buffer
        this.device.copyBuffer(streamingBuffer, this.arenaBuffer, transfer.offset(), this.toBytes(segment.getOffset()), transfer.length());
        glFinish();
        transfer.holder().set(segment);

        return true;
    }

    public void ensureCapacity(int elementCount) {
        // Re-sizing the arena results in a compaction, so any free space in the arena will be
        // made into one contiguous segment, joined with the new segment of free space we're asking for
        // We calculate the number of free elements in our arena and then subtract that from the total requested
        int elementsNeeded = elementCount - (this.capacity - this.used);

        // Try to allocate some extra buffer space unless this is an unusually large allocation
        this.resize(Math.max(this.capacity + this.resizeIncrement, this.capacity + elementsNeeded));
    }

    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    private void checkAssertions0() {
        BufferSegment seg = this.head;
        int used = 0;

        while (seg != null) {
            if (seg.getOffset() < 0) {
                throw new IllegalStateException("segment.start < 0: out of bounds");
            } else if (seg.getEnd() > this.capacity) {
                throw new IllegalStateException("segment.end > arena.capacity: out of bounds");
            }

            if (!seg.isFree()) {
                used += seg.getLength();
            }

            BufferSegment next = seg.getNext();

            if (next != null) {
                if (next.getOffset() < seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start < segment.end: overlapping segments (corrupted)");
                } else if (next.getOffset() > seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start > segment.end: not truly connected (sparsity error)");
                }

                if (next.isFree() && next.getNext() != null) {
                    if (next.getNext().isFree()) {
                        throw new IllegalStateException("segment.free && segment.next.free: not merged consecutive segments");
                    }
                }
            }

            BufferSegment prev = seg.getPrev();

            if (prev != null) {
                if (prev.getEnd() > seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end > segment.start: overlapping segments (corrupted)");
                } else if (prev.getEnd() < seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end < segment.start: not truly connected (sparsity error)");
                }

                if (prev.isFree() && prev.getPrev() != null) {
                    if (prev.getPrev().isFree()) {
                        throw new IllegalStateException("segment.free && segment.prev.free: not merged consecutive segments");
                    }
                }
            }

            seg = next;
        }

        if (this.used < 0) {
            throw new IllegalStateException("arena.used < 0: failure to track");
        } else if (this.used > this.capacity) {
            throw new IllegalStateException("arena.used > arena.capacity: failure to track");
        }

        if (this.used != used) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }

    private long toBytes(long index) {
        return index * this.stride;
    }

    private long toElements(long bytes) {
        return bytes / this.stride;
    }

    @Override
    public int getStride() {
        return this.stride;
    }

}
