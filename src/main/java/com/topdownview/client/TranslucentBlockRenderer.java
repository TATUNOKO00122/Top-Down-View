package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.util.PerfMonitor;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * カリング境界フェード描画 & マイニングモード動的描画
 * RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKSで描画
 */
public final class TranslucentBlockRenderer {

    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * 前フレームのフェードα。位置ごとに目標値へ指数減衰させ、プレイヤー移動時の
     * ブロック単位のα段差(点滅)を無くす。描画スレッドからのみ触る。
     */
    private static final Long2FloatOpenHashMap SMOOTHED_ALPHAS = new Long2FloatOpenHashMap();
    private static long lastFrameNanos;

    private TranslucentBlockRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void renderFadeBlocks(RenderLevelStageEvent event) {
        long tRender = System.nanoTime();
        renderFadeBlocksInternal(event);
        PerfMonitor.FADE_RENDER.add(System.nanoTime() - tRender);
    }

    private static void renderFadeBlocksInternal(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // フェード有効/無効の判定は getFadeBlocks() 側で行う。
        Long2FloatMap fadeBlocks = TopDownCuller.getInstance().getFadeBlocks(mc.level);
        PerfMonitor.recordFadeBlocks(fadeBlocks.size());

        if (fadeBlocks.isEmpty()) {
            SMOOTHED_ALPHAS.clear();
            lastFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min((now - lastFrameNanos) / 1.0E9f, 0.1f);
        lastFrameNanos = now;
        float halfLife = (float) Config.getFadeSmoothingHalfLife();

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double pX = mc.player.getX();
        double pY = mc.player.getY();
        double pZ = mc.player.getZ();
        double maxDistSq = 24.0 * 24.0; // 24ブロックより遠いフェードブロックは描画をスキップ

        // パフォーマンス最適化: ラッパーオブジェクトを再利用
        VertexConsumer baseConsumer = bufferSource.getBuffer(RenderType.translucent());
        ReusableAlphaVertexConsumer alphaConsumer = new ReusableAlphaVertexConsumer(baseConsumer);
        FadeBlockGetter fadeLevel = new FadeBlockGetter(mc.level, fadeBlocks);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (Long2FloatMap.Entry entry : fadeBlocks.long2FloatEntrySet()) {
            long posLong = entry.getLongKey();
            int bx = BlockPos.getX(posLong);
            int by = BlockPos.getY(posLong);
            int bz = BlockPos.getZ(posLong);

            // 距離カリング：遠すぎるフェードブロックの描画・アロケーションをスキップ
            double dx = bx + 0.5 - pX;
            double dy = by + 0.5 - pY;
            double dz = bz + 0.5 - pZ;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                continue;
            }

            // αを前フレームから時間平滑化(フレームレート非依存)。チラツキ低減用。
            // 透明化方向(α減少)は遅延なく即時反映し、不透明方向(α増加)だけ滑らかにする。
            float target = entry.getFloatValue();
            float previous = SMOOTHED_ALPHAS.getOrDefault(posLong, target);
            float alpha = target < previous ? target : expDecay(previous, target, halfLife, dt);
            SMOOTHED_ALPHAS.put(posLong, alpha);

            mutablePos.set(bx, by, bz);
            renderFadeBlock(mc.level, fadeLevel, mutablePos, poseStack, blockRenderer, alphaConsumer, alpha, cameraPos);
        }

        // フェード集合から外れたブロックの平滑化状態を破棄(無制限な増加を防ぐ)
        if (SMOOTHED_ALPHAS.size() > fadeBlocks.size()) {
            var iterator = SMOOTHED_ALPHAS.long2FloatEntrySet().iterator();
            while (iterator.hasNext()) {
                if (!fadeBlocks.containsKey(iterator.next().getLongKey())) {
                    iterator.remove();
                }
            }
        }

        // 復帰ゴースト(alpha=1)は再構築済みメッシュのブロックと同一平面に描かれることがある。
        // 深度をわずかに奥へずらし、コプレーナな不透明メッシュに負けさせる。ずらさないと
        // 半透明ゴーストが不透明ブロックに重なってブレンドされ、明るく光って見える。
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(1.0f, 1.0f);
        try {
            bufferSource.endBatch(RenderType.translucent());
        } finally {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        }
    }

    /** フレームレート非依存の指数減衰。halfLife<=0 なら即時追従。 */
    private static float expDecay(float current, float target, float halfLifeSeconds, float dtSeconds) {
        if (halfLifeSeconds <= 0.0f || dtSeconds <= 0.0f) {
            return target;
        }
        float factor = 1.0f - (float) Math.pow(2.0, -dtSeconds / halfLifeSeconds);
        return current + (target - current) * factor;
    }

    private static void renderFadeBlock(
            BlockAndTintGetter level,
            FadeBlockGetter fadeLevel,
            BlockPos pos,
            PoseStack poseStack,
            BlockRenderDispatcher blockRenderer,
            ReusableAlphaVertexConsumer alphaConsumer,
            float alpha,
            Vec3 cameraPos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        BakedModel model = blockRenderer.getBlockModel(state);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        alphaConsumer.setAlpha(alpha);

        long seed = state.getSeed(pos);
        RANDOM.setSeed(seed);

        ModelData modelData = ModelData.EMPTY;
        if (state.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                modelData = be.getModelData();
            }
        }

        // 面カリングはフェード集合の所属だけで判定する(α値には依存しない)。
        // 実ブロックで判定すると、隣が不透明ブロックの面まで消えて露出面(多くの場合は上面)しか
        // 描かれず、Blockが一面だけの板に見える。フェード同士は面を消して二重合成を防ぐ。
        blockRenderer.getModelRenderer().tesselateBlock(
                fadeLevel,
                model,
                state,
                pos,
                poseStack,
                alphaConsumer,
                true,
                RANDOM,
                seed,
                OverlayTexture.NO_OVERLAY,
                modelData,
                RenderType.translucent());

        poseStack.popPose();
    }

    /**
     * 頂点カラーのアルファ値を強制的に上書きするVertexConsumerラッパー
     * パフォーマンス最適化: alpha値を更新して再利用可能
     */
    private static class ReusableAlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private float alpha;

        ReusableAlphaVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
            this.alpha = 1.0f;
        }

        void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return delegate.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return delegate.color(r, g, b, (int) (this.alpha * 255));
        }

        @Override
        public VertexConsumer color(float r, float g, float b, float a) {
            return delegate.color(r, g, b, this.alpha);
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return delegate.uv(u, v);
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return delegate.overlayCoords(u, v);
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return delegate.uv2(u, v);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return delegate.normal(x, y, z);
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

    /**
     * フェードブロック描画用のBlockAndTintGetterプロキシ。
     * フェード集合内のブロックは実状態を返し(面カリング対象)、集合外のブロックは空気として扱う。
     * こうすることで、フェードブロックは不透明ブロックと接する面も描画され、Blockの形を保つ。
     * 判定は集合の所属のみで行うため、α値が変化しても面の描画は反転しない。
     */
    private static class FadeBlockGetter implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final Long2FloatMap fadeBlocks;

        FadeBlockGetter(BlockAndTintGetter delegate, Long2FloatMap fadeBlocks) {
            this.delegate = delegate;
            this.fadeBlocks = fadeBlocks;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return delegate.getBlockTint(pos, resolver);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (fadeBlocks.containsKey(pos.asLong())) {
                return delegate.getBlockState(pos);
            }
            return AIR_STATE;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }
}
