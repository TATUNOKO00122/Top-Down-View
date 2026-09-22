package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium(Sodium)用 SodiumWorldRenderer Mixin。
 *
 * Embeddium はエンティティを「カメラ基準で構築された可視セクション」との交差で追加カリングする。
 * トップダウンビューではカメラがプレイヤーから大きく離れ・見下ろすため、プレイヤー周辺の
 * ドロップアイテム等のセクションが可視集合に入らず消える。トップダウン中はこのカメラ基準の
 * 判定を行わない（プレイヤー周辺は表示する）。視錐台カリングはバニラ側で維持される。
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void onIsEntityVisible(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
