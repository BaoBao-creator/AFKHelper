package com.afk.bot;

import net.minecraft.client.util.Session;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SessionIdentity {
    public static final Pattern OFFLINE_USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private final String username;
    private final UUID uuid;
    private final Session session;

    private SessionIdentity(String username, UUID uuid, Session session) {
        this.username = username;
        this.uuid = uuid;
        this.session = session;
    }

    public static SessionIdentity offline(String username) {
        if (!OFFLINE_USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Invalid offline username. Use 3-16 letters, numbers, or underscores.");
        }
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        Session session = new Session(username, uuid.toString(), "", Optional.empty(), Optional.empty(), Session.AccountType.LEGACY);
        return new SessionIdentity(username, uuid, session);
    }

    public static SessionIdentity from(Session session) {
        UUID uuid;
        try { uuid = UUID.fromString(session.getUuid()); } catch (RuntimeException ignored) { uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + session.getUsername()).getBytes(StandardCharsets.UTF_8)); }
        return new SessionIdentity(session.getUsername(), uuid, session);
    }

    public String username() { return username; }
    public UUID uuid() { return uuid; }
    public Session session() { return session; }
}
