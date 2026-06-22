package com.afk.bot;

import com.afk.mixin.MinecraftClientSessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Moves the currently connected vanilla client session into AFKHelper's background store.
 */
public final class SessionTransitionManager {
    private final BackgroundConnectionStore store;
    private final AtomicReference<SessionIdentity> nextIdentity = new AtomicReference<>();

    public SessionTransitionManager(BackgroundConnectionStore store) {
        this.store = store;
    }

    public boolean isPreserving() {
        return false;
    }

    public SessionIdentity getNextIdentity() {
        return nextIdentity.get();
    }

    public SessionIdentity setNextOfflineIdentity(String username) {
        SessionIdentity identity = SessionIdentity.offline(username);
        nextIdentity.set(identity);
        return identity;
    }

    public BotConnection preserveActiveAndPrepare(MinecraftClient client, String username) {
        if (client == null) {
            throw new IllegalStateException("MinecraftClient is not available.");
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            throw new IllegalStateException("Join a multiplayer server before using /bot join.");
        }

        ClientConnection connection = handler.getConnection();
        if (connection == null || !connection.isOpen()) {
            throw new IllegalStateException("The active server connection is not open.");
        }

        SessionIdentity currentIdentity = SessionIdentity.from(client.getSession());
        SessionIdentity nextOfflineIdentity = setNextOfflineIdentity(username);
        ServerEndpoint endpoint = resolveServerEndpoint(client, connection);
        BotConnection bot = new BotConnection(currentIdentity, endpoint.host(), endpoint.port(), connection, handler);
        if (!store.put(bot)) {
            throw new IllegalStateException(currentIdentity.username() + " is already tracked.");
        }

        overrideClientSession(client, nextOfflineIdentity);
        detachClientToTitleScreen(client);
        bot.startBackgroundTicking();
        return bot;
    }

    private static ServerEndpoint resolveServerEndpoint(MinecraftClient client, ClientConnection connection) {
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (serverInfo != null && serverInfo.address != null && !serverInfo.address.isBlank()) {
            ServerAddress address = ServerAddress.parse(serverInfo.address);
            return new ServerEndpoint(address.getAddress(), address.getPort());
        }

        SocketAddress remote = connection.getAddress();
        if (remote instanceof InetSocketAddress inetSocketAddress) {
            return new ServerEndpoint(inetSocketAddress.getHostString(), inetSocketAddress.getPort());
        }
        return new ServerEndpoint(String.valueOf(remote), -1);
    }

    private static void overrideClientSession(MinecraftClient client, SessionIdentity identity) {
        ((MinecraftClientSessionAccessor) client).afkhelper$setSession(identity.session());
    }

    private static void detachClientToTitleScreen(MinecraftClient client) {
        client.setScreen(new TitleScreen());
        client.interactionManager = null;
        client.player = null;
        client.cameraEntity = null;
        client.targetedEntity = null;
        client.crosshairTarget = null;
        client.world = null;
        client.setCurrentServerEntry(null);
    }

    private record ServerEndpoint(String host, int port) { }
}
