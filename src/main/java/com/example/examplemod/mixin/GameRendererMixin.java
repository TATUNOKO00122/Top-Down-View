package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void onBobView(PoseStack pPoseStack, float pPartialTicks, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView) {
            ci.cancel();
        }
    }

    @Inject(method = "pick", at = @At("TAIL"))
    private void onPick(float partialTicks, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView) {
            Minecraft mc = Minecraft.getInstance();
            // カスタムリーチ距離でレイキャスト
            // GUIが開いているときはデフォルトの挙動（このMixinはGUI描画前のワールドレンダリング中に呼ばれるはず）
            // ただし、pickは毎フレーム呼ばれる。

            net.minecraft.world.phys.HitResult result = com.example.examplemod.client.MouseRaycast.getHitResult(mc,
                    partialTicks, com.example.examplemod.client.MouseRaycast.CUSTOM_REACH_DISTANCE);
            mc.hitResult = result;

            if (result instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
                mc.crosshairPickEntity = entityHit.getEntity();
            } else {
                mc.crosshairPickEntity = null;
            }
        }
    }
}
