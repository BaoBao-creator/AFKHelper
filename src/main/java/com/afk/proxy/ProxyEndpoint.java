package com.afk.proxy;

public record ProxyEndpoint(String host, int port, ProxyType type) {
    public String key() { return host + ":" + port; }
    public enum ProxyType { HTTP, SOCKS5 }
}
