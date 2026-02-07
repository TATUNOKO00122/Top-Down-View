package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer Mixin
 * 
 * 優先度100: Entity Culling等の軽量化MODより先に実行し、
 * トップダウンビュー時にプレイヤーのカリングを防止する
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

    /**
     * renderEntityメソッドの先頭でプレイヤーのカリング状態をリセット
     * 
     * Entity Culling MODはCullableインターフェースを通じてエンティティに
     * カリング状態を設定する。このMixinでプレイヤーを強制的に可視状態にする。
     */
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void onRenderEntityHead(Entity entity, double camX, double camY, double camZ,
            float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) {
            // Entity CullingのCullableインターフェースを通じてカリングを無効化
            // Cullable.setCulled(false)を呼び出す
            try {
                // リフレクションでCullableインターフェースにアクセス
                // Entity CullingがロードされていなくてもクラッシュしないようにOptionalで処理
                Class<?> cullableClass = Class.forName("dev.tr7zw.entityculling.access.Cullable");
                if (cullableClass.isInstance(entity)) {
                    java.lang.reflect.Method setCulledMethod = cullableClass.getMethod("setCulled", boolean.class);
                    setCulledMethod.invoke(entity, false);

                    // timeout/遅延リセット用のsetOutOfCameraも呼び出し
                    java.lang.reflect.Method setOutOfCameraMethod = cullableClass.getMethod("setOutOfCamera",
                            boolean.class);
                    setOutOfCameraMethod.invoke(entity, false);
                }
            } catch (ClassNotFoundException e) {
                // Entity Culling MODがインストールされていない場合は無視
            } catch (Exception e) {
                // その他のエラーも無視（パフォーマンス影響を最小化）
            }
        }
    }
}
