package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SlimeOuterLayer.class)
public abstract class SlimeOuterLayerMixin<T extends LivingEntity> {

    // スライムの外殻のモデル描画時に、アルファチャンネル（引数 7）を設定した透明度に置き換えて描画を実行する
    @ModifyArg(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
        index = 7
    )
    private float modifyAlpha(float originalAlpha) {
        // MODが無効、またはMobの半透明設定が無効な場合は何もしない
        if (!ModState.STATUS.isEnabled() || !Config.isMobTranslucencyEnabled()) {
            return originalAlpha;
        }

        // 設定された透明度（アルファ値）を適用する
        return (float) Config.getMobTranslucencyAlpha();
    }
}
