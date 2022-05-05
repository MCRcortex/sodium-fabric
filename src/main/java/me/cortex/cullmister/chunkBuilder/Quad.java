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

    private boolean constColour() {
        int base = corners[0].color();
        for (Vert i : corners) {
            if (i.color() != base)
                return false;
        }
        return true;
    }

    private boolean constLight() {
        int base = corners[0].light();
        for (Vert i : corners) {
            if (i.light() != base)
                return false;
        }
        return true;
    }

    public boolean couldMerge() {
        if (constColour() && constLight())
            return true;
        return false;
    }

    public boolean looselyCompatibleWith(Quad other) {
        //Check that its the same sprite
        if (!sprite.getId().equals(other.sprite.getId()))
            return false;

        //Check for colour compatibility

        //TODO: see if maybe change this or something!!!
        if (!(couldMerge()&&other.couldMerge()))
            return false;
        if (corners[0].color() != other.corners[0].color())
            return false;


        //TODO: can probably do the same thing for light that is being done for textures, e.g. each light level is a unique texture
        //Check for light compatibility
        if (corners[0].light() != other.corners[0].light())
            return false;

        //TODO: maybe do texture check
        return true;
    }



    public int connectingVerticies(Quad other) {
        int out = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (corners[i].isSamePos(other.corners[j])) {
                    out++;
                }
            }
        }
        return out;
    }
}
