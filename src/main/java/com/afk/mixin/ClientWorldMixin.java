package com.afk.mixin;

import com.afk.AfkClientMod;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = {"runQueuedChunkUpdates", "tickEntities", "doRandomBlockDisplayTicks", "addParticle", "addImportantParticle", "addFireworkParticle", "playSound", "playSoundFromEntity", "scheduleBlockRenders", "scheduleBlockRerenderIfNeeded", "markChunkRenderability", "setBlockBreakingInfo", "syncGlobalEvent", "syncWorldEvent", "addBlockBreakParticles", "reloadColor", "resetChunkColor"}, at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipClientWorldVisuals(CallbackInfo ci) {
        if (AfkClientMod.isAfkEnabled()) ci.cancel();
    }
}
