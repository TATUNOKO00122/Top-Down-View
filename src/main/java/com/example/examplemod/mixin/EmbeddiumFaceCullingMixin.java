package com.example.examplemod.mixin;

import com.example.examplemod.api.cullers.BlockCullingLogic;
import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium BlockOcclusionCache Mixin
 * カリングされたブロックの隣接ブロックの面を強制描画させる
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public class EmbeddiumFaceCullingMixin {

    /**
     * shouldDrawSide は「このブロックのこの面を描画すべきか？」を判定する
     * 引数:
     * - selfState: 自分（描画しようとしているブロック）の状態
     * - view: ワールド
     * - pos: 自分の位置
     * - facing: 描画しようとしている面の方向
     * 
     * 隣接ブロック（pos + facing の位置）がカリング対象の場合、
     * この面を描画すべき（true）を返す
     */
    @Inject(method = "shouldDrawSide", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void onShouldDrawSide(BlockState selfState, BlockGetter view, BlockPos pos,
            Direction facing, CallbackInfoReturnable<Boolean> cir) {

        // MODが無効なら何もしない
        if (!ClientForgeEvents.isTopDownView || !ClientForgeEvents.isBlockCullingEnabled) {
            return;
        }

        // 既にtrueなら何もしない（描画される）
        if (cir.getReturnValue()) {
            return;
        }

        // 隣接ブロックの位置
        BlockPos neighborPos = pos.relative(facing);

        // 隣接ブロックがカリング対象なら、この面を描画させる
        if (BlockCullingLogic.shouldCull(neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}
