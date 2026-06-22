package com.afk.proxy;

import com.afk.bot.SessionIdentity;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class BotProxyContext {
    private static final AtomicReference<PendingBotJoin> NEXT_BOT_JOIN = new AtomicReference<>();
    private static final AtomicReference<PendingBotJoin> ACTIVE_JOIN_CONNECT = new AtomicReference<>();

    private BotProxyContext() { }

    public static void markNextJoinAsBot(SessionIdentity identity) {
        NEXT_BOT_JOIN.set(new PendingBotJoin(identity.username()));
    }

    public static void beginJoinServerConnect(SessionIdentity identity) {
        PendingBotJoin expected = NEXT_BOT_JOIN.get();
        if (identity == null || expected == null || !expected.matches(identity.username())) {
            ACTIVE_JOIN_CONNECT.set(null);
            return;
        }
        ACTIVE_JOIN_CONNECT.set(expected);
    }

    public static void endJoinServerConnect() { }

    public static Optional<ProxyEndpoint> claimProxyIfBotJoin(InetSocketAddress serverAddress) {
        PendingBotJoin pending = ACTIVE_JOIN_CONNECT.getAndSet(null);
        if (pending == null) return Optional.empty();

        Optional<ProxyEndpoint> proxy = ProxyProvider.getInstance().acquireVerifiedProxy(serverAddress);
        if (proxy.isEmpty()) {
            throw new ProxyUnavailableException("No working proxy could reach " + serverAddress.getHostString() + ":" + serverAddress.getPort() + "; cancelled bot join for " + pending.username() + " to avoid using the same IP.");
        }

        NEXT_BOT_JOIN.compareAndSet(pending, null);
        return proxy;
    }

    private record PendingBotJoin(String username) {
        private boolean matches(String candidate) {
            return candidate != null && username.toLowerCase(Locale.ROOT).equals(candidate.toLowerCase(Locale.ROOT));
        }
    }
}
