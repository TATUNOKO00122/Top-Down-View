package com.topdownview.culling;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.state.ModState;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.util.DirectionUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.embeddium.api.BlockRendererRegistry;

import java.util.List;

/**
 * カリングで消えるブロックの断面を、そのブロック自身のセクションで描画する Embeddium カスタムレンダラ。
 *
 * <p>隣接ブロック側で面を強制すると「消える側」と別セクションになり、Embeddium の遅延アップロードで
 * 別フレームになって穴が残る。ここでは消えるブロック自身の、非カリングの隣に接する面だけを頂点順反転で
 * 出力する。除去と断面が同一セクションでアトミックにコミットされるため、同期再構築が不要になる。
 */
public final class CullBoundaryRenderer implements BlockRendererRegistry.Renderer {

    private static final CullBoundaryRenderer INSTANCE = new CullBoundaryRenderer();
    private static final int WHITE = 0xFFFFFFFF;

    // メッシュ生成は複数ワーカースレッドで並列実行されるため、位置計算はスレッド毎に持つ。
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private final TopDownCuller culler = TopDownCuller.getInstance();

    private CullBoundaryRenderer() {}

    /** Embeddium が存在する場合のみ呼ぶ。 */
    public static void register() {
        BlockRendererRegistry.instance().registerRenderPopulator((resultList, ctx) -> {
            if (!ModState.STATUS.isEnabled()) {
                return;
            }
            BlockPos pos = ctx.pos();
            if (pos != null && INSTANCE.culler.isBlockCulled(pos, ctx.world())) {
                resultList.add(INSTANCE);
            }
        });
    }

    @Override
    public BlockRendererRegistry.RenderResult renderBlock(BlockRenderContext ctx, RandomSource random, VertexConsumer consumer) {
        BlockAndTintGetter level = ctx.localSlice();
        BlockPos pos = ctx.pos();
        BlockState state = ctx.state();
        BlockPos.MutableBlockPos neighborPos = NEIGHBOR_POS.get();

        for (Direction face : DirectionUtil.ALL_DIRECTIONS) {
            neighborPos.set(
                    pos.getX() + face.getStepX(),
                    pos.getY() + face.getStepY(),
                    pos.getZ() + face.getStepZ());

            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.isAir() || culler.isBlockCulled(neighborPos, ctx.world())) {
                continue;
            }

            random.setSeed(ctx.seed());
            List<BakedQuad> quads = ctx.model().getQuads(state, face, random, ctx.modelData(), ctx.renderLayer());
            if (quads.isEmpty()) {
                continue;
            }

            int blockLight = level.getBrightness(LightLayer.BLOCK, pos) << 4;
            int skyLight = level.getBrightness(LightLayer.SKY, pos) << 4;

            // 断面は穴の内側から見えるため、頂点順を反転して表裏を返す。
            for (int q = 0, n = quads.size(); q < n; q++) {
                BakedQuadView quad = (BakedQuadView) quads.get(q);
                for (int i = 3; i >= 0; i--) {
                    consumer.vertex(quad.getX(i), quad.getY(i), quad.getZ(i));
                    emitColor(consumer, quad.getColor(i));
                    consumer.uv(quad.getTexU(i), quad.getTexV(i));
                    consumer.uv2(blockLight, skyLight);
                    consumer.normal(0.0F, 1.0F, 0.0F);
                    consumer.endVertex();
                }
            }
        }

        return BlockRendererRegistry.RenderResult.OVERRIDE;
    }

    private static void emitColor(VertexConsumer consumer, int color) {
        if (color == 0) {
            color = WHITE;
        }
        consumer.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF);
    }
}
