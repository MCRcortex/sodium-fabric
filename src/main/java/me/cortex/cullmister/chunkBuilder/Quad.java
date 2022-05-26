package me.cortex.cullmister.chunkBuilder;

import net.minecraft.client.texture.Sprite;

public class Quad {
    //NOTE: corners are already in correct render order to use with IBO
    Vert[] corners = new Vert[4];
    Sprite sprite;

    public Quad(Vert[] corners, Sprite sprite) {
        System.arraycopy(corners,0, this.corners,0,4);
        this.sprite = sprite;
    }



    public static int W = 0;
    public static int N = 1;
    public static int E = 2;
    public static int S = 3;

    public static int SW = 0;
    public static int NW = 1;
    public static int NE = 2;
    public static int SE = 3;

    public static final int[] OPPOSITE_FACE = new int[] {E, S, W, N};
    public static final int[] DIRINDEX = new int[] {0,1,0,1};
    public static final int[][] FACE2INDEX = new int[][] {{0,1},{1,2},{2,3},{3,0}};

    private static final int[][] OPPOSITE_POINT_LUT = new int[][] {
            { 3, 2,-1,-1}, //W
            {-1, 0, 3,-1}, //N
            {-1,-1, 1, 0}, //E
            { 1,-1,-1, 2}, //S
    };

    public static int getOppositeIndex(int index, int cFaceDir) {
        return OPPOSITE_POINT_LUT[cFaceDir][index];
    }



    public int mergeabilityAxis() {
        int i = 0;
        i|=(corners[0].isSimilar(corners[3]) && corners[1].isSimilar(corners[2]))?1:0;//Horizontal
        i|=(corners[0].isSimilar(corners[1]) && corners[3].isSimilar(corners[2]))?2:0;//Vertical
        return i-1;
    }

    public int connectable(Quad other) {
        for (int i = 0; i < 4; i++) {
            int[] a = FACE2INDEX[i];
            int[] b = FACE2INDEX[OPPOSITE_FACE[i]];
            if (    corners[a[0]].isSimilar(other.corners[b[1]]) &&
                    corners[a[1]].isSimilar(other.corners[b[0]]) &&
                    corners[a[0]].isSamePos(other.corners[b[1]]) &&
                    corners[a[1]].isSamePos(other.corners[b[0]])
                )
                return i;
        }
        return -1;
    }
}
