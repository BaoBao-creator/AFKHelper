package com.afk.command;

import com.afk.bot.BotConnection;
import com.afk.bot.BotManager;
import com.afk.bot.SessionIdentity;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;

public final class BotCommands {
    private BotCommands() { }

    public static int join(FabricClientCommandSource source, String username) {
        try {
            BotConnection connection = BotManager.getInstance().join(source.getClient(), username);
            source.sendFeedback(new LiteralText("Preserved " + connection.getUsername() + " in background; next offline profile is " + username + "."));
            return 1;
        } catch (RuntimeException e) {
            source.sendError(new LiteralText("/bot join failed: " + e.getMessage()));
            return 0;
        }
    }

    public static int list(FabricClientCommandSource source) {
        BotManager manager = BotManager.getInstance();
        SessionIdentity next = manager.getNextIdentity();
        source.sendFeedback(new LiteralText("AFKHelper sessions:"));
        if (source.getClient().getNetworkHandler() != null) {
            source.sendFeedback(new LiteralText(" - " + source.getClient().getSession().getUsername() + " ACTIVE status=connected ping=client uptime=n/a"));
        }
        for (BotConnection c : manager.list()) {
            String ping = c.getPing() >= 0 ? c.getPing() + "ms" : "unknown";
            source.sendFeedback(new LiteralText(" - " + c.getUsername() + " BACKGROUND status=" + c.getState() + " open=" + c.isOpen() + " ping=" + ping + " uptime=" + BotManager.formatDuration(c.getUptime()) + " server=" + c.getServerAddress()));
        }
        if (next != null) source.sendFeedback(new LiteralText("Next active offline profile: " + next.username()));
        return 1;
    }

    public static int leave(FabricClientCommandSource source, String username) {
        boolean removed = BotManager.getInstance().leave(username);
        if (removed) {
            source.sendFeedback(new LiteralText("Closed background session " + username + "."));
            return 1;
        }
        source.sendError(new LiteralText("No background session named " + username + "."));
        return 0;
    }

    public static int leaveAll(FabricClientCommandSource source) {
        int count = BotManager.getInstance().leaveAll();
        source.sendFeedback(new LiteralText("Closed " + count + " background session(s)."));
        return 1;
    }
}
