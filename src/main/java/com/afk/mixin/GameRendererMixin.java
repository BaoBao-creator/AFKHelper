package com.afk.mixin;

import com.afk.AfkClientMod;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = {"renderWithZoom", "showFloatingItem"}, at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipOptionalVisualEffects(CallbackInfo info) {
        if (AfkClientMod.isAfkEnabled()) {
            info.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipWorldRendering(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo info) {
        if (AfkClientMod.isAfkEnabled()) {
            info.cancel();
        }
    }
}
