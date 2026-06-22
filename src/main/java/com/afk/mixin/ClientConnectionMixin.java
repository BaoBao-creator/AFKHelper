package com.afk.mixin;

import com.afk.bot.BotConnection;
import com.afk.bot.BotManager;
import com.afk.proxy.BotProxyContext;
import com.afk.proxy.ProxyEndpoint;
import com.afk.proxy.ProxyInjectingChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Redirect(
            method = "connect",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;")
    )
    private static io.netty.bootstrap.AbstractBootstrap<?, ?> afkhelper$injectProxyHandler(Bootstrap bootstrap, ChannelHandler handler, InetSocketAddress address, boolean useEpoll) {
        Optional<ProxyEndpoint> proxy = BotProxyContext.claimProxyIfBotJoin(address);
        proxy.ifPresent(endpoint -> bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ProxyInjectingChannelInitializer.PROXY_CONNECT_TIMEOUT_MS));
        return bootstrap.handler(proxy.<ChannelHandler>map(endpoint -> new ProxyInjectingChannelInitializer(handler, endpoint)).orElse(handler));
    }

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
        } else if (packet instanceof PlayPingS2CPacket ping) {
            bot.getPacketHandler().onPlayPing(ping);
        } else if (packet instanceof ResourcePackSendS2CPacket resourcePack) {
            bot.getPacketHandler().onResourcePack(resourcePack);
        } else if (packet instanceof DisconnectS2CPacket disconnect) {
            bot.getPacketHandler().onDisconnect(disconnect);
        } else if (packet instanceof GameJoinS2CPacket gameJoin) {
            bot.getPacketHandler().onGameJoin(gameJoin);
        } else if (packet instanceof PlayerRespawnS2CPacket respawn) {
            bot.getPacketHandler().onPlayerRespawn(respawn);
        } else if (packet instanceof PlayerPositionLookS2CPacket playerPositionLook) {
            bot.getPacketHandler().onPlayerPositionLook(playerPositionLook);
        } else if (packet instanceof DeathMessageS2CPacket death) {
            bot.getPacketHandler().onDeath(death);
        } else {
            bot.getPacketHandler().onBackgroundPacket(packet);
        }
    }
}
