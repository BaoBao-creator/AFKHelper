package com.afk.proxy;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class AutoIpErrorLog {
    private static final Logger LOGGER = LogManager.getLogger("AFKHelper/AutoIpErrorLog");
    private static final String LOG_FILE_NAME = "afkhelper-auto-ip-errors.txt";

    private AutoIpErrorLog() { }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(LOG_FILE_NAME);
    }

    public static void write(String message) {
        write(message, null);
    }

    public static synchronized void write(String message, Throwable throwable) {
        StringBuilder entry = new StringBuilder()
                .append('[').append(Instant.now()).append("] ")
                .append(message == null || message.isBlank() ? "Unknown auto IP error" : message.strip())
                .append(System.lineSeparator());
        if (throwable != null) {
            StringWriter stackTrace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stackTrace));
            entry.append(stackTrace);
        }
        entry.append(System.lineSeparator());

        try {
            Path logPath = path();
            Files.createDirectories(logPath.getParent());
            Files.writeString(logPath, entry.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Failed to write auto IP error log", e);
        }
    }
}
