package com.afk.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.concurrent.TimeUnit;

import java.net.InetSocketAddress;

public final class ProxyInjectingChannelInitializer extends ChannelInitializer<Channel> {
    public static final int PROXY_CONNECT_TIMEOUT_MS = 1_500;
    private static final int PROXY_READ_TIMEOUT_SECONDS = 5;
    private final ChannelHandler vanillaInitializer;
    private final ProxyEndpoint proxyEndpoint;

    public ProxyInjectingChannelInitializer(ChannelHandler vanillaInitializer, ProxyEndpoint proxyEndpoint) {
        this.vanillaInitializer = vanillaInitializer;
        this.proxyEndpoint = proxyEndpoint;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        ProxyHandler handler = proxyEndpoint.type() == ProxyEndpoint.ProxyType.SOCKS5
                ? new Socks5ProxyHandler(new InetSocketAddress(proxyEndpoint.host(), proxyEndpoint.port()))
                : new HttpProxyHandler(new InetSocketAddress(proxyEndpoint.host(), proxyEndpoint.port()));
        handler.setConnectTimeoutMillis(PROXY_CONNECT_TIMEOUT_MS);
        pipeline.addFirst("afkhelper_proxy", handler);
        pipeline.addAfter("afkhelper_proxy", "afkhelper_proxy_read_timeout", new ReadTimeoutHandler(PROXY_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        pipeline.addLast(vanillaInitializer);
    }
}
