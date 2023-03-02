package me.jellysquid.mods.sodium.client.render.chunk.graph.v4;

import me.jellysquid.mods.sodium.client.util.frustum.Frustum;

import java.util.Arrays;


public final class FrustumCuller {
    final int rd;
    final int heightOffset;
    final int height;
    final int layers;
    final byte[] tree;
    final int widthMsk;
    public FrustumCuller(int rd, int height, int heightOffset) {
        this.heightOffset = heightOffset;
        this.height = height;
        this.rd = rd;
        layers = (int) Math.ceil(Math.max(Math.log(rd*2+1)/Math.log(2), Math.log(height)/Math.log(2)));
        tree = new byte[(((1<<(layers)*3)-1)/7)];//(8^(levels)-1)/7
        widthMsk = (1<<layers)-1;
    }

    private int getOffsetForLevelParent(int level) {//It might be faster to make this a const final array
        return getOffsetForLevelUndivided(level)/7;
    }

    private int getOffsetForLevelUndivided(int level) {
        return ((1<<(layers-level-1)*3)-1);
    }


    private int getIndex(int lvl, int leafX, int leafY, int leafZ) {
        int ilvl = layers - lvl;
        return (leafX&(widthMsk>>lvl)) |
                ((leafZ&(widthMsk>>lvl))<<ilvl) |
                ((leafY&(widthMsk>>lvl))<<(ilvl<<1))
                ;
    }

    private int getOctoIndex(int x, int y, int z) {
        return (x&1)|((z&1)<<1)|((y&1)<<2);
    }

    public boolean isInFrustum(int lvl, int x, int y, int z) {
        if (lvl == layers) return tree[0] == -1;
        return (tree[getIndex(lvl+1, x>>1, y>>1, z>>1) + getOffsetForLevelParent(lvl)] & (1<<(getOctoIndex(x,y,z)&0b111))) != 0;

    }

    private int checkNode(CFrust frustum, int lvl, int x, int y, int z, int parent) {
        int sx = x<<(lvl+4);
        int sy = ((y<<lvl)+heightOffset)<<4;
        int sz = z<<(lvl+4);
        int ex = (x+1)<<(lvl+4);
        int ey = (((y+1)<<lvl)+heightOffset)<<4;
        int ez = (z+1)<<(lvl+4);
        return frustum.intersectBox(sx, sy, sz, ex, ey, ez, parent);
    }

    //Recursivly mark all nodes as being within the frustum
    private void recurseMarkInside(int lvl, int x, int y, int z) {
        //TODO: check if within render distance, if not, abort
        //System.out.println("L: "+lvl+" "+x+" "+y+" " + z);
        //Mark parent that the node is full
        //tree[getIndex(lvl+1, x>>1, y>>1, z>>1) + getOffsetForLevelParent(lvl)] |= (1<<(getOctoIndex(x,y,z)&0b111));
        //Recursivly set children //TODO: optimize this
        for (int Y = y << lvl; Y < (y + 1) << lvl; Y++) {
            for (int Z = z << lvl; Z < (z + 1) << lvl; Z++) {
                for (int X = x << lvl; X < (x + 1) << lvl; X++) {
                    if (!inBounds(0, X,Y,Z)) continue;//TODO: REMOVE/OPTIMIZE
                    set(X,Y,Z);
                }
            }
        }
    }
    private void set(int x, int y, int z) {
        for (int i = 0; i < layers; i++) {
            byte parent = tree[getIndex(i+1,x>>(i+1),y>>(i+1),z>>(i+1))+getOffsetForLevelParent(i)] |= 1<<getOctoIndex(x>>(i), y>>(i), z>>(i));
            if (parent != (byte) 0xFF) {
                break;
            }
        }
    }

    private int distAxis(int lvl, int source, int b) {
        if (source>>lvl == b) return 0;//Within same range
        int diff = (b<<lvl) - source;
        diff += diff<0?(1<<lvl)-1:0;
        return Math.abs(diff);
    }

    //TODO: move this to a precomputed range check like burger
    private boolean inBounds(int lvl, int x, int y, int z) {
        if ((y<<lvl)+((1<<lvl)-1) < 0 || height < (y<<lvl)) return false;
        int dx = distAxis(lvl, cx, x);
        int dz = distAxis(lvl, cz, z);

        return true;//dx*dx+dz*dz<=rd*rd;
    }

    //FIXME: need to take into account the section that we are in, that is player is in a specific point
    // to fill in the tree correctyl the surrounding 9 (technically 4) tree level nodes must be tested
    // however this could mean that they are not within rd range anymore which is incorrect
    // can do what burger did an pass in min/max rd range
    private void recurseCull(CFrust frustum, int lvl, int x, int y, int z, int parentResult) {
        if (parentResult == CFrust.OUTSIDE) {
            return;
        } else if (!inBounds(lvl, x, y, z)) {
            return;
        } else if (parentResult == CFrust.INSIDE || lvl == 0) {
            recurseMarkInside(lvl, x, y, z);
            return;
        }
        //TODO: check if within render distance, if not, abort

        //Need to extend outwards
        x <<= 1;
        y <<= 1;
        z <<= 1;
        recurseCull(frustum, lvl-1, x|0, y|0, z|0, checkNode(frustum, lvl-1, x|0, y|0, z|0, parentResult));
        recurseCull(frustum, lvl-1, x|1, y|0, z|0, checkNode(frustum, lvl-1, x|1, y|0, z|0, parentResult));
        recurseCull(frustum, lvl-1, x|0, y|0, z|1, checkNode(frustum, lvl-1, x|0, y|0, z|1, parentResult));
        recurseCull(frustum, lvl-1, x|1, y|0, z|1, checkNode(frustum, lvl-1, x|1, y|0, z|1, parentResult));
        recurseCull(frustum, lvl-1, x|0, y|1, z|0, checkNode(frustum, lvl-1, x|0, y|1, z|0, parentResult));
        recurseCull(frustum, lvl-1, x|1, y|1, z|0, checkNode(frustum, lvl-1, x|1, y|1, z|0, parentResult));
        recurseCull(frustum, lvl-1, x|0, y|1, z|1, checkNode(frustum, lvl-1, x|0, y|1, z|1, parentResult));
        recurseCull(frustum, lvl-1, x|1, y|1, z|1, checkNode(frustum, lvl-1, x|1, y|1, z|1, parentResult));
    }

    private int cx;
    private int cy;
    private int cz;
    public void cull(Frustum frustum, int x, int y, int z) {
        y -= heightOffset;
        cx = x;
        cy = y;
        cz = z;
        Arrays.fill(tree, (byte) 0);
        CFrust cfrust = new CFrust(frustum.getX(), frustum.getY(), frustum.getZ(), frustum.getPlanes());
        x >>= layers;
        y >>= layers;
        z >>= layers;

        recurseCull(cfrust, layers, x-1, y, z-1, 0);//TODO: Compute parent result here
        recurseCull(cfrust, layers, x+0, y, z-1, 0);
        recurseCull(cfrust, layers, x+1, y, z-1, 0);
        recurseCull(cfrust, layers, x-1, y, z+0, 0);
        recurseCull(cfrust, layers, x+0, y, z+0, 0);
        recurseCull(cfrust, layers, x+1, y, z+0, 0);
        recurseCull(cfrust, layers, x-1, y, z+1, 0);
        recurseCull(cfrust, layers, x+0, y, z+1, 0);
        recurseCull(cfrust, layers, x+1, y, z+1, 0);
        //recurseCull();
    }
}
