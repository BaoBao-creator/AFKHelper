package com.afk;

import com.afk.bot.BotManager;
import com.afk.bot.SessionIdentity;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.regex.Pattern;

/**
 * Turns the client into a minimal network keeper while AFK is enabled.
 */
public final class AfkClientMod implements ClientModInitializer {
    private static final Text AFK_TITLE = new LiteralText("");
    private static final Text ENABLED = new LiteralText("AFK network-only mode enabled.");
    private static final Text DISABLED = new LiteralText("AFK network-only mode disabled.");
    private static final Pattern OFFLINE_USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final EmptyAfkScreen EMPTY_SCREEN = new EmptyAfkScreen();

    private static final int AFK_FRAMERATE_LIMIT = 1;
    private static final int AFK_VIEW_DISTANCE = 2;
    private static final int AFK_SIMULATION_DISTANCE = 2;

    private static boolean afkEnabled;
    private static int savedFramerateLimit = 60;
    private static Screen previousScreen;
    private static SavedOptions savedOptions;

    @Override
    public void onInitializeClient() {
        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("afk")
                .executes(context -> toggleWithFeedback(context.getSource().getClient(), context.getSource()))
                .then(ClientCommandManager.literal("on").executes(context -> {
                    enableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(ENABLED);
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    disableAfk(context.getSource().getClient());
                    context.getSource().sendFeedback(DISABLED);
                    return 1;
                }))
                .then(ClientCommandManager.literal("toggle").executes(context ->
                    toggleWithFeedback(context.getSource().getClient(), context.getSource())
                ))
        );


        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("bot")
                .then(ClientCommandManager.literal("join")
                    .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .executes(context -> com.afk.command.BotCommands.join(
                            context.getSource(),
                            StringArgumentType.getString(context, "username")
                        ))))
                .then(ClientCommandManager.literal("list")
                    .executes(context -> com.afk.command.BotCommands.list(context.getSource())))
                .then(ClientCommandManager.literal("leave")
                    .then(ClientCommandManager.literal("all")
                        .executes(context -> com.afk.command.BotCommands.leaveAll(context.getSource())))
                    .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .executes(context -> com.afk.command.BotCommands.leave(
                            context.getSource(),
                            StringArgumentType.getString(context, "username")
                        ))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("switch")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> switchOfflineAccount(
                        context.getSource().getClient(),
                        context.getSource(),
                        StringArgumentType.getString(context, "name")
                    ))
                )
        );
    }

    public static boolean isAfkEnabled() {
        return afkEnabled;
    }

    private static int switchOfflineAccount(MinecraftClient client, FabricClientCommandSource source, String username) {
        if (!OFFLINE_USERNAME_PATTERN.matcher(username).matches()) {
            source.sendError(new LiteralText("Invalid cracked account name. Use 3-16 letters, numbers, or underscores."));
            return 0;
        }

        SessionIdentity identity = BotManager.getInstance().setNextOfflineIdentity(username);
        source.sendFeedback(new LiteralText("Stored offline profile internally: " + identity.username() + ". MinecraftClient.session was not changed; reconnect with vanilla UI to keep the real client stable."));
        return 1;
    }

    private static int toggleWithFeedback(MinecraftClient client, FabricClientCommandSource source) {
        if (afkEnabled) {
            disableAfk(client);
            source.sendFeedback(DISABLED);
        } else {
            enableAfk(client);
            source.sendFeedback(ENABLED);
        }
        return 1;
    }

    private static void enableAfk(MinecraftClient client) {
        if (client == null || afkEnabled) {
            return;
        }

        afkEnabled = true;
        previousScreen = client.currentScreen;
        savedOptions = new SavedOptions(client.options);

        if (client.getWindow() != null) {
            savedFramerateLimit = client.getWindow().getFramerateLimit();
            client.getWindow().setFramerateLimit(AFK_FRAMERATE_LIMIT);
        }

        applyNetworkOnlyOptions(client);
        client.getSoundManager().stopAll();
        client.setScreen(EMPTY_SCREEN);
        client.worldRenderer.scheduleTerrainUpdate();
    }

    public static void disableAfk(MinecraftClient client) {
        if (client == null || !afkEnabled) {
            return;
        }

        afkEnabled = false;

        if (client.getWindow() != null) {
            client.getWindow().setFramerateLimit(savedFramerateLimit);
        }
        if (savedOptions != null) {
            savedOptions.restore(client.options);
            savedOptions = null;
        }

        Screen screenToRestore = previousScreen == EMPTY_SCREEN ? null : previousScreen;
        previousScreen = null;
        client.setScreen(screenToRestore);
        client.worldRenderer.scheduleTerrainUpdate();
    }

    private static void applyNetworkOnlyOptions(MinecraftClient client) {
        GameOptions options = client.options;
        options.maxFps = AFK_FRAMERATE_LIMIT;
        options.viewDistance = AFK_VIEW_DISTANCE;
        options.simulationDistance = AFK_SIMULATION_DISTANCE;
        options.entityDistanceScaling = 0.5F;
        options.cloudRenderMode = CloudRenderMode.OFF;
        options.graphicsMode = GraphicsMode.FAST;
        options.chunkBuilderMode = ChunkBuilderMode.NONE;
        options.particles = ParticlesMode.MINIMAL;
        options.mipmapLevels = 0;
        options.biomeBlendRadius = 0;
        options.entityShadows = false;
        options.showSubtitles = false;
        options.hudHidden = true;
        options.debugEnabled = false;
        options.debugProfilerEnabled = false;
        options.debugTpsEnabled = false;
        options.bobView = false;
        options.heldItemTooltips = false;
        options.fovEffectScale = 0.0F;
        options.distortionEffectScale = 0.0F;
        for (SoundCategory category : SoundCategory.values()) {
            options.setSoundVolume(category, 0.0F);
            client.getSoundManager().updateSoundVolume(category, 0.0F);
        }
        if (client.world != null) {
            client.world.setSimulationDistance(AFK_SIMULATION_DISTANCE);
        }
    }

    private static final class EmptyAfkScreen extends Screen {
        private EmptyAfkScreen() {
            super(AFK_TITLE);
        }

        @Override
        protected void init() {
            clearChildren();
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void tick() {
        }

        @Override
        public void renderBackground(MatrixStack matrices) {
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }

    private static final class SavedOptions {
        private final int maxFps;
        private final int viewDistance;
        private final int simulationDistance;
        private final float entityDistanceScaling;
        private final CloudRenderMode cloudRenderMode;
        private final GraphicsMode graphicsMode;
        private final ChunkBuilderMode chunkBuilderMode;
        private final ParticlesMode particles;
        private final int mipmapLevels;
        private final int biomeBlendRadius;
        private final boolean entityShadows;
        private final boolean showSubtitles;
        private final boolean hudHidden;
        private final boolean debugEnabled;
        private final boolean debugProfilerEnabled;
        private final boolean debugTpsEnabled;
        private final boolean bobView;
        private final boolean heldItemTooltips;
        private final float fovEffectScale;
        private final float distortionEffectScale;
        private final float[] soundVolumes = new float[SoundCategory.values().length];

        private SavedOptions(GameOptions options) {
            maxFps = options.maxFps;
            viewDistance = options.viewDistance;
            simulationDistance = options.simulationDistance;
            entityDistanceScaling = options.entityDistanceScaling;
            cloudRenderMode = options.cloudRenderMode;
            graphicsMode = options.graphicsMode;
            chunkBuilderMode = options.chunkBuilderMode;
            particles = options.particles;
            mipmapLevels = options.mipmapLevels;
            biomeBlendRadius = options.biomeBlendRadius;
            entityShadows = options.entityShadows;
            showSubtitles = options.showSubtitles;
            hudHidden = options.hudHidden;
            debugEnabled = options.debugEnabled;
            debugProfilerEnabled = options.debugProfilerEnabled;
            debugTpsEnabled = options.debugTpsEnabled;
            bobView = options.bobView;
            heldItemTooltips = options.heldItemTooltips;
            fovEffectScale = options.fovEffectScale;
            distortionEffectScale = options.distortionEffectScale;
            SoundCategory[] categories = SoundCategory.values();
            for (int i = 0; i < categories.length; i++) {
                soundVolumes[i] = options.getSoundVolume(categories[i]);
            }
        }

        private void restore(GameOptions options) {
            options.maxFps = maxFps;
            options.viewDistance = viewDistance;
            options.simulationDistance = simulationDistance;
            options.entityDistanceScaling = entityDistanceScaling;
            options.cloudRenderMode = cloudRenderMode;
            options.graphicsMode = graphicsMode;
            options.chunkBuilderMode = chunkBuilderMode;
            options.particles = particles;
            options.mipmapLevels = mipLevels();
            options.biomeBlendRadius = biomeBlendRadius;
            options.entityShadows = entityShadows;
            options.showSubtitles = showSubtitles;
            options.hudHidden = hudHidden;
            options.debugEnabled = debugEnabled;
            options.debugProfilerEnabled = debugProfilerEnabled;
            options.debugTpsEnabled = debugTpsEnabled;
            options.bobView = bobView;
            options.heldItemTooltips = heldItemTooltips;
            options.fovEffectScale = fovEffectScale;
            options.distortionEffectScale = distortionEffectScale;
            SoundCategory[] categories = SoundCategory.values();
            for (int i = 0; i < categories.length; i++) {
                options.setSoundVolume(categories[i], soundVolumes[i]);
            }
        }

        private int mipLevels() {
            return mipmapLevels;
        }
    }
}
