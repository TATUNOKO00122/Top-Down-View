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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Set;

/**
 * 遮蔽トラップドアの半透明描画
 * RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKSで描画
 */
public final class TranslucentBlockRenderer {

    private static final RandomSource RANDOM = RandomSource.create();

    private TranslucentBlockRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void renderTranslucentTrapdoors(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        if (!Config.trapdoorTranslucencyEnabled) {
            return;
        }

        TopDownCuller culler = TopDownCuller.getInstance();
        Set<BlockPos> translucentPositions = culler.getTranslucentTrapdoors(mc.level);

        if (translucentPositions.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        float alpha = (float) Config.trapdoorTransparency;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        for (BlockPos pos : translucentPositions) {
            renderTranslucentBlock(mc.level, pos, poseStack, bufferSource, blockRenderer, alpha, cameraPos);
        }

        bufferSource.endBatch(RenderType.translucent());
    }

    private static void renderTranslucentBlock(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockRenderDispatcher blockRenderer,
            float alpha,
            Vec3 cameraPos
    ) {
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
        
        int packedLight = getLightColor(level, pos);

        blockRenderer.getModelRenderer().renderModel(
            poseStack.last(),
            alphaConsumer,
            state,
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    private static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        return (skyLight << 20) | (blockLight << 4);
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
            return delegate.color(r, g, b, (int)(this.alpha * 255));
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
