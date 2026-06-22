package com.afk.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

public final class ProxyInjectingChannelInitializer extends ChannelInitializer<Channel> {
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
        pipeline.addFirst("afkhelper_proxy", handler);
        pipeline.addLast(vanillaInitializer);
    }
}
