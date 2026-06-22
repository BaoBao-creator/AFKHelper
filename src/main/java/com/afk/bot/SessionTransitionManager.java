package com.afk.bot;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps AFKHelper's offline/bot identity separate from MinecraftClient.session.
 *
 * <p>Fabric/Minecraft 1.18 does not provide a supported way to swap the running client's private final
 * session. The old implementation used an accessor to rewrite that field and then nulled client.player/world;
 * both operations corrupt vanilla client state. This manager now only prepares an internal bot identity that
 * AFKHelper can track without altering the active client.</p>
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
        SessionIdentity identity = setNextOfflineIdentity(username);
        BotConnection bot = BotConnection.internalProfile(identity);
        if (!store.put(bot)) {
            throw new IllegalStateException(identity.username() + " is already tracked.");
        }
        return bot;
    }
}
