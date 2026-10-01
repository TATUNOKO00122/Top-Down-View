package com.topdownview.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.topdownview.client.HiddenBodyRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * プレイヤー描画の直接フック。
 * {@code PlayerRenderer.render} は冒頭で {@code setModelProperties} により
 * setAllVisible(true) を実行してスキン設定通りにパーツ可視性を書き直すため、
 * シルエット描画時はこの直後に2層目スキンを非表示にする。
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onHideSecondLayer(AbstractClientPlayer entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!HiddenBodyRenderer.HiddenBodyState.isActive()) {
            return;
        }

        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) ((com.topdownview.mixin.LivingEntityRendererAccessor) this)
                .topdownview_getModel();
        if (model != null) {
            model.hat.visible = false;
            model.jacket.visible = false;
            model.leftSleeve.visible = false;
            model.rightSleeve.visible = false;
            model.leftPants.visible = false;
            model.rightPants.visible = false;
        }
    }
}
