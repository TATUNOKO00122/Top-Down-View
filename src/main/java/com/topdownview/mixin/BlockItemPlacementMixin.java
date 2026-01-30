package com.topdownview.mixin;

import com.topdownview.placement.PlacementHandler;
import com.topdownview.server.ServerPlacementRotationHandler;
import com.topdownview.state.ModState;
import com.topdownview.Config;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
 * リダイレクトし、現在の指定向きで BlockState の DirectionProperty を差し替える。
 *
 * 向きの取得元:
 *   - 専用サーバー: ServerPlacementRotationHandler.getFacing(player)
 *   - シングルプレイ（統合サーバー）: ModState.PLACEMENT_ROTATION（同一JVMなので直接参照）
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

        // 手動の配置方向指定（PlacementRotation）が有効な場合はそれを優先
        Direction facing = resolveFacing(context.getPlayer());
        if (facing != null) {
            return PlacementHandler.applyFacing(original, facing);
        }

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

    /**
     * 実行環境に応じて指定向きを取得する。
     */
    @Nullable
    private static Direction resolveFacing(@Nullable Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = serverPlayer.getServer();
            if (server != null && server.isDedicatedServer()) {
                return ServerPlacementRotationHandler.getFacing(serverPlayer);
            }
        }
        // シングルプレイ（統合サーバー）またはクライアント側: ModState を直接参照
        if (ModState.PLACEMENT_ROTATION.hasOverride()) {
            return ModState.PLACEMENT_ROTATION.getCurrentFacing();
        }
        return null;
    }
}
