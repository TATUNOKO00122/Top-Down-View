package com.topdownview.placement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.topdownview.Config;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * ブロック配置プレビューの半透明ゴーストブロック描画。
 *
 * TranslucentBlockRenderer と同様のアプローチで
 * PoseStack + BlockRenderDispatcher + アルファ上書きVertexConsumerを使用。
 * AFTER_TRANSLUCENT_BLOCKS ステージで描画する。
 */
public final class PlacementRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RandomSource RANDOM = RandomSource.create();

    private PlacementRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS で呼び出す
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        List<PlacementPreviewManager.PlacementEntry> entries =
                PlacementPreviewManager.getInstance().getEntries();
        if (entries.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        float alpha = (float) Config.getPlacementTransparency();
        // アルファ上書きVertexConsumerラッパー
        VertexConsumer baseConsumer = bufferSource.getBuffer(RenderType.translucent());
        AlphaOverrideVertexConsumer alphaConsumer = new AlphaOverrideVertexConsumer(baseConsumer, alpha);

        // 実ワールドをプロキシとして使用（光量・バイオーム取得用）
        GhostBlockGetter ghostLevel = new GhostBlockGetter(mc.level, entries);

        for (PlacementPreviewManager.PlacementEntry entry : entries) {
            renderGhostBlock(entry.pos(), entry.state(), poseStack, blockRenderer,
                    alphaConsumer, ghostLevel, cameraPos);
        }

        bufferSource.endBatch(RenderType.translucent());
    }

    private static void renderGhostBlock(
            BlockPos pos,
            BlockState state,
            PoseStack poseStack,
            BlockRenderDispatcher blockRenderer,
            AlphaOverrideVertexConsumer alphaConsumer,
            GhostBlockGetter ghostLevel,
            Vec3 cameraPos) {

        if (state.isAir()) {
            return;
        }

        BakedModel model = blockRenderer.getBlockModel(state);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(
                pos.getX() - cameraPos.x,
                pos.getY() - cameraPos.y,
                pos.getZ() - cameraPos.z);

        long seed = state.getSeed(pos);
        RANDOM.setSeed(seed);

        try {
            blockRenderer.getModelRenderer().tesselateBlock(
                    ghostLevel,
                    model,
                    state,
                    pos,
                    poseStack,
                    alphaConsumer,
                    true,
                    RANDOM,
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    RenderType.translucent());
        } catch (Throwable t) {
            // 例外時は静かに無視（クラッシュ防止）
            LOGGER.debug("[TopDownView] Ghost block render failed, skipping", t);
        } finally {
            poseStack.popPose();
        }
    }

    // ==================== 内部クラス ====================

    /**
     * アルファ値を強制上書きするVertexConsumerラッパー
     */
    private static class AlphaOverrideVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        private final int alphaInt;

        AlphaOverrideVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            this.alphaInt = (int) (this.alpha * 255);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return delegate.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return delegate.color(r, g, b, alphaInt);
        }

        @Override
        public VertexConsumer color(float r, float g, float b, float a) {
            return delegate.color(r, g, b, alpha);
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

    /**
     * ゴーストブロック描画用のBlockAndTintGetterプロキシ。
     * 配置予定位置はAIR扱いにして全面描画、隣接ブロックは実ワールドを返す。
     */
    private static class GhostBlockGetter implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final java.util.Set<BlockPos> ghostPositions;
        private final List<PlacementPreviewManager.PlacementEntry> entries;

        GhostBlockGetter(BlockAndTintGetter delegate,
                         List<PlacementPreviewManager.PlacementEntry> entries) {
            this.delegate = delegate;
            this.entries = entries;
            this.ghostPositions = new java.util.HashSet<>();
            for (PlacementPreviewManager.PlacementEntry e : entries) {
                ghostPositions.add(e.pos());
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            // ゴースト配置予定位置はAIRとして扱う（面カリングを防ぎ全面描画）
            if (ghostPositions.contains(pos)) {
                // 自身の場合は実際の状態で判定（隣接判定用）
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            return delegate.getBlockState(pos);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return delegate.getFluidState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
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
    }
}
