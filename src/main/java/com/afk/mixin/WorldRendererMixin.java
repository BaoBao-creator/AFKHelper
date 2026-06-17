package com.afk.mixin;

import com.afk.AfkClientMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipWorldRender(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (AfkClientMod.isAfkEnabled()) ci.cancel();
    }

    @Inject(method = {"tick", "reload", "scheduleTerrainUpdate", "drawEntityOutlinesFramebuffer", "tickRainSplashing", "renderSky", "renderClouds", "addParticle", "processGlobalEvent", "processWorldEvent", "setBlockBreakingInfo", "updateNoCullingBlockEntities", "scheduleBlockRenders", "scheduleBlockRender", "scheduleBlockRerenderIfNeeded", "updateBlock", "playSong"}, at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipRendererWork(CallbackInfo ci) {
        if (AfkClientMod.isAfkEnabled()) ci.cancel();
    }
}
