package com.topdownview.placement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.topdownview.Config;
import com.topdownview.client.AlphaVertexConsumer;
import com.topdownview.client.DelegatingBlockGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        AlphaVertexConsumer alphaConsumer = new AlphaVertexConsumer(baseConsumer);
        alphaConsumer.setAlpha(alpha);

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
            AlphaVertexConsumer alphaConsumer,
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
     * ゴーストブロック描画用のBlockAndTintGetterプロキシ。
     * 配置予定位置はAIR扱いにして全面描画、隣接ブロックは実ワールドを返す。
     */
    private static final class GhostBlockGetter extends DelegatingBlockGetter {

        private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

        private final Set<BlockPos> ghostPositions;

        GhostBlockGetter(BlockAndTintGetter delegate, List<PlacementPreviewManager.PlacementEntry> entries) {
            super(delegate);
            this.ghostPositions = new HashSet<>();
            for (PlacementPreviewManager.PlacementEntry e : entries) {
                ghostPositions.add(e.pos());
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            // ゴースト配置予定位置はAIRとして扱う（面カリングを防ぎ全面描画）
            if (ghostPositions.contains(pos)) {
                return AIR_STATE;
            }
            return delegate.getBlockState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }
    }
}
