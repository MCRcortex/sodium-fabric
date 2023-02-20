package me.cortex.nv.gl;

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;

//FIXME: NOTE: if there is a free block of size > 2^30 EVERYTHING BREAKS, need to either increase size
// or automatically split and manage multiple blocks which is very painful
//OR instead of addr, defer to a long[] and use indicies
public class SegmentedManager {
    private final int ADDR_BITS = 34;//This gives max size per allocation of 2^30 and max address of 2^39
    private final int SIZE_BITS = 64 - ADDR_BITS;
    private final long SIZE_MSK = (1L<<SIZE_BITS)-1;
    private final long ADDR_MSK = (1L<<ADDR_BITS)-1;
    private final LongAVLTreeSet FREE = new LongAVLTreeSet();//Size Address
    private final LongAVLTreeSet TAKEN = new LongAVLTreeSet();//Address Size

    //swaps from size,address to address,size
    private long swapF2T(long slot) {
        return (slot<<SIZE_BITS)|(slot>>>ADDR_BITS);
    }
    //swaps from address,size to size,address
    private long swapT2F(long slot) {
        return (slot<<ADDR_BITS)|(slot>>>SIZE_BITS);
    }

    /*
    public long allocFromLargest(int size) {//Allocates from the largest avalible block, this is useful for expanding later on

    }*/

    public long alloc(int size) {//TODO: add alignment support
        var iter = FREE.iterator((long) size << ADDR_BITS);
        if (!iter.hasNext()) {//No free space for allocation
            //Create new allocation
            return -1;
        } else {
            long slot = iter.nextLong();
            iter.remove();
            if ((slot >>> ADDR_BITS) == size) {//If the allocation and slot is the same size, just add it to the taken
                TAKEN.add(swapF2T(slot));
            } else {
                TAKEN.add(((slot&ADDR_MSK)<<SIZE_BITS)|size);
                FREE.add((((slot >>> ADDR_BITS)-size)<<ADDR_BITS)|((slot&ADDR_MSK)+size));
            }
            return slot&ADDR_MSK;
        }
    }

    public int free(long addr) {//Returns size of freed memory
        var iter = TAKEN.iterator(addr<<SIZE_BITS);
        long slot = iter.nextLong();
        iter.remove();

        //Note: if there is a previous entry, it means that it is guaranteed for the ending address to either
        // be the addr, or indicate a free slot that needs to be merged
        if (iter.hasPrevious()) {
            long prevSlot = iter.previousLong();
        }

        //If there is a next element it is guarenteed to either be the next block, or indicate that there is
        // a block that needs to be merged into
        if (iter.hasNext()) {
            long nextSlot = iter.nextLong();
        }

        //TODO: need to attempt to merge multiple elements of FREE together

        return 0;
    }



    //Attempts to expand an allocation, returns true on success
    public boolean expand(long addr, int extra) {
        return false;
    }
}
