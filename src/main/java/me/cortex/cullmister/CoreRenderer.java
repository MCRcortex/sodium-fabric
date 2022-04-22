package me.cortex.cullmister;

import net.minecraft.client.world.ClientWorld;

public class CoreRenderer {
    public RegionManager regionManager;

    public CoreRenderer(ClientWorld world) {
        regionManager = new RegionManager(world);
    }

    public void tick() {
        regionManager.tick(0);
    }
}
