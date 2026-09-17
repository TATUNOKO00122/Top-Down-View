package com.topdownview.mixin.compat.distanthorizons;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons互換。
 *
 * バニラのチャンク描画はプレイヤー中心だが、DHのLODはカメラ中心で近傍を破棄する。
 * トップダウンはカメラがプレイヤーから離れるため両者の範囲がズレ、
 * TopDownViewがカリングした穴からDHのLODが覗いてしまう。
 *
 * そこでプレイヤーのバニラ描画距離内に完全に収まるLODセクションをカリングし、
 * DHの描画範囲をバニラ描画範囲に合わせる（穴も残らない）。
 *
 * DH未導入環境では {@code @Pseudo} + required:false により適用されない。
 * 対象は既定のカリングFrustum（DhFrustumBounds）。影パスは別Frustumのため影響しない。
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.cullingFrustum.DhFrustumBounds", remap = false)
public class DhFrustumBoundsMixin {

    @Inject(method = "intersects", at = @At("HEAD"), cancellable = true, require = 0)
    private void topdownview$cullLodWithinVanillaRadius(
            int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (topdownview$isWithinVanillaRadius(lodBlockPosMinX, lodBlockPosMinZ, lodBlockWidth)) {
            cir.setReturnValue(false);
        }
    }

    /** セクション全体がプレイヤーのバニラ描画距離内か（隅がすべて内側） */
    private static boolean topdownview$isWithinVanillaRadius(int minX, int minZ, int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }

        double radius = mc.options.getEffectiveRenderDistance() * 16.0;
        if (!(radius > 0.0)) {
            return false;
        }

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        double maxDx = Math.max(Math.abs(minX - playerX), Math.abs((minX + width) - playerX));
        double maxDz = Math.max(Math.abs(minZ - playerZ), Math.abs((minZ + width) - playerZ));
        return maxDx * maxDx + maxDz * maxDz < radius * radius;
    }
}
