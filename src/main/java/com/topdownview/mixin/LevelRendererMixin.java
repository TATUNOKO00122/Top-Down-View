package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.culling.Cullable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * LevelRenderer Mixin
 * 
 * 1. エンティティカリング（トップダウン視点）
 * 2. Entity Culling MOD対応（プレイヤー保護）
 * 3. ターゲットアウトライン色変更
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

    @Shadow
    private RenderBuffers renderBuffers;

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    private static Class<?> entityCullingCullableClass = null;
    private static MethodHandle entityCullingSetCulledHandle = null;
    private static MethodHandle entityCullingSetOutOfCameraHandle = null;
    private static boolean entityCullingLoaded = false;
    private static boolean entityCullingReflectionInitialized = false;

    static {
        initEntityCullingReflection();
    }

    private static void initEntityCullingReflection() {
        if (entityCullingReflectionInitialized) return;
        entityCullingReflectionInitialized = true;

        try {
            entityCullingCullableClass = Class.forName("dev.tr7zw.entityculling.access.Cullable");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            entityCullingSetCulledHandle = lookup.findVirtual(entityCullingCullableClass, "setCulled", MethodType.methodType(void.class, boolean.class));
            entityCullingSetOutOfCameraHandle = lookup.findVirtual(entityCullingCullableClass, "setOutOfCamera", MethodType.methodType(void.class, boolean.class));
            entityCullingLoaded = true;
        } catch (ClassNotFoundException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e) {
        }
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void onRenderEntityHead(Entity entity, double camX, double camY, double camZ,
            float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();

        if (entity instanceof Player && entity == mc.player) {
            if (entityCullingLoaded && entityCullingCullableClass.isInstance(entity)) {
                try {
                    entityCullingSetCulledHandle.invoke(entity, false);
                    entityCullingSetOutOfCameraHandle.invoke(entity, false);
                } catch (Throwable e) {
                }
            }
            return;
        }

        if (entity instanceof Cullable && ((Cullable) entity).topdownview_isCulled()) {
            ci.cancel();
        }
    }

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

        Entity target = ModState.TARGET_HIGHLIGHT.getCurrentTarget();
        if (target == null) return;

        int[] color = ModState.TARGET_HIGHLIGHT.getOutlineColor();
        this.renderBuffers.outlineBufferSource().setColor(color[0], color[1], color[2], color[3]);
    }
}
