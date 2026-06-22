package com.afk.bot;

import net.minecraft.network.packet.s2c.play.*;

public final class PacketHandler {
    private final BotConnection connection;
    PacketHandler(BotConnection connection) { this.connection = connection; }
    public void onKeepAlive(KeepAliveS2CPacket packet) { connection.markKeepAlive(); connection.sendKeepAlive(packet.getId()); }
    public void onDisconnect(DisconnectS2CPacket packet) { connection.close(); }
    public void onGameJoin(GameJoinS2CPacket packet) { connection.markKeepAlive(); }
    public void onPlayerPositionLook(PlayerPositionLookS2CPacket packet) { connection.markKeepAlive(); }
    public void onGameMessage(GameMessageS2CPacket packet) { connection.markKeepAlive(); }
    public void onEntitySpawn(EntitySpawnS2CPacket packet) { connection.markKeepAlive(); }
    public void onPlayerList(PlayerListS2CPacket packet) { connection.markKeepAlive(); }
    public void onPlayerRespawn(PlayerRespawnS2CPacket packet) { connection.markKeepAlive(); }
}
