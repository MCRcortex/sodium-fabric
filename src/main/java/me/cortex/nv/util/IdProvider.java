package me.cortex.nv.util;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;

public class IdProvider {
    private int cid = 0;
    private final IntSortedSet free = new IntAVLTreeSet(Integer::compareTo);

    public int provide() {
        if (free.isEmpty()) {
            return cid++;
        }
        int ret = free.firstInt();
        free.remove(ret);
        return ret;
    }

    public void release(int id) {
        free.add(id);
        while ((!free.isEmpty()) && free.lastInt()+1 == cid) {
            free.remove(--cid);
        }
    }

    public int maxIndex() {
        return cid;
    }
}
