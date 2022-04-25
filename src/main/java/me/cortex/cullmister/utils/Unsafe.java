package me.cortex.cullmister.utils;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.concurrent.Semaphore;

public class Unsafe {
    public static final sun.misc.Unsafe UNSAFE = getUnsafeInstance();
    static Thread memFreeThread;
    static Semaphore freeCount = new Semaphore(0);
    static final LongArrayFIFOQueue freeAddrs = new LongArrayFIFOQueue();
    static {
        memFreeThread = new Thread(Unsafe::freeerThread);
        memFreeThread.setPriority(7);
        memFreeThread.start();
    }

    private static sun.misc.Unsafe getUnsafeInstance() {
        java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();

        /*
        Different runtimes use different names for the Unsafe singleton,
        so we cannot use .getDeclaredField and we scan instead. For example:

        Oracle: theUnsafe
        PERC : m_unsafe_instance
        Android: THE_ONE
        */
        for (java.lang.reflect.Field field : fields) {
            if (!field.getType().equals(sun.misc.Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers))) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (sun.misc.Unsafe)field.get(null);
            } catch (Exception ignored) {
            }
            break;
        }

        throw new UnsupportedOperationException("LWJGL requires sun.misc.Unsafe to be available.");
    }


    public static long malloc(long size) {
        return UNSAFE.allocateMemory(size);
    }

    public static void free(long addr) {
        UNSAFE.freeMemory(addr);
    }

    public static void freeIndirect(long addr) {
        synchronized (freeAddrs) {
            freeAddrs.enqueue(addr);
        }
        freeCount.release();
    }

    static void freeerThread() {
        while (true) {
            try {
                freeCount.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            long addr;
            synchronized (freeAddrs) {
                addr = freeAddrs.dequeueLong();
            }
            free(addr);
        }
    }
}
