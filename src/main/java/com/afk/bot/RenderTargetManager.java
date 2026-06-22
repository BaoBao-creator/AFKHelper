package com.afk.bot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

public final class RenderTargetManager {
    public void returnToMenu(MinecraftClient client) {
        client.execute(() -> client.setScreen(new TitleScreen()));
    }
}
