package com.topdownview.mixin;

import com.topdownview.server.ServerPickupHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手動取得モードのプレイヤーに対して、バニラの自動アイテム取得を停止する。
 * 実際の取得は {@link ServerPickupHandler} のクリック要求処理が行う。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void topdownview$suppressAutoPickup(Player player, CallbackInfo ci) {
        if (ServerPickupHandler.shouldSuppressAutoPickup(player)) {
            ci.cancel();
        }
    }
}
