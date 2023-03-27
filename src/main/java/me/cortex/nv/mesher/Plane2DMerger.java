package me.cortex.nv.mesher;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

//Merges along a 2D axis (that is the quad is convering the entire block surface, thus only need points)
public class Plane2DMerger <T> {
    private final T[][] plane;
    private final T[] result;

    public Plane2DMerger(Class<T> clz) {
        plane = (T[][]) Array.newInstance(clz, 16,16);
        result = (T[]) Array.newInstance(clz, 16*16);
    }

    public boolean setQuad(int axisA, int axisB, T obj) {
        if (plane[axisA][axisB] != null)
            return false;
        plane[axisA][axisB] = obj;
        return true;
    }

    public record RangeResult(int minx, int maxx, int miny, int maxy) {
        public boolean isSingleQuad() {
            return minx == maxx && miny == maxy;
        }
        public int count() {
            return (maxx-minx+1)*(maxy-miny+1);
        }
    }

    private boolean quadSet(int x, int y) {
        return plane[x][y] != null;
    }

    private RangeResult computeMaxBounds(int bx, int by) {
        /*
        //Find the base radius
        int radius = 1;
        outerRadius:
        for (; (bx-radius>=0&&bx+radius<16&&by-radius>=0&&by+radius<16); radius++) {
            for (int x = Math.max(bx-radius-1, 0); x<=Math.min(bx+radius+1, 15); x++) {
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
        */
        //We have our base bounds, try and expand each axis

        boolean px = true;
        boolean py = true;
        boolean nx = true;
        boolean ny = true;
        int minx = bx;//-radius;
        int maxx = bx;//+radius;
        int miny = by;//-radius;
        int maxy = by;//+radius;


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

    static volatile int IN;
    static volatile int OUT;
    private static synchronized void statistics(int in, int out) {
        IN += in;
        OUT += out;
        System.out.println("Ratio: "+ ((float)OUT/IN));
    }

    public record MergedQuad <T>(T[] quads, RangeResult bounds) {}
    public float merge(Consumer<T> singleQuadConsumer, Consumer<MergedQuad<T>> mergedQuadConsumer) {
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

            if (false) {
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
            } else {
                outer:
                for (x = 0; x < 16; x++) {
                    for (y = 0; y < 16; y++) {
                        if (quadSet(x, y)) {
                            break outer;
                        }
                    }
                }
            }
            outQuadCount++;
            RangeResult rr = computeMaxBounds(x,y);
            if (rr.isSingleQuad()) {
                singleQuadConsumer.accept(plane[rr.minx][rr.miny]);
                plane[rr.minx][rr.miny] = null;
                count--;
            } else {
                MergedQuad<T> result = new MergedQuad<>(this.result, rr);
                int i = 0;
                for (int X = rr.minx; X <= rr.maxx; X++) {
                    for (int Y = rr.miny; Y <= rr.maxy; Y++) {
                        count--;
                        result.quads[i++] = plane[X][Y];
                        plane[X][Y] = null;
                    }
                }
                mergedQuadConsumer.accept(result);
            }
            if (count < 0) {
                throw new IllegalStateException();
            }
        }
        //System.out.println("In: "+inQuadCount+" Out: "+outQuadCount);
        //statistics(inQuadCount, outQuadCount);
        return  ((float)outQuadCount);
    }

    private void dumpQuads() {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (quadSet(x,y)) {
                    System.out.print("# ");
                } else {
                    System.out.print("  ");
                }
            }
            System.out.println();
        }
        System.out.println("__________________________________________________________________________");
    }


    public static void main(String[] args) {
        Plane2DMerger m = new Plane2DMerger(Object.class);
        long t=0;
        float tr = 0;
        int tests = 10000;
        for (int i = 0; i < tests; i++) {
            //Randomly spray quads at 50% chance
            Random r = new Random(i);
            r.nextLong();
            r.nextLong();
            r.nextLong();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (r.nextFloat()>0.5) {
                        m.plane[x][y] = new Object();
                    }
                }
            }
            long a = System.nanoTime();
            tr+=m.merge(c->{}, b->{});
            t += (System.nanoTime() - a);
        }
        System.out.println("Avg time per test: "+((t/1000)/tests)+" microseconds, shrink ratio: "+(tr/tests));//Lower is better
    }
}
