package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.audio.Listener;

/**
 * SoundEngineMixin
 * トップダウンビュー時にサウンドリスナーの位置をプレイヤーの頭に固定する
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Shadow
    private Listener listener;

    /**
     * tick の最後でリスナー位置をプレイヤーの頭に上書きする
     * pPausedパラメータをbooleanで受け取る
     */
    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void onTick(boolean pPaused, CallbackInfo ci) {
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && this.listener != null) {
            // プレイヤーの頭（アイハイト）の位置でリスナー位置を上書き
            Vec3 eyePos = player.getEyePosition(mc.getFrameTime());
            this.listener.setListenerPosition(eyePos);
        }
    }
}
