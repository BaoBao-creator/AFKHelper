package com.afk.bot;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.text.LiteralText;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BotConnection implements AutoCloseable {
    private final SessionIdentity identity;
    private final String serverAddress;
    private final int serverPort;
    private final ClientConnection connection;
    private final ClientPlayNetworkHandler handler;
    private final Instant startedAt = Instant.now();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.BACKGROUND);
    private final AtomicBoolean background = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastKeepAlive = new AtomicLong(System.currentTimeMillis());
    private final KeepAliveManager keepAliveManager;
    private final PacketHandler packetHandler;
    private final ScheduledExecutorService executor;

    public BotConnection(SessionIdentity identity, String serverAddress, int serverPort, ClientConnection connection, ClientPlayNetworkHandler handler) {
        this.identity = java.util.Objects.requireNonNull(identity);
        this.serverAddress = serverAddress == null ? "unknown" : serverAddress;
        this.serverPort = serverPort;
        this.connection = java.util.Objects.requireNonNull(connection);
        this.handler = java.util.Objects.requireNonNull(handler);
        this.keepAliveManager = new KeepAliveManager(this);
        this.packetHandler = new PacketHandler(this);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AFKHelper-BotConnection-" + identity.username());
            t.setDaemon(true);
            return t;
        });
    }

    public static BotConnection internalProfile(SessionIdentity identity) {
        return new BotConnection(identity);
    }

    private BotConnection(SessionIdentity identity) {
        this.identity = java.util.Objects.requireNonNull(identity);
        this.serverAddress = "internal-offline-profile";
        this.serverPort = -1;
        this.connection = null;
        this.handler = null;
        this.keepAliveManager = new KeepAliveManager(this);
        this.packetHandler = new PacketHandler(this);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AFKHelper-BotProfile-" + identity.username());
            t.setDaemon(true);
            return t;
        });
        this.state.set(ConnectionState.DISCONNECTED);
        this.background.set(false);
        this.closed.set(true);
        this.executor.shutdownNow();
    }

    public void startBackgroundTicking() {
        executor.scheduleAtFixedRate(() -> {
            try {
                if (closed.get()) return;
                if (connection != null && connection.isOpen()) {
                    connection.tick();
                    keepAliveManager.tick();
                } else {
                    state.compareAndSet(ConnectionState.BACKGROUND, ConnectionState.DISCONNECTED);
                    connection.handleDisconnection();
                    close();
                }
            } catch (Throwable t) {
                state.set(ConnectionState.ERROR);
                close();
            }
        }, 0L, 50L, TimeUnit.MILLISECONDS);
    }

    public String getUsername() { return identity.username(); }
    public SessionIdentity getIdentity() { return identity; }
    public String getServerAddress() { return serverAddress; }
    public int getServerPort() { return serverPort; }
    public ClientConnection getConnection() { return connection; }
    public ClientPlayNetworkHandler getHandler() { return handler; }
    public PacketHandler getPacketHandler() { return packetHandler; }
    public ConnectionState getState() { return state.get(); }
    public boolean isBackground() { return background.get(); }
    public boolean isOpen() { return !closed.get() && connection != null && connection.isOpen(); }
    public Duration getUptime() { return Duration.between(startedAt, Instant.now()); }
    public long getLastKeepAliveMillis() { return lastKeepAlive.get(); }
    public int getPing() { return handler != null && handler.getPlayerListEntry(identity.uuid()) != null ? handler.getPlayerListEntry(identity.uuid()).getLatency() : -1; }
    public void markKeepAlive() { lastKeepAlive.set(System.currentTimeMillis()); }
    public void sendKeepAlive(long id) { if (isOpen()) connection.send(new KeepAliveC2SPacket(id)); markKeepAlive(); }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            state.set(ConnectionState.DISCONNECTING);
            try { if (connection != null && connection.isOpen()) connection.disconnect(new LiteralText("AFKHelper background session closed")); } finally {
                state.set(ConnectionState.DISCONNECTED);
                executor.shutdownNow();
            }
        }
    }
}
