package com.afk.bot;

import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.*;

/** Routes protocol-critical play packets for a detached background client. */
public final class PacketHandler {
    private final BotConnection connection;

    PacketHandler(BotConnection connection) {
        this.connection = connection;
    }

    public void onKeepAlive(KeepAliveS2CPacket packet) {
        connection.markKeepAlive();
        connection.sendKeepAlive(packet.getId());
    }

    public void onPlayPing(PlayPingS2CPacket packet) {
        connection.sendPong(packet.getParameter());
    }

    public void onResourcePack(ResourcePackSendS2CPacket packet) {
        connection.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.ACCEPTED);
        connection.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
    }

    public void onDisconnect(DisconnectS2CPacket packet) {
        BotManager.getInstance().remove(connection);
        connection.closeSilently();
    }

    public void onGameJoin(GameJoinS2CPacket packet) {
        connection.getClientState().onGameJoin(packet);
        connection.markKeepAlive();
    }

    public void onPlayerRespawn(PlayerRespawnS2CPacket packet) {
        connection.getClientState().onRespawn(packet);
        connection.markKeepAlive();
    }

    public void onPlayerPositionLook(PlayerPositionLookS2CPacket packet) {
        connection.confirmTeleport(connection.getClientState().onPositionLook(packet));
    }

    public void onDeath(DeathMessageS2CPacket packet) {
        connection.getClientState().markDead();
        connection.requestRespawn();
    }

    /**
     * Packets that mutate only render/UI/chunk/entity caches are intentionally
     * consumed here. They must not be forwarded to the vanilla handler because
     * that handler is bound to MinecraftClient's active world/player fields.
     */
    public void onBackgroundPacket(Packet<?> packet) {
        connection.markKeepAlive();
    }

    public void onGameMessage(GameMessageS2CPacket packet) { connection.markKeepAlive(); }
    public void onEntitySpawn(EntitySpawnS2CPacket packet) { connection.markKeepAlive(); }
    public void onPlayerList(PlayerListS2CPacket packet) { connection.markKeepAlive(); }
}
