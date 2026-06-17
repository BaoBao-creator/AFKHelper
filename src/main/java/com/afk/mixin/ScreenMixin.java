package com.afk.mixin;

import com.afk.AfkClientMod;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = {"render", "tick", "renderBackground(Lnet/minecraft/client/util/math/MatrixStack;)V", "renderBackground(Lnet/minecraft/client/util/math/MatrixStack;I)V", "renderBackgroundTexture", "updateNarrator"}, at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipScreenWork(CallbackInfo ci) {
        if (AfkClientMod.isAfkEnabled()) ci.cancel();
    }
}
