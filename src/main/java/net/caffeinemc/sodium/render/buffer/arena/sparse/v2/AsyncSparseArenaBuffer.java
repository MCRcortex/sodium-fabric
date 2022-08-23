package net.caffeinemc.sodium.render.buffer.arena.sparse.v2;

import it.unimi.dsi.fastutil.longs.*;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.ImmutableSparseBuffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.buffer.arena.PendingTransfer;
import net.caffeinemc.sodium.render.buffer.arena.PendingUpload;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

// TODO: handle alignment
// TODO: handle element vs pointers
// TODO: convert to longs
public class AsyncSparseArenaBuffer implements ArenaBuffer {
    private static final boolean CHECK_ASSERTIONS = true;

    private final RenderDevice device;
    private final StreamingBuffer stagingBuffer;

    private ImmutableSparseBuffer arenaBuffer;

    private final LongSortedSet freedSegmentsByOffset = new LongRBTreeSet(BufferSegment::compareOffset);
    private final LongSortedSet freedSegmentsByLength = new LongRBTreeSet(BufferSegment::compareLengthOffset);

    private final Long2IntOpenHashMap pageUsageCount = new Long2IntOpenHashMap();
    private final int pageSize;

    private int capacity;
    private int position;
    private int used;

    private final int stride;

    private int committedPageCount;


    //HACKS TODO/FIX: CLEANUP
    static long gcd(long a, long b) {
        if (a == 0)
            return b;
        return gcd(b % a, a);
    }
    static long lcm(long a, long b) {
        return (a / gcd(a, b)) * b;
    }

    public AsyncSparseArenaBuffer(
            RenderDevice device,
            StreamingBuffer stagingBuffer,
            long maxCapacityBytes,
            int stride
    ) {
        this.device = device;
        this.stagingBuffer = stagingBuffer;
        this.stride = stride;
        long mul = lcm(stride, device.sparsePageSize());
        maxCapacityBytes = maxCapacityBytes + (maxCapacityBytes%mul);
        this.pageSize = device.sparsePageSize();
        this.setBuffer(device.createSparseBuffer(maxCapacityBytes, Set.of()));
    }
    
    public void reset() {
        this.freedSegmentsByOffset.clear();
        this.freedSegmentsByLength.clear();
        this.used = 0;
        this.position = 0;
    }

    @Override
    public long getDeviceUsedMemory() {
        return this.toBytes(this.used);
    }

    @Override
    public long getDeviceAllocatedMemory() {
        //return this.toBytes(this.capacity);
        return (long) committedPageCount *pageSize;
    }

    private void markUsed(long start, long size) {
        long startPage = Math.floorDiv(start, pageSize);
        long endPage = Math.floorDiv(start+size,pageSize);
        for (long page = startPage; page <= endPage; page++) {
            if (!pageUsageCount.containsKey(page)) {
                pageUsageCount.put(page,1);
                //TODO: optimize this, i.e. batch commit
                device.commitPages(arenaBuffer, page, 1);
                committedPageCount++;
            } else {
                pageUsageCount.put(page, pageUsageCount.get(page)+1);
            }
        }
    }

    private void markUnused(long start, long size) {
        long startPage = Math.floorDiv(start, pageSize);
        long endPage = Math.floorDiv(start+size,pageSize);
        for (long page = startPage; page <= endPage; page++) {
            if (!pageUsageCount.containsKey(page)) {
                throw new IllegalStateException();
            }
            if (pageUsageCount.get(page)==1) {
                pageUsageCount.remove(page);
                //TODO: optimize this, i.e. batch uncommit
                device.uncommitPages(arenaBuffer, page, 1);
                committedPageCount--;
            } else {
                pageUsageCount.put(page, pageUsageCount.get(page)-1);
            }
        }
    }

    private long alloc(int size) {
        long result = BufferSegment.INVALID;
    
        // this is used to get the closest in the tree
        long tempKey = BufferSegment.createKey(size, 0);
        LongIterator itr = this.freedSegmentsByLength.iterator(tempKey);
        
        if (itr.hasNext()) {
            long freeSegment = itr.nextLong();
            // the segment will always need to be removed from here
            this.freedSegmentsByLength.remove(freeSegment);
            this.freedSegmentsByOffset.remove(freeSegment);
            
            if (BufferSegment.getLength(freeSegment) == size) {
                // no need to add back to tree
                result = freeSegment;
            } else {
                result = BufferSegment.createKey(
                        size,
                        BufferSegment.getEnd(freeSegment) - size
                );
                
                long newFreeSegment = BufferSegment.createKey(
                        BufferSegment.getLength(freeSegment) - size,
                        BufferSegment.getOffset(freeSegment)
                );
                
                this.freedSegmentsByLength.add(newFreeSegment);
                this.freedSegmentsByOffset.add(newFreeSegment);
            }
        } else if (this.capacity - this.position >= size) {
            result = BufferSegment.createKey(size, this.position);
            
            this.position += size;
        }

        // will be 0 if invalid
        this.used += BufferSegment.getLength(result);
        
        this.checkAssertions();
        if (result != BufferSegment.INVALID) {
            markUsed(this.toBytes(BufferSegment.getOffset(result)), this.toBytes(BufferSegment.getLength(result)));
        }
        return result;
    }

