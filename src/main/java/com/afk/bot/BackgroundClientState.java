package com.afk.bot;

import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.world.GameMode;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal play-state mirror for a detached client connection.
 *
 * <p>A vanilla {@code ClientPlayNetworkHandler} owns a world, player entity and
 * chunk/entity registries. AFKHelper cannot reuse those objects for more than
 * one account because they are rooted in the singleton {@code MinecraftClient}.
 * This state stores only protocol-critical values that the server expects the
 * client to acknowledge or echo while the account is in the background.</p>
 */
public final class BackgroundClientState {
    private final AtomicLong lastMovementSent = new AtomicLong();
    private boolean joined;
    private boolean dead;
    private int entityId = -1;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private GameMode gameMode;

    public synchronized void onGameJoin(GameJoinS2CPacket packet) {
        joined = true;
        dead = false;
        entityId = packet.playerEntityId();
        gameMode = packet.gameMode();
        lastMovementSent.set(0L);
    }

    public synchronized void onRespawn(PlayerRespawnS2CPacket packet) {
        dead = false;
        gameMode = packet.getGameMode();
        lastMovementSent.set(0L);
    }

    public synchronized BotConnection.PlayerPositionSnapshot onPositionLook(PlayerPositionLookS2CPacket packet) {
        Set<PlayerPositionLookS2CPacket.Flag> flags = packet.getFlags();
        x = flags.contains(PlayerPositionLookS2CPacket.Flag.X) ? x + packet.getX() : packet.getX();
        y = flags.contains(PlayerPositionLookS2CPacket.Flag.Y) ? y + packet.getY() : packet.getY();
        z = flags.contains(PlayerPositionLookS2CPacket.Flag.Z) ? z + packet.getZ() : packet.getZ();
        yaw = flags.contains(PlayerPositionLookS2CPacket.Flag.Y_ROT) ? yaw + packet.getYaw() : packet.getYaw();
        pitch = flags.contains(PlayerPositionLookS2CPacket.Flag.X_ROT) ? pitch + packet.getPitch() : packet.getPitch();
        onGround = false;
        return snapshot(packet.getTeleportId());
    }

    public synchronized BotConnection.PlayerPositionSnapshot snapshot(int teleportId) {
        return new BotConnection.PlayerPositionSnapshot(teleportId, x, y, z, yaw, pitch, onGround);
    }

    public synchronized BotConnection.PlayerPositionSnapshot movementSnapshot() {
        return snapshot(-1);
    }

    public boolean shouldSendMovement(long nowMillis) {
        long previous = lastMovementSent.get();
        return nowMillis - previous >= 1_000L && lastMovementSent.compareAndSet(previous, nowMillis);
    }

    public synchronized void markDead() {
        dead = true;
    }

    public synchronized boolean isJoined() { return joined; }
    public synchronized boolean isDead() { return dead; }
    public synchronized int getEntityId() { return entityId; }
    public synchronized GameMode getGameMode() { return gameMode; }
}
