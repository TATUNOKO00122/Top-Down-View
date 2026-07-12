package com.topdownview.mixin;

import com.topdownview.placement.PlacementHandler;
import com.topdownview.state.ModState;
import com.topdownview.Config;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

/**
 * ブロック配置方向差し替え Mixin（クライアント・サーバー両側で動作）
 *
 * BlockItem#getPlacementState 内で呼ばれる Block#getStateForPlacement を
 * リダイレクトし、クリック位置に基づく自動配置で BlockState の DirectionProperty を差し替える。
 *
 * StandingAndWallBlockItem は getPlacementState を完全オーバーライドして
 * super を呼ばないため、両クラスをターゲットに指定する。
 */
@Mixin(value = {BlockItem.class, StandingAndWallBlockItem.class}, priority = 1000)
public abstract class BlockItemPlacementMixin {

    @Redirect(method = "getPlacementState",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/Block;getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"),
              require = 0)
    @Nullable
    private BlockState topdownview$onGetStateForPlacement(Block block, BlockPlaceContext context) {
        BlockState original = block.getStateForPlacement(context);
        if (original == null) return null;

        // クリック位置ベースの自動配置が有効な場合
        if (Config.isClickPositionPlacementEnabled() && ModState.STATUS.isEnabled()) {
            // 看板や松明など、すでにバニラでクリック面に沿って配向されているブロックは上書きしない
            if (!PlacementHandler.isAlreadyAlignedToFace(original, context.getClickedFace())) {
                Direction calculated = PlacementHandler.calculateClickPositionFacing(context);
                if (calculated != null) {
                    return PlacementHandler.applyFacing(original, calculated);
                }
            }
        }

        return original;
    }
}
