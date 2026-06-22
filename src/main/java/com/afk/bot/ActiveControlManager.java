package com.afk.bot;

import net.minecraft.client.MinecraftClient;

public final class ActiveControlManager {
    public void releaseActiveClient(MinecraftClient client) {
        client.player = null;
        client.interactionManager = null;
        client.cameraEntity = null;
        client.targetedEntity = null;
        client.crosshairTarget = null;
        client.world = null;
    }
}
