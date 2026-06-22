package com.afk.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProxyProvider {
    private static final Logger LOGGER = LogManager.getLogger("AFKHelper/ProxyProvider");
    private static final ProxyProvider INSTANCE = new ProxyProvider();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);
    private static final int VERIFY_TIMEOUT_MS = 500;
    private static final Pattern HOST_PORT = Pattern.compile("(?m)([A-Za-z0-9.-]+):(\\d{2,5})");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final Queue<ProxyEndpoint> candidates = new ArrayDeque<>();
    private final Set<String> usedProxyKeys = new LinkedHashSet<>();

    public static ProxyProvider getInstance() { return INSTANCE; }

    public synchronized Optional<ProxyEndpoint> acquireVerifiedProxy(InetSocketAddress targetServer) {
        for (int round = 0; round < 2; round++) {
            if (candidates.isEmpty()) fetchCandidates();
            while (!candidates.isEmpty()) {
                ProxyEndpoint endpoint = candidates.poll();
                if (usedProxyKeys.contains(endpoint.key())) continue;
                if (canTunnelToServer(endpoint, targetServer)) {
                    usedProxyKeys.add(endpoint.key());
                    LOGGER.info("Using {} proxy {} for AFK bot join to {}:{}", endpoint.type(), endpoint.key(), targetServer.getHostString(), targetServer.getPort());
                    return Optional.of(endpoint);
                }
            }
        }
        LOGGER.error("No public proxy passed the fast pre-check or could tunnel to {}:{}; refusing AFK bot join instead of reusing the normal IP.", targetServer.getHostString(), targetServer.getPort());
        return Optional.empty();
    }

    private void fetchCandidates() {
        Set<ProxyEndpoint> fetched = new LinkedHashSet<>();
        fetchJson(fetched, "https://proxylist.geonode.com/api/proxy-list?limit=200&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Csocks5", ProxyEndpoint.ProxyType.HTTP);
        fetchRaw(fetched, "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt", ProxyEndpoint.ProxyType.HTTP);
        fetchRaw(fetched, "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt", ProxyEndpoint.ProxyType.SOCKS5);
        candidates.addAll(fetched);
        LOGGER.info("Fetched {} unique public proxy candidate(s)", fetched.size());
    }

    private void fetchRaw(Set<ProxyEndpoint> sink, String url, ProxyEndpoint.ProxyType type) {
        try {
            String body = request(url);
            Matcher matcher = HOST_PORT.matcher(body);
            while (matcher.find()) add(sink, matcher.group(1), matcher.group(2), type);
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to fetch proxy list from {}", url, e);
        }
    }

    private void fetchJson(Set<ProxyEndpoint> sink, String url, ProxyEndpoint.ProxyType fallbackType) {
        try {
            JsonObject root = JsonParser.parseString(request(url)).getAsJsonObject();
            JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String host = string(item, "ip");
                String port = string(item, "port");
                ProxyEndpoint.ProxyType type = parseType(item, fallbackType);
                add(sink, host, port, type);
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to fetch proxy JSON from {}", url, e);
        }
    }

    private String request(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("HTTP " + response.statusCode());
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private boolean canTunnelToServer(ProxyEndpoint endpoint, InetSocketAddress targetServer) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), VERIFY_TIMEOUT_MS);
            socket.setSoTimeout(VERIFY_TIMEOUT_MS);
            return endpoint.type() == ProxyEndpoint.ProxyType.SOCKS5
                    ? verifySocks5Tunnel(socket, targetServer)
                    : verifyHttpConnectTunnel(socket, targetServer);
        } catch (IOException ignored) { return false; }
    }

    private static boolean verifyHttpConnectTunnel(Socket socket, InetSocketAddress targetServer) throws IOException {
        String host = targetServer.getHostString();
        int port = targetServer.getPort();
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(("CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nProxy-Connection: Keep-Alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        byte[] buffer = new byte[128];
        int read = in.read(buffer);
        if (read <= 0) return false;
        String status = new String(buffer, 0, read, StandardCharsets.US_ASCII);
        return status.startsWith("HTTP/1.0 200") || status.startsWith("HTTP/1.1 200");
    }

    private static boolean verifySocks5Tunnel(Socket socket, InetSocketAddress targetServer) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(new byte[] { 0x05, 0x01, 0x00 });
        out.flush();
        byte[] response = in.readNBytes(2);
        if (response.length != 2 || response[0] != 0x05 || response[1] != 0x00) return false;

        byte[] host = targetServer.getHostString().getBytes(StandardCharsets.US_ASCII);
        if (host.length > 255) return false;
        int port = targetServer.getPort();
        byte[] request = new byte[7 + host.length];
        request[0] = 0x05;
        request[1] = 0x01;
        request[2] = 0x00;
        request[3] = 0x03;
        request[4] = (byte) host.length;
        System.arraycopy(host, 0, request, 5, host.length);
        request[5 + host.length] = (byte) ((port >> 8) & 0xFF);
        request[6 + host.length] = (byte) (port & 0xFF);
        out.write(request);
        out.flush();

        byte[] header = in.readNBytes(4);
        return header.length == 4 && header[0] == 0x05 && header[1] == 0x00;
    }

    private static void add(Set<ProxyEndpoint> sink, String host, String portText, ProxyEndpoint.ProxyType type) {
        if (host == null || host.isBlank() || portText == null) return;
        try {
            int port = Integer.parseInt(portText.trim());
            if (port > 0 && port <= 65535) sink.add(new ProxyEndpoint(host.trim(), port, type));
        } catch (NumberFormatException ignored) { }
    }

    private static String string(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null; }

    private static ProxyEndpoint.ProxyType parseType(JsonObject item, ProxyEndpoint.ProxyType fallback) {
        if (item.has("protocols") && item.get("protocols").isJsonArray()) {
            for (JsonElement e : item.getAsJsonArray("protocols")) if ("socks5".equalsIgnoreCase(e.getAsString())) return ProxyEndpoint.ProxyType.SOCKS5;
        }
        String protocol = string(item, "protocol");
        return protocol != null && protocol.toLowerCase(Locale.ROOT).contains("socks5") ? ProxyEndpoint.ProxyType.SOCKS5 : fallback;
    }
}
