package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * エンティティの表示距離判定の基準をカメラ位置からプレイヤー位置へ変更する。
 *
 * バニラの {@link Entity#shouldRenderAtSqrDistance(double)} はカメラからの距離で判定し、
 * 表示距離はエンティティのバウンディングボックス依存（ドロップアイテムは約16〜24ブロック）。
 * トップダウンビューではカメラがプレイヤーから離れるため、プレイヤー周辺の小さな
 * エンティティが消える。基準をプレイヤー位置に置き換え、カメラ距離に関係なく表示させる。
 * サブクラスの {@code shouldRenderAtSqrDistance} オーバーライドはそのまま活かす。
 */
@Mixin(Entity.class)
public abstract class EntityRenderDistanceMixin {

    @Inject(method = "shouldRender(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void topdownview$shouldRenderFromPlayer(double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (Object) this == mc.player) {
            return;
        }
        Entity self = (Entity) (Object) this;
        double dx = self.getX() - mc.player.getX();
        double dy = self.getY() - mc.player.getY();
        double dz = self.getZ() - mc.player.getZ();
        cir.setReturnValue(self.shouldRenderAtSqrDistance(dx * dx + dy * dy + dz * dz));
    }
}
