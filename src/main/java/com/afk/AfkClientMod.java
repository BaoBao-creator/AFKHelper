package com.example.afk;

// Fabric 1.18-style client-side AFK toggle.
// If your project uses official Mojang mappings, your IDE can remap the vanilla imports.

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

/**
 * Client-side AFK mode:
 * /afk on     -> opens a black screen with "Return to game"
 * /afk off    -> closes AFK mode and restores the previous screen
 * /afk toggle -> toggles AFK mode
 *
 * This is intentionally client-only.
 */
public final class AfkClientMod implements ClientModInitializer {
    private static final Text AFK_TITLE = new LiteralText("AFK Mode");
    private static final Text AFK_BODY = new LiteralText("AFK mode enabled");
    private static final Text RETURN_TO_GAME = new LiteralText("Return to game");

    private static boolean afkEnabled = false;
    private static int savedFramerateLimit = 60;
    private static Screen previousScreen = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("afk")
                .then(ClientCommandManager.literal("on").executes(context -> {
                    enableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(new LiteralText("AFK mode enabled."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    disableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(new LiteralText("AFK mode disabled."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("toggle").executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    if (afkEnabled) {
                        disableAfk(client);
                        context.getSource().sendFeedback(new LiteralText("AFK mode disabled."));
                    } else {
                        enableAfk(client);
                        context.getSource().sendFeedback(new LiteralText("AFK mode enabled."));
                    }
                    return 1;
                })));
        });
    }

    private static void enableAfk(MinecraftClient client) {
        if (client == null || afkEnabled) {
            return;
        }

        afkEnabled = true;
        previousScreen = client.currentScreen;

        if (client.getWindow() != null) {
            savedFramerateLimit = client.getWindow().getFramerateLimit();
            // 1 is the practical floor for keeping the UI alive; 0 is not a safe "real FPS" target.
            client.getWindow().setFramerateLimit(1);
        }

        client.execute(() -> client.setScreen(new AfkScreen()));
    }

    private static void disableAfk(MinecraftClient client) {
        if (client == null || !afkEnabled) {
            return;
        }

        afkEnabled = false;

        if (client.getWindow() != null) {
            client.getWindow().setFramerateLimit(savedFramerateLimit);
        }

        client.execute(() -> client.setScreen(previousScreen));
        previousScreen = null;
    }

    private static final class AfkScreen extends Screen {
        protected AfkScreen() {
            super(AFK_TITLE);
        }

        @Override
        protected void init() {
            clearChildren();

            addDrawableChild(new ButtonWidget(
                this.width / 2 - 100,
                this.height / 2 + 20,
                200,
                20,
                RETURN_TO_GAME,
                button -> disableAfk(MinecraftClient.getInstance())
            ));
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, 0, 0, this.width, this.height, 0xFF000000);
            drawCenteredTextWithShadow(matrices, this.textRenderer, AFK_BODY, this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return true;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
