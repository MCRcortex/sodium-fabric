package net.caffeinemc.sodium.render.buffer.arena.sparse;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import net.caffeinemc.gfx.api.buffer.SparseBuffer;
import net.caffeinemc.gfx.api.device.RenderDevice;

import java.util.Comparator;
import java.util.Set;

public class SparseBufferArena {
    private static final class Allocation {
        long offset;
        long length;
        private Allocation(long offset, long length) {
            this.offset = offset;
            this.length = length;
        }
    }

    private final SparseBuffer buffer;
    private final RenderDevice device;
    private final int pageSize;

    //TODO: make an implmentation that has the required finding methods so its faster
    private final Long2ObjectAVLTreeMap<Allocation> allocations = new Long2ObjectAVLTreeMap<>();
    private final ObjectAVLTreeSet<Allocation> freeAllocations = new ObjectAVLTreeSet<>((a,b)-> a.length==b.length?Long.compare(a.offset, b.offset):Long.compare(a.length, b.length));

    public SparseBufferArena(RenderDevice device, long maxSize) {
        this.device = device;
        this.buffer = device.createSparseBuffer(maxSize, Set.of());
        this.pageSize = device.sparsePageSize();
        freeAllocations.add(new Allocation(0, maxSize));
    }

    private final Allocation FREE_FINDER = new Allocation(0,0);
    //TODO: make it also have an allignment argument
    private Allocation findBestFreeAllocation(long size) {
        FREE_FINDER.length = size;
        for (Allocation possibleAllocation : freeAllocations.tailSet(FREE_FINDER)) {
            //TODO: account for page alignment allocation stuff too
            
        }
        return null;
    }



    //TODO: make a malloc that has allignment parameter
    public long malloc(long size) {
        //TODO: maybe just put findBestFreeAllocation into here
        Allocation bestFree = findBestFreeAllocation(size);
        if (bestFree != null) {
            freeAllocations.remove(bestFree);
            if (bestFree.length == size) {
                allocations.put(bestFree.offset, bestFree);
                //TODO: check if pages need committing
                return bestFree.offset;
            } else {
                Allocation newA = new Allocation(bestFree.offset, size);
                allocations.put(newA.offset, newA);

                //Push the remaining free data back into the tree
                bestFree.offset += size;
                bestFree.length -= size;
                freeAllocations.add(bestFree);

                //TODO: check if pages need committing
                return newA.offset;
            }
        } else {
            throw new IllegalStateException("OOM");
        }
    }

    private final Allocation ALLOC_FINDER = new Allocation(0,0);
    public void free(long addr) {
        ALLOC_FINDER.offset = addr;
        Allocation allocation = allocations.get(ALLOC_FINDER);
    }
}
