package com.afk.bot;

public final class KeepAliveManager {
    private final BotConnection connection;
    private long lastWarn;
    KeepAliveManager(BotConnection connection) { this.connection = connection; }
    public void tick() {
        long age = System.currentTimeMillis() - connection.getLastKeepAliveMillis();
        if (age > 25_000L && System.currentTimeMillis() - lastWarn > 5_000L) lastWarn = System.currentTimeMillis();
    }
    public void respond(long id) { connection.sendKeepAlive(id); }
}
