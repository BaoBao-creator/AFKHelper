package com.afk.mixin;

import com.afk.bot.BotManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void afkhelper$keepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onKeepAlive(packet)); }
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void afkhelper$disconnect(DisconnectS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onDisconnect(packet)); }
    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void afkhelper$join(GameJoinS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onGameJoin(packet)); }
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void afkhelper$pos(PlayerPositionLookS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onPlayerPositionLook(packet)); }
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void afkhelper$msg(GameMessageS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onGameMessage(packet)); }
    @Inject(method = "onEntitySpawn", at = @At("HEAD"))
    private void afkhelper$spawn(EntitySpawnS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onEntitySpawn(packet)); }
    @Inject(method = "onPlayerList", at = @At("HEAD"))
    private void afkhelper$list(PlayerListS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onPlayerList(packet)); }
    @Inject(method = "onPlayerRespawn", at = @At("HEAD"))
    private void afkhelper$respawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) { BotManager.getInstance().findByHandler((ClientPlayNetworkHandler)(Object)this).ifPresent(c -> c.getPacketHandler().onPlayerRespawn(packet)); }
}
