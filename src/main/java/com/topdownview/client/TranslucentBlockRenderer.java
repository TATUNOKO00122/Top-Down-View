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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Map;

/**
 * カリング境界フェード描画
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

        if (!Config.fadeEnabled) {
            return;
        }

        TopDownCuller culler = TopDownCuller.getInstance();
        Map<BlockPos, Float> fadeBlocks = culler.getFadeBlocks(mc.level);

        if (fadeBlocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        for (Map.Entry<BlockPos, Float> entry : fadeBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = entry.getValue();
            renderFadeBlock(mc.level, pos, poseStack, bufferSource, blockRenderer, alpha, cameraPos);
        }

        bufferSource.endBatch(RenderType.translucent());
    }

    private static void renderFadeBlock(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockRenderDispatcher blockRenderer,
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

        VertexConsumer baseConsumer = bufferSource.getBuffer(RenderType.translucent());
        VertexConsumer alphaConsumer = new AlphaOverrideVertexConsumer(baseConsumer, alpha);

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
                level,
                model,
                state,
                pos,
                poseStack,
                alphaConsumer,
                true, // checkSides
                RANDOM,
                seed,
                OverlayTexture.NO_OVERLAY,
                modelData,
                RenderType.translucent());

        poseStack.popPose();
    }

    /**
     * 頂点カラーのアルファ値を強制的に上書きするVertexConsumerラッパー
     */
    private static class AlphaOverrideVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        AlphaOverrideVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
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
}
