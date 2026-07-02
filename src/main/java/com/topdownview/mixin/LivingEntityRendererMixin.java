package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    // MOBの標準的な不透明描画指定を、半透明を許容する描画指定（RenderType.entityTranslucent()）へ変更する
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        // MODが無効、またはMobの半透明設定が無効な場合は何もしない
        if (!ModState.STATUS.isEnabled() || !Config.isMobTranslucencyEnabled()) {
            return;
        }

        // キャストを介してスーパークラス（EntityRenderer）のgetTextureLocationを呼び出す
        // これにより、extendsによるMixin의 メソッド解決競合を防ぎつつ、難読化実行時のリマップも正しく適用される
        ResourceLocation texture = ((EntityRenderer<T>) (Object) this).getTextureLocation(entity);
        cir.setReturnValue(RenderType.entityTranslucent(texture));
    }

    // 描画時のカラー情報（RGBA）において、A（アルファチャンネル）の数値を1.0未満に設定して描画を実行する
    @ModifyArg(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
        index = 7
    )
    private float modifyAlpha(float originalAlpha) {
        // MODが無効、またはMobの半透明設定が無効な場合は元のアルファ値をそのまま使用する
        if (!ModState.STATUS.isEnabled() || !Config.isMobTranslucencyEnabled()) {
            return originalAlpha;
        }

        // 設定された透明度（アルファ値）を適用する
        return (float) Config.getMobTranslucencyAlpha();
    }
}