    @Override
    public void free(long key) {
        this.used -= BufferSegment.getLength(key);
        if (key != BufferSegment.INVALID)
            markUnused(this.toBytes(BufferSegment.getOffset(key)), this.toBytes(BufferSegment.getLength(key)));

        LongBidirectionalIterator itr = this.freedSegmentsByOffset.iterator(key);
        
        if (itr.hasPrevious()) {
            long prev = itr.previousLong();
            
            if (BufferSegment.getEnd(prev) == BufferSegment.getOffset(key)) {
                itr.remove();
                this.freedSegmentsByLength.remove(prev);
        
                // merge key
                key = BufferSegment.createKey(
                        BufferSegment.getLength(prev) + BufferSegment.getLength(key),
                        BufferSegment.getOffset(prev)
                );
            } else {
                // need to skip one in the iterator to cancel out the previous() call
                itr.nextLong();
            }
        }
    
    
        if (itr.hasNext()) {
            long next = itr.nextLong();
            
            if (BufferSegment.getEnd(key) == BufferSegment.getOffset(next)) {
                itr.remove();
                this.freedSegmentsByLength.remove(next);
        
                // merge key
                key = BufferSegment.createKey(
                        BufferSegment.getLength(key) + BufferSegment.getLength(next),
                        BufferSegment.getOffset(key)
                );
            }
        }
        
        this.freedSegmentsByOffset.add(key);
        this.freedSegmentsByLength.add(key);
        this.checkAssertions();
    }

    @Override
    public void delete() {
        this.device.deleteBuffer(arenaBuffer);
    }

    @Nullable
    @Override
    public LongSortedSet compact() {
        return null;
    }

    @Override
    public float getFragmentation() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return this.used <= 0;
    }

    @Override
    public Buffer getBufferObject() {
        return this.arenaBuffer;
    }

    @Override
    public void upload(List<PendingUpload> uploads, int frameIndex) {
        // A linked list is used as we'll be randomly removing elements and want O(1) performance
        var pendingTransfers = new LinkedList<PendingTransfer>();

        var section = this.stagingBuffer.getSection(
                frameIndex,
                uploads.stream().mapToInt(u -> u.data.getLength()).sum(),
                true
        );

        // Write the PendingUploads to the mapped streaming buffer
        // Also create the pending transfers to go from streaming buffer -> arena buffer
        long sectionOffset = section.getDeviceOffset() + section.getView().position();
        // this is basically the address of what sectionOffset points to
        long sectionAddress = MemoryUtil.memAddress(section.getView());
        int transferOffset = 0;
        for (var upload : uploads) {
            int length = upload.data.getLength();
            pendingTransfers.add(
                    new PendingTransfer(
                            upload.bufferSegmentResult,
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

        // Try to upload all the data into free segments first
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

            // Try again to upload any buffers that failed last time
            pendingTransfers.removeIf(transfer -> this.tryUpload(backingStreamingBuffer, transfer));

            // If we still had failures, something has gone wrong
            if (!pendingTransfers.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }
    }

    private boolean tryUpload(Buffer streamingBuffer, PendingTransfer transfer) {
        long segment = this.alloc(this.toElements(transfer.length()));

        if (segment == BufferSegment.INVALID) {
            return false;
        }

        // Copy the uploads from the streaming buffer to the arena buffer
        this.device.copyBuffer(
                streamingBuffer,
                this.arenaBuffer,
                transfer.offset(),
                this.toBytes(BufferSegment.getOffset(segment)),
                transfer.length()
        );

        transfer.bufferSegmentHolder().set(segment);

        return true;
    }

    
    private void setBuffer(ImmutableSparseBuffer buffer) {
        this.arenaBuffer = buffer;
        this.capacity = this.toElements(buffer.capacity());
    }

    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    private void checkAssertions0() {
        int used = 0;
        
        long prev = BufferSegment.INVALID;
        
        for(long freedSegment : this.freedSegmentsByOffset) {
            int offset = BufferSegment.getOffset(freedSegment);
            int length = BufferSegment.getLength(freedSegment);
            int end = BufferSegment.getEnd(freedSegment);
            
            Validate.isTrue(offset >= 0, "segment.offset < 0: out of bounds");
            // TODO: is it actually valid for a freed section to be past the position?
            Validate.isTrue(end <= this.position, "segment.end > arena.position: out of bounds");
            
            used += length;
            
            if (prev != BufferSegment.INVALID) {
                int prevEnd = BufferSegment.getEnd(prev);
    
                Validate.isTrue(prevEnd <= offset,
                                "segment.prev.end > segment.offset: overlapping segments (corrupted)");
            }
            
            prev = freedSegment;
        }
    
        Validate.isTrue(this.used >= 0, "arena.used < 0: failure to track");
        Validate.isTrue(this.position <= this.capacity,
                        "arena.position > arena.capacity: failure to track");
        Validate.isTrue(this.used <= this.position, "arena.used > arena.position: failure to track");
        Validate.isTrue(this.position - this.used == used, "arena.used is invalid");
        Validate.isTrue(this.freedSegmentsByLength.size() == this.freedSegmentsByOffset.size(),
                        "freedSegmentsByLength.size != freedSegmentsByOffset.size, mismatched add/remove");
        Validate.isTrue(this.toBytes(this.toElements(this.arenaBuffer.capacity())) == this.toBytes(this.capacity),
                        "this.capacity != buffer.capacity, failure to track");
    }

    private long toBytes(int index) {
        return (long) index * this.stride;
    }

    private int toElements(long bytes) {
        return (int) (bytes / this.stride);
    }

    @Override
    public int getStride() {
        return this.stride;
    }
    
}
