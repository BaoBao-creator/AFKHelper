package com.afk.mixin;

import com.afk.bot.BotManager;
import com.afk.proxy.BotProxyContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private static void afkhelper$beginJoinServerConnect(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, CallbackInfo ci) {
        if (BotManager.getInstance().getNextIdentity() != null) {
            BotProxyContext.beginJoinServerConnect();
        }
    }

    @Inject(method = "connect", at = @At("RETURN"))
    private static void afkhelper$endJoinServerConnect(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, CallbackInfo ci) {
        BotProxyContext.endJoinServerConnect();
    }
}
