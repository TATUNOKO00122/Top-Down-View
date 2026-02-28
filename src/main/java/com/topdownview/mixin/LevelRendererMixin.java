package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * LevelRenderer Mixin
 * 
 * 優先度100: Entity Culling等の軽量化MODより先に実行し、
 * トップダウンビュー時にプレイヤーのカリングを防止する
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

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
}
