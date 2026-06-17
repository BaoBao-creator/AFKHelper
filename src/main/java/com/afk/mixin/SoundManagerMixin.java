package com.afk.mixin;

import com.afk.AfkClientMod;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.TickableSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = {"play", "playNextTick", "tick", "updateListenerPosition", "reloadSounds"}, at = @At("HEAD"), cancellable = true)
    private void afkhelper$skipSound(CallbackInfo ci) {
        if (AfkClientMod.isAfkEnabled()) ci.cancel();
    }
}
