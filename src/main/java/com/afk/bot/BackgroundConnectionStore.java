package com.afk.bot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BackgroundConnectionStore {
    private final ConcurrentMap<String, BotConnection> sessions = new ConcurrentHashMap<>();
    public boolean put(BotConnection connection) { return sessions.putIfAbsent(key(connection.getUsername()), connection) == null; }
    public Optional<BotConnection> get(String username) { return Optional.ofNullable(sessions.get(key(username))); }
    public Collection<BotConnection> all() { return new ArrayList<>(sessions.values()); }
    public Optional<BotConnection> remove(String username) { return Optional.ofNullable(sessions.remove(key(username))); }
    public void clear() { sessions.clear(); }
    private static String key(String username) { return username.toLowerCase(Locale.ROOT); }
}
