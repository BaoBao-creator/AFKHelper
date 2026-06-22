package com.afk.proxy;

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
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProxyProvider {
    private static final Logger LOGGER = LogManager.getLogger("AFKHelper/ProxyProvider");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final int VERIFY_TIMEOUT_MS = 3000;
    private static final int MAX_READY_PROXIES = 16;
    private static final Pattern HOST_PORT = Pattern.compile("(?m)^\\s*([A-Za-z0-9.-]+):(\\d{2,5})(?=\\s|$)");

    // Use one public source only. SOCKS5 is preferred for Minecraft because Netty can tunnel the raw TCP protocol directly.
    private static final String PROXYSCRAPE_SOCKS5 = "https://api.proxyscrape.com/v4/free-proxy-list/get?request=getproxies&protocol=socks5&timeout=5000&country=all&ssl=all&anonymity=all";
    private static final ProxyProvider INSTANCE = new ProxyProvider();

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ExecutorService warmupExecutor = Executors.newSingleThreadExecutor(new WarmupThreadFactory());
    private final Queue<ProxyEndpoint> candidates = new ArrayDeque<>();
    private final Queue<ProxyEndpoint> ready = new ArrayDeque<>();
    private final Set<String> usedProxyKeys = new LinkedHashSet<>();
    private final AtomicBoolean warmupRunning = new AtomicBoolean();

    private volatile InetSocketAddress warmupTarget;

    public static ProxyProvider getInstance() { return INSTANCE; }

    public void startWarmup() {
        startWarmup(null);
    }

    public void startWarmup(InetSocketAddress targetServer) {
        if (targetServer != null) {
            InetSocketAddress previousTarget = warmupTarget;
            warmupTarget = targetServer;
            if (previousTarget != null && !sameTarget(previousTarget, targetServer)) {
                synchronized (this) {
                    ready.clear();
                }
            }
        }
        if (warmupRunning.compareAndSet(false, true)) {
            warmupExecutor.execute(this::warmupLoop);
        }
    }

    public Optional<ProxyEndpoint> getReadyProxy(InetSocketAddress targetServer) {
        if (targetServer != null) {
            warmupTarget = targetServer;
            startWarmup(targetServer);
        }
        synchronized (this) {
            while (!ready.isEmpty()) {
                ProxyEndpoint endpoint = ready.poll();
                if (usedProxyKeys.add(endpoint.key())) {
                    LOGGER.info("Using pre-verified {} proxy {} for AFK bot join to {}:{}", endpoint.type(), endpoint.key(), targetServer.getHostString(), targetServer.getPort());
                    return Optional.of(endpoint);
                }
            }
        }
        String target = targetServer == null ? "the selected server" : targetServer.getHostString() + ":" + targetServer.getPort();
        String message = "No pre-verified public proxy is ready for " + target + "; refusing AFK bot join instead of blocking on Connecting to server or reusing the normal IP.";
        LOGGER.error(message);
        AutoIpErrorLog.write(message);
        return Optional.empty();
    }

    private void warmupLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                InetSocketAddress target = warmupTarget;
                if (target == null) {
                    fetchCandidatesIfNeeded();
                } else {
                    fillReadyQueue(target);
                }
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.debug("Auto IP proxy warmup failed", e);
                AutoIpErrorLog.write("Auto IP proxy warmup failed", e);
            }
        }
    }

    private void fillReadyQueue(InetSocketAddress targetServer) {
        while (readySize() < MAX_READY_PROXIES) {
            ProxyEndpoint endpoint = pollCandidate();
            if (endpoint == null) {
                fetchCandidatesIfNeeded();
                endpoint = pollCandidate();
                if (endpoint == null) return;
            }
            if (usedProxyKeys.contains(endpoint.key())) continue;
            if (canTunnelToServer(endpoint, targetServer)) {
                String endpointKey = endpoint.key();
                synchronized (this) {
                    if (!usedProxyKeys.contains(endpointKey) && ready.stream().noneMatch(existing -> existing.key().equals(endpointKey))) {
                        ready.add(endpoint);
                        LOGGER.info("Prepared {} proxy {} for AFK bot joins to {}:{}", endpoint.type(), endpoint.key(), targetServer.getHostString(), targetServer.getPort());
                    }
                }
            }
        }
    }

    private synchronized int readySize() { return ready.size(); }

    private synchronized ProxyEndpoint pollCandidate() { return candidates.poll(); }

    private void fetchCandidatesIfNeeded() {
        synchronized (this) {
            if (!candidates.isEmpty()) return;
        }

        Set<ProxyEndpoint> fetched = new LinkedHashSet<>();
        fetchRaw(fetched, PROXYSCRAPE_SOCKS5, ProxyEndpoint.ProxyType.SOCKS5);

        synchronized (this) {
            candidates.addAll(fetched);
        }
        LOGGER.info("Fetched {} unique SOCKS5 proxy candidate(s) from ProxyScrape", fetched.size());
    }

    private void fetchRaw(Set<ProxyEndpoint> sink, String url, ProxyEndpoint.ProxyType type) {
        try {
            String body = request(url);
            Matcher matcher = HOST_PORT.matcher(body);
            while (matcher.find()) add(sink, matcher.group(1), matcher.group(2), type);
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to fetch proxy list from {}", url, e);
            AutoIpErrorLog.write("Failed to fetch auto IP proxy list from " + url, e);
        }
    }

    private String request(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();
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
            return verifySocks5Tunnel(socket, targetServer);
        } catch (IOException e) {
            AutoIpErrorLog.write("Auto IP proxy " + endpoint.key() + " failed to tunnel to " + targetServer.getHostString() + ":" + targetServer.getPort(), e);
            return false;
        }
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

    private static boolean sameTarget(InetSocketAddress first, InetSocketAddress second) {
        return first.getPort() == second.getPort() && first.getHostString().equalsIgnoreCase(second.getHostString());
    }

    private static void add(Set<ProxyEndpoint> sink, String host, String portText, ProxyEndpoint.ProxyType type) {
        if (host == null || host.isBlank() || portText == null) return;
        try {
            int port = Integer.parseInt(portText.trim());
            if (port > 0 && port <= 65535) sink.add(new ProxyEndpoint(host.trim(), port, type));
        } catch (NumberFormatException ignored) { }
    }

    private static final class WarmupThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "AFKHelper-ProxyWarmup");
            thread.setDaemon(true);
            return thread;
        }
    }
}
