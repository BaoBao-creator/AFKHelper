package com.afk.bot;

import com.afk.mixin.MinecraftClientSessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SessionTransitionManager {
    private final BackgroundConnectionStore store;
    private final ActiveControlManager controlManager = new ActiveControlManager();
    private final RenderTargetManager renderTargetManager = new RenderTargetManager();
    private final AtomicBoolean preserving = new AtomicBoolean(false);
    private final AtomicReference<SessionIdentity> nextIdentity = new AtomicReference<>();

    public SessionTransitionManager(BackgroundConnectionStore store) { this.store = store; }
    public boolean isPreserving() { return preserving.get(); }
    public SessionIdentity getNextIdentity() { return nextIdentity.get(); }

    public BotConnection preserveActiveAndPrepare(MinecraftClient client, String username) {
        SessionIdentity next = SessionIdentity.offline(username);
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null || client.world == null) throw new IllegalStateException("Join a server before preserving an active account.");
        ClientConnection connection = handler.getConnection();
        if (connection == null || !connection.isOpen()) throw new IllegalStateException("The active connection is not open.");
        SessionIdentity current = SessionIdentity.from(client.getSession());
        ServerInfo server = client.getCurrentServerEntry();
        String address = server == null ? String.valueOf(connection.getAddress()) : server.address;
        int port = parsePort(address);
        BotConnection bot = new BotConnection(current, address, port, connection, handler);
        preserving.set(true);
        try {
            if (!store.put(bot)) throw new IllegalStateException(current.username() + " is already preserved.");
            bot.startBackgroundTicking();
            controlManager.releaseActiveClient(client);
            ((MinecraftClientSessionAccessor) client).afkhelper$setSession(next.session());
            nextIdentity.set(next);
            renderTargetManager.returnToMenu(client);
            return bot;
        } finally {
            preserving.set(false);
        }
    }

    private static int parsePort(String address) {
        int colon = address == null ? -1 : address.lastIndexOf(':');
        if (colon >= 0) {
            try { return Integer.parseInt(address.substring(colon + 1)); } catch (NumberFormatException ignored) { }
        }
        return 25565;
    }
}
