package com.example.examplemod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.examplemod.client.ClientForgeEvents;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * Minecraftは音声の再生可否（カリング）および初期音量を決定する際、
     * SoundEngineのcalculateVolumeメソッドで「カメラからの距離」を使用しています。
     * トップダウン視点ではカメラが遠すぎるため、ここで音量が0と判定されて完全にカリングされてしまいます。
     * これを防ぐため、プレイヤーからの距離を元に音量を再計算して上書きします。
     */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void onCalculateVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // 音源の座標を取得
            double sourceX = sound.getX();
            double sourceY = sound.getY();
            double sourceZ = sound.getZ();

            // プレイヤーからの距離を計算（カメラからの距離ではない）
            double distanceToPlayer = mc.player.distanceToSqr(sourceX, sourceY, sourceZ);
            distanceToPlayer = Math.sqrt(distanceToPlayer);

            // 音の基本Volumeに基づく最大可聴距離を算出
            // ※ vanillaの実装: max(16.0, volume * 16.0)
            float baseVolume = sound.getVolume();
            float maxDistance = Math.max(16.0F, baseVolume * 16.0F);

            // プレイヤーからの距離をもとに、Minecraft標準の線形減衰音量を計算
            float calculatedVolume = (float) (1.0D - distanceToPlayer / maxDistance);

            // 最小0.0、最大1.0 または baseVolume にクランプする
            calculatedVolume = Math.max(0.0F, Math.min(baseVolume, calculatedVolume));

            // そのまま結果を返すことでバニラの計算（カメラ距離ベース）をスキップする
            cir.setReturnValue(calculatedVolume);
        }
    }
}
