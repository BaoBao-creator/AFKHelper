package com.afk.mixin;

import com.afk.bot.BotConnection;
import com.afk.bot.BotManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void afkhelper$routeBackgroundPacket(io.netty.channel.ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        BotManager.getInstance().findByConnection((ClientConnection) (Object) this).ifPresent(bot -> {
            route(bot, packet);
            ci.cancel();
        });
    }

    private static void route(BotConnection bot, Packet<?> packet) {
        if (packet instanceof KeepAliveS2CPacket keepAlive) {
            bot.getPacketHandler().onKeepAlive(keepAlive);
        } else if (packet instanceof DisconnectS2CPacket disconnect) {
            bot.getPacketHandler().onDisconnect(disconnect);
        } else {
            bot.getPacketHandler().onBackgroundPacket(packet);
        }
    }
}
