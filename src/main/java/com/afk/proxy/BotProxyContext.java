package com.afk.proxy;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BotProxyContext {
    private static final AtomicBoolean NEXT_CONNECT_IS_JOIN_SERVER = new AtomicBoolean(false);
    private static final AtomicBoolean NEXT_JOIN_IS_BOT = new AtomicBoolean(false);

    private BotProxyContext() { }

    public static void markNextJoinAsBot() { NEXT_JOIN_IS_BOT.set(true); }

    public static void beginJoinServerConnect() { NEXT_CONNECT_IS_JOIN_SERVER.set(true); }

    public static void endJoinServerConnect() { }

    public static Optional<ProxyEndpoint> claimProxyIfBotJoin(InetSocketAddress serverAddress) {
        if (!NEXT_CONNECT_IS_JOIN_SERVER.compareAndSet(true, false)) return Optional.empty();
        if (!NEXT_JOIN_IS_BOT.compareAndSet(true, false)) return Optional.empty();
        Optional<ProxyEndpoint> proxy = ProxyProvider.getInstance().acquireVerifiedProxy(serverAddress);
        if (proxy.isEmpty()) {
            throw new ProxyUnavailableException("No working proxy could reach " + serverAddress.getHostString() + ":" + serverAddress.getPort() + "; cancelled join to avoid using the same IP.");
        }
        return proxy;
    }
}
