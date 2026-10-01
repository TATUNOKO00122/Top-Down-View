package com.topdownview.mixin;

import com.topdownview.culling.CullingManager;
import com.topdownview.culling.ReversedQuad;
import com.topdownview.state.ModState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Embeddium(Sodium)用 BlockRenderer Mixin。
 *
 * <p>カリングで消えるブロックは、そのブロック自身のセクションに「非カリングの隣に接する面」だけを
 * 断面として出す必要がある(除去と断面を同一セクションでアトミックに確定させるため)。
 *
 * <p>以前は独自カスタムレンダラで生の頂点を手書きしていたため、バイオームtint(葉が白くなる)や
 * アンビエントオクルージョン/面方向シェード(暗くなる)が抜け、隣の面と二重になることがあった。
 * ここでは {@code renderModel} が既に計算した colorizer/lighter をそのまま使えるよう、面ループの
 * 2つの入力を差し替える:
 * <ul>
 *   <li>{@code getGeometry} … カリング済みブロックは境界面のみ、頂点順を反転した quad を返す
 *       ({@link ReversedQuad})。それ以外は従来どおり。</li>
 *   <li>{@code isFaceVisible} … カリング済みブロックは true(境界面だけを getGeometry が返す)。
 *       それ以外は従来のオクルージョン判定。</li>
 * </ul>
 * これで色・tint・AO・ライトマップ・圧縮頂点エンコードがすべて正規パイプラインのままになる。
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    @Shadow @Final private BlockOcclusionCache occlusionCache;
    @Shadow @Final private RandomSource random;

    // 面ごと(最大7回)に isCulled を呼ぶのを避けるため、現在ブロックの判定結果を1つだけ覚える。
    // BlockRenderer はメッシュスレッドごとに1個なのでこのフィールドはスレッド安全。
    @Unique private long cullMemoPos = Long.MIN_VALUE;
    @Unique private boolean cullMemoValue = false;

    @Redirect(method = "renderModel",
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;getGeometry(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;Lnet/minecraft/core/Direction;)Ljava/util/List;"))
    private List<BakedQuad> onGetGeometry(BlockRenderer self, BlockRenderContext ctx, Direction face) {
        RandomSource random = this.random;
        random.setSeed(ctx.seed());

        if (!isCulled(ctx)) {
            return ctx.model().getQuads(ctx.state(), face, random, ctx.modelData(), ctx.renderLayer());
        }
        // カリング済みブロックの非cullface幾何(草・花などの十字モデル)は除去する。
        if (face == null) {
            return Collections.emptyList();
        }

        BlockPos pos = ctx.pos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        neighborPos.set(pos.getX() + face.getStepX(), pos.getY() + face.getStepY(), pos.getZ() + face.getStepZ());

        BlockState neighbor = ctx.localSlice().getBlockState(neighborPos);
        if (neighbor.isAir() || CullingManager.isBlockCulled(neighborPos, ctx.world())) {
            return Collections.emptyList();
        }
        // 隣ブロックが自分側の面を描く場合は、二重描画になるのでこちらは出さない。
        if (this.occlusionCache.shouldDrawSide(neighbor, ctx.localSlice(), neighborPos, face.getOpposite())) {
            return Collections.emptyList();
        }

        List<BakedQuad> quads = ctx.model().getQuads(ctx.state(), face, random, ctx.modelData(), ctx.renderLayer());
        if (quads.isEmpty()) {
            return quads;
        }

        List<BakedQuad> reversed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            reversed.add(ReversedQuad.reverse(quad));
        }
        return reversed;
    }

    @Redirect(method = "renderModel",
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;isFaceVisible(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;Lnet/minecraft/core/Direction;)Z"))
    private boolean onIsFaceVisible(BlockRenderer self, BlockRenderContext ctx, Direction face) {
        if (isCulled(ctx)) {
            // getGeometry が境界面だけを返しているのでここは常に描画でよい。
            return true;
        }
        return this.occlusionCache.shouldDrawSide(ctx.state(), ctx.localSlice(), ctx.pos(), face);
    }

    private boolean isCulled(BlockRenderContext ctx) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ctx.pos() == null) {
            return false;
        }
        long key = ctx.pos().asLong();
        if (key == this.cullMemoPos) {
            return this.cullMemoValue;
        }
        boolean value = CullingManager.isBlockCulled(ctx.pos(), ctx.world());
        this.cullMemoPos = key;
        this.cullMemoValue = value;
        return value;
    }
}
