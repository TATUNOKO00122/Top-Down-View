package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.culling.TopDownCuller;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * カリング境界フェード描画 & マイニングモード動的描画
 * RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKSで描画
 */
public final class TranslucentBlockRenderer {

    private static final RandomSource RANDOM = RandomSource.create();

    private TranslucentBlockRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void renderFadeBlocks(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        TopDownCuller culler = TopDownCuller.getInstance();

        if (!Config.isFadeEnabled()) {
            return;
        }

        Map<BlockPos, Float> fadeBlocks = culler.getFadeBlocks(mc.level);

        if (fadeBlocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // パフォーマンス最適化: ラッパーオブジェクトを再利用
        VertexConsumer baseConsumer = bufferSource.getBuffer(RenderType.translucent());
        ReusableAlphaVertexConsumer alphaConsumer = new ReusableAlphaVertexConsumer(baseConsumer);
        FadeBlockGetter fadeLevel = new FadeBlockGetter(mc.level, fadeBlocks);

        for (Map.Entry<BlockPos, Float> entry : fadeBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = entry.getValue();
            renderFadeBlock(mc.level, pos, poseStack, blockRenderer, alphaConsumer, fadeLevel, alpha, cameraPos);
        }

        bufferSource.endBatch(RenderType.translucent());
    }

    private static void renderFadeBlock(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack poseStack,
            BlockRenderDispatcher blockRenderer,
            ReusableAlphaVertexConsumer alphaConsumer,
            FadeBlockGetter fadeLevel,
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
        fadeLevel.setRenderContext(pos, alpha);

        long seed = state.getSeed(pos);
        RANDOM.setSeed(seed);

        net.minecraftforge.client.model.data.ModelData modelData = net.minecraftforge.client.model.data.ModelData.EMPTY;
        if (state.hasBlockEntity()) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                modelData = be.getModelData();
            }
        }

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

    private static final float ALPHA_DIFF_THRESHOLD = 0.05f;
    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

    /**
     * フェードブロック描画用のBlockAndTintGetterプロキシ。
     * 
     * 状態フル設計: パフォーマンス最適化のためインスタンスを再利用。
     * renderFadeBlock()呼び出し前にsetRenderContext()で現在のブロック情報を設定すること。
     * 
     * 判定ロジック:
     * - 非フェードブロック → AIR（面を描画）
     * - 自身のブロック → 元の状態（面を描画しない、checkSidesでカリング）
     * - 透明度差 < 閾値 → 元の状態（面を描画しない）
     * - 透明度差 >= 閾値 → AIR（面を描画）
     */
    private static class FadeBlockGetter implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final Map<BlockPos, Float> fadeBlocks;
        private BlockPos renderPos;
        private float renderAlpha;

        FadeBlockGetter(BlockAndTintGetter delegate, Map<BlockPos, Float> fadeBlocks) {
            this.delegate = delegate;
            this.fadeBlocks = fadeBlocks;
        }

        void setRenderContext(BlockPos pos, float alpha) {
            this.renderPos = pos;
            this.renderAlpha = alpha;
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
            Float neighborAlpha = fadeBlocks.get(pos);
            if (neighborAlpha == null) {
                return AIR_STATE;
            }
            if (pos.equals(renderPos)) {
                return delegate.getBlockState(pos);
            }
            float diff = Math.abs(renderAlpha - neighborAlpha);
            if (diff < ALPHA_DIFF_THRESHOLD) {
                return delegate.getBlockState(pos);
            }
            return AIR_STATE;
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
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
