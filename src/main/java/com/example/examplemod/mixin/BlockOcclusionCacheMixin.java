package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.example.examplemod.culling.TopDownCuller;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium用ブロック遮蔽キャッシュMixin
 * カリング対象ブロックとの隣接面を強制的に描画する
 */
@SuppressWarnings("all")
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache.class, remap = false)
public class BlockOcclusionCacheMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void onShouldDrawSideHead(
            BlockState selfState,
            BlockGetter view,
            BlockPos pos,
            Direction face,
            CallbackInfoReturnable<Boolean> cir) {

        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        // 隣接ブロックの位置を取得
        BlockPos neighborPos = pos.relative(face);

        // 隣接ブロックがカリング対象なら、この面を強制的に描画
        if (CULLER.isBlockCulled(neighborPos, view)) {
            cir.setReturnValue(true);
        }
    }
}
