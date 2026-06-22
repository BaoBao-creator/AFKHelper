package com.afk.bot;

import net.minecraft.client.MinecraftClient;

import java.time.Duration;
import java.util.Collection;

public final class BotManager {
    private static final BotManager INSTANCE = new BotManager();
    private final BackgroundConnectionStore store = new BackgroundConnectionStore();
    private final SessionTransitionManager transitionManager = new SessionTransitionManager(store);
    public static BotManager getInstance() { return INSTANCE; }
    public BotConnection join(MinecraftClient client, String username) { return transitionManager.preserveActiveAndPrepare(client, username); }
    public SessionIdentity setNextOfflineIdentity(String username) { return transitionManager.setNextOfflineIdentity(username); }
    public Collection<BotConnection> list() { return store.all(); }
    public java.util.Optional<BotConnection> findByHandler(net.minecraft.client.network.ClientPlayNetworkHandler handler) { return store.all().stream().filter(c -> c.getHandler() == handler).findFirst(); }
    public java.util.Optional<BotConnection> findByConnection(net.minecraft.network.ClientConnection connection) { return store.all().stream().filter(c -> c.getConnection() == connection).findFirst(); }
    public void remove(BotConnection connection) { store.remove(connection.getUsername()); }
    public boolean leave(String username) { return store.remove(username).map(c -> { c.close(); return true; }).orElse(false); }
    public int leaveAll() { int count = 0; for (BotConnection c : store.all()) { if (store.remove(c.getUsername()).isPresent()) { c.close(); count++; } } return count; }
    public SessionIdentity getNextIdentity() { return transitionManager.getNextIdentity(); }
    public static String formatDuration(Duration duration) { long s = duration.getSeconds(); return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60); }
}
