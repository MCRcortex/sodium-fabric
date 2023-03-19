package me.cortex.nv.mesher;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.caffeinemc.sodium.util.collections.BitArray;

import java.util.List;
import java.util.Random;
import java.util.Set;

//Merges along a 2D axis (that is the quad is convering the entire block surface, thus only need points)
public class Plane2DMerger {
    private record Quad() {

    }

    private record RangeResult(int minx, int maxx, int miny, int maxy) {

    }

    private final Quad[][] plane = new Quad[16][16];

    private boolean quadSet(int x, int y) {
        return plane[x][y] != null;
    }

    private RangeResult computeMaxBounds(int bx, int by) {
        //Find the base radius
        int radius = 1;
        outerRadius:
        for (; (bx-radius>=0&&bx+radius<16&&by-radius>=0&&by+radius<16); radius++) {
            for (int x = Math.max(bx-radius, 0); x<=Math.min(bx+radius, 15); x++) {
                if (!quadSet(x,by-radius)) {
                    break outerRadius;
                }
                if (!quadSet(x,by+radius)) {
                    break outerRadius;
                }
            }
            for (int y = Math.max(by-radius+1, 0); y<=Math.min(bx+radius-1, 15); y++) {
                if (!quadSet(bx-radius,y)) {
                    break outerRadius;
                }
                if (!quadSet(bx+radius,y)) {
                    break outerRadius;
                }
            }
        }
        radius--;
        //We have our base bounds, try and expand each axis

        boolean px = true;
        boolean py = true;
        boolean nx = true;
        boolean ny = true;
        int minx = bx-radius;
        int maxx = bx+radius;
        int miny = by-radius;
        int maxy = by+radius;
        while (px||py||nx||ny) {
            if (maxx == 15) px = false;
            if (minx == 0)  nx = false;
            if (maxy == 15) py = false;
            if (miny == 0)  ny = false;

            if (px) {//Try to expand in x+1
                for (int y = miny; y <= maxy; y++) {
                    if (!quadSet(maxx+1, y)) {
                        px = false;
                        break;
                    }
                }
                if (px) {
                    maxx++;
                }
            }
            if (nx) {
                for (int y = miny; y <= maxy; y++) {
                    if (!quadSet(minx-1, y)) {
                        nx = false;
                        break;
                    }
                }
                if (nx) {
                    minx--;
                }
            }
            if (py) {
                for (int x = minx; x <= maxx; x++) {
                    if (!quadSet(x, maxy+1)) {
                        py = false;
                        break;
                    }
                }
                if (py) {
                    maxy++;
                }
            }
            if (ny) {
                for (int x = minx; x <= maxx; x++) {
                    if (!quadSet(x, miny-1)) {
                        ny = false;
                        break;
                    }
                }
                if (ny) {
                    miny--;
                }
            }
        }
        return new RangeResult(minx, maxx, miny, maxy);
    }

    private float merge() {
        int count = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                count += quadSet(x,y)?1:0;
            }
        }
        int inQuadCount = count;
        int outQuadCount = 0;
        Random r = new Random(0);
        while (count!=0) {
            int x = 0;
            int y = 0;
            //Select a uniform position to expand from

            outer:
            while (true) {
                for (x = 0; x < 16; x++) {
                    for (y = 0; y < 16; y++) {
                        if (quadSet(x, y) && r.nextFloat() < 1f / count) {
                            break outer;
                        }
                    }
                }
            }
            /*
            outer:
            for (x = 0; x < 16; x++) {
                for (y = 0; y < 16; y++) {
                    if (quadSet(x, y)) {
                        break outer;
                    }
                }
            }*/
            outQuadCount++;
            RangeResult rr = computeMaxBounds(x,y);
            for (int X = rr.minx; X <= rr.maxx; X++) {
                for (int Y = rr.miny; Y <= rr.maxy; Y++) {
                    count--;
                    plane[X][Y] = null;
                }
            }

            if (count < 0) {
                throw new IllegalStateException();
            }
        }
        //System.out.println("In: "+inQuadCount+" Out: "+outQuadCount);
        return (float) outQuadCount/inQuadCount;
    }

    public static void main(String[] args) {
        Plane2DMerger m = new Plane2DMerger();
        long t=0;
        float tr = 0;
        for (int i = 0; i < 2; i++) {
            //Randomly spray quads at 50% chance
            Random r = new Random(i);
            r.nextLong();
            r.nextLong();
            r.nextLong();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (r.nextFloat()>0.25) {
                        m.plane[x][y] = new Quad();
                    }
                }
            }
            long a = System.nanoTime();
            tr+=m.merge()/2;
            t += System.nanoTime() - a;
        }
        System.out.println(t+": "+tr);
    }
}
