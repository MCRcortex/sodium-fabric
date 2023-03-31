package me.cortex.nv.sorting;

import org.lwjgl.system.MemoryUtil;

public class CPUSorting {
    public static void sort(short[] quadIndices, long quadDataAddr) {
        //Populate the midpoint and depth
        int[] sortingArray = new int[quadIndices.length];
        for (int i = 0; i < quadIndices.length; i++) {
            //Compact vertex format
            //long a = MemoryUtil.memGetLong(quadDataAddr+(quadIndices[i]*4L)*20);//A point
            //long b = MemoryUtil.memGetLong(quadDataAddr+(quadIndices[i]*4L+3)*20);//B point
            float depth = 0.0f;
            sortingArray[i] = i;
        }

        //Partial sort
        for (int pass = 0; pass < 3; pass++) {
            //Part A
            for (int i = 0; i < sortingArray.length-1; i += 2) {
                int a = sortingArray[i];
                int b = sortingArray[i + 1];
                if (b < a) {
                    sortingArray[i] = b;
                    sortingArray[i + 1] = a;
                }
            }
            //Part B
            for (int i = 1; i < sortingArray.length-1; i += 2) {
                int a = sortingArray[i];
                int b = sortingArray[i + 1];
                if (b < a) {
                    sortingArray[i] = b;
                    sortingArray[i + 1] = a;
                }
            }
        }
        for (int i = 0; i < sortingArray.length; i++) {
            quadIndices[i] = (short) (sortingArray[i]&0xFFFF);
        }
    }


    public static void main(String[] args) {
        sort(new short[101], 0);
    }
}
