package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * LevelRenderer Mixin
 * 
 * 1. Entity Culling対応（優先度100）
 * 2. ターゲットアウトライン色変更（EpicFight式）
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

    @Shadow
    private RenderBuffers renderBuffers;

    private static Class<?> cullableClass = null;
    private static Method setCulledMethod = null;
    private static Method setOutOfCameraMethod = null;
    private static boolean entityCullingLoaded = false;
    private static boolean reflectionInitialized = false;

    static {
        initializeReflection();
    }

    private static void initializeReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            cullableClass = Class.forName("dev.tr7zw.entityculling.access.Cullable");
            setCulledMethod = cullableClass.getMethod("setCulled", boolean.class);
            setOutOfCameraMethod = cullableClass.getMethod("setOutOfCamera", boolean.class);
            entityCullingLoaded = true;
        } catch (ClassNotFoundException e) {
            // Entity Culling MODがインストールされていない
        } catch (NoSuchMethodException e) {
            // メソッドシグネチャが変更されている
        }
    }

    /**
     * Entity Culling対応：プレイヤーのカリングを防止
     */
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void onRenderEntityHead(Entity entity, double camX, double camY, double camZ,
            float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!entityCullingLoaded) return;

        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return;

        try {
            if (cullableClass.isInstance(entity)) {
                setCulledMethod.invoke(entity, false);
                setOutOfCameraMethod.invoke(entity, false);
            }
        } catch (Exception e) {
            // リフレクションエラーは無視
        }
    }

    /**
     * ターゲットアウトライン色変更（EpicFight式）
     * OutlineBufferSource.setColor()の直後に色を上書き
     */
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OutlineBufferSource;setColor(IIII)V",
            shift = At.Shift.AFTER
        )
    )
    private void onRenderLevelOutline(CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 現在のターゲットを取得
        Entity target = ModState.TARGET_HIGHLIGHT.getCurrentTarget();
        if (target == null) return;

        // アウトライン色を設定
        int[] color = ModState.TARGET_HIGHLIGHT.getOutlineColor();
        this.renderBuffers.outlineBufferSource().setColor(color[0], color[1], color[2], color[3]);
    }
}
