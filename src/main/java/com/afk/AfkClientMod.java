package com.afk;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

/**
 * Client-side AFK power saver.
 *
 * When enabled, the mod keeps the client connected to multiplayer servers while
 * aggressively reducing local rendering work: it caps foreground FPS to 1,
 * opens a minimal black AFK screen, and the render mixin skips world rendering.
 */
public final class AfkClientMod implements ClientModInitializer {
    private static final Text AFK_TITLE = new LiteralText("AFK Helper");
    private static final Text AFK_BODY = new LiteralText("AFK power saver is enabled");
    private static final Text AFK_HINT = new LiteralText("World rendering is paused locally; network/server connection stays active.");
    private static final Text RETURN_TO_GAME = new LiteralText("Return to game");

    private static final int AFK_FRAMERATE_LIMIT = 1;

    private static boolean afkEnabled;
    private static int savedFramerateLimit = 60;
    private static Screen previousScreen;

    @Override
    public void onInitializeClient() {
        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("afk")
                .executes(context -> toggleWithFeedback(context.getSource().getClient(), context.getSource()))
                .then(ClientCommandManager.literal("on").executes(context -> {
                    enableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(new LiteralText("AFK Helper enabled. FPS capped to 1 and rendering minimized."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    disableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(new LiteralText("AFK Helper disabled. Settings restored."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("toggle").executes(context ->
                    toggleWithFeedback(context.getSource().getClient(), context.getSource())
                ))
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (afkEnabled) {
                keepAfkOptimizationsApplied(client);
            }
        });

        AFKHelper.LOGGER.info("AFK Helper client power saver loaded. Use /afk on, /afk off, or /afk toggle.");
    }

    public static boolean isAfkEnabled() {
        return afkEnabled;
    }

    private static int toggleWithFeedback(MinecraftClient client, net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource source) {
        if (afkEnabled) {
            disableAfk(client);
            source.sendFeedback(new LiteralText("AFK Helper disabled. Settings restored."));
        } else {
            enableAfk(client);
            source.sendFeedback(new LiteralText("AFK Helper enabled. FPS capped to 1 and rendering minimized."));
        }
        return 1;
    }

    private static void enableAfk(MinecraftClient client) {
        if (client == null || afkEnabled) {
            return;
        }

        afkEnabled = true;
        previousScreen = client.currentScreen;

        if (client.getWindow() != null) {
            savedFramerateLimit = client.getWindow().getFramerateLimit();
        }

        client.execute(() -> {
            keepAfkOptimizationsApplied(client);
            client.setScreen(new AfkScreen());
        });
    }

    public static void disableAfk(MinecraftClient client) {
        if (client == null || !afkEnabled) {
            return;
        }

        afkEnabled = false;

        if (client.getWindow() != null) {
            client.getWindow().setFramerateLimit(savedFramerateLimit);
        }

        Screen screenToRestore = previousScreen;
        previousScreen = null;
        client.execute(() -> client.setScreen(screenToRestore));
    }

    private static void keepAfkOptimizationsApplied(MinecraftClient client) {
        if (client != null && client.getWindow() != null
            && client.getWindow().getFramerateLimit() != AFK_FRAMERATE_LIMIT) {
            client.getWindow().setFramerateLimit(AFK_FRAMERATE_LIMIT);
        }
    }

    private static final class AfkScreen extends Screen {
        private AfkScreen() {
            super(AFK_TITLE);
        }

        @Override
        protected void init() {
            clearChildren();
            addDrawableChild(new ButtonWidget(
                this.width / 2 - 100,
                this.height / 2 + 28,
                200,
                20,
                RETURN_TO_GAME,
                button -> disableAfk(MinecraftClient.getInstance())
            ));
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, 0, 0, this.width, this.height, 0xFF000000);
            drawCenteredTextWithShadow(matrices, this.textRenderer, AFK_BODY.asOrderedText(), this.width / 2, this.height / 2 - 18, 0x55FF55);
            drawCenteredTextWithShadow(matrices, this.textRenderer, AFK_HINT.asOrderedText(), this.width / 2, this.height / 2 - 4, 0xAAAAAA);
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) {
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
