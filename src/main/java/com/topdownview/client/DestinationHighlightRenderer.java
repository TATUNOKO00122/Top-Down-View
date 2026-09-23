package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class DestinationHighlightRenderer {

    // ブロックテクスチャと同じ16x16のピクセル格子に合わせてドット調に描画する
    private static final float PIXEL_SIZE = 1.0f / 16.0f;
    private static final float HEIGHT_OFFSET = 0.02f;

    private static final float CENTER_INITIAL_RADIUS = 0.18f;
    private static final float OUTER_INITIAL_RADIUS = 0.08f;
    private static final float OUTER_MAX_RADIUS = 0.6f;
    private static final float RING_WIDTH = 0.04f;
    private static final float CENTER_SHRINK_DELAY = 0.3f;

    private static final float COLOR_R = 1.0f;
    private static final float COLOR_G = 0.84f;
    private static final float COLOR_B = 0.2f;

    private static final float GLOW_R = 0.9f;
    private static final float GLOW_G = 0.65f;
    private static final float GLOW_B = 0.15f;

    private DestinationHighlightRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!Config.isClickToMoveEnabled()) return;
        if (!Config.isDestinationHighlightEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!ModState.DESTINATION_HIGHLIGHT.isAnimating()) return;

        Vec3 targetPos = ModState.CLICK_TO_MOVE.getTargetPosition();
        if (targetPos == null) return;

        renderHighlight(event, targetPos, mc);
    }

    private static void renderHighlight(RenderLevelStageEvent event, Vec3 targetPos, Minecraft mc) {
        if (targetPos == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        float progress = ModState.DESTINATION_HIGHLIGHT.getProgress();
        float alpha = ModState.DESTINATION_HIGHLIGHT.getAlpha();

        if (progress >= 1.0f || alpha <= 0.0f) return;

        // ドットをブロックのピクセル格子に固定するため、ワールド整数基準の格子へ原点を合わせる
        double snappedX = Math.floor(targetPos.x / PIXEL_SIZE) * PIXEL_SIZE;
        double snappedZ = Math.floor(targetPos.z / PIXEL_SIZE) * PIXEL_SIZE;
        float fracX = (float) (targetPos.x - snappedX);
        float fracZ = (float) (targetPos.z - snappedZ);

        double x = snappedX - cameraPos.x;
        double y = targetPos.y - cameraPos.y + HEIGHT_OFFSET;
        double z = snappedZ - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        float centerProgress = Math.max(0.0f, (progress - CENTER_SHRINK_DELAY) / (1.0f - CENTER_SHRINK_DELAY));
        float centerRadius = CENTER_INITIAL_RADIUS * (1.0f - centerProgress);
        if (centerRadius > 0.005f) {
            renderPixelCircle(buffer, matrix, centerRadius, 0.0f, fracX, fracZ,
                    alpha, COLOR_R, COLOR_G, COLOR_B);
        }

        float outerRadius = OUTER_INITIAL_RADIUS + (OUTER_MAX_RADIUS - OUTER_INITIAL_RADIUS) * progress;
        renderPixelCircle(buffer, matrix, outerRadius, outerRadius - RING_WIDTH, fracX, fracZ,
                alpha * 0.9f, COLOR_R, COLOR_G, COLOR_B);
        renderPixelCircle(buffer, matrix, outerRadius + RING_WIDTH * 2, outerRadius, fracX, fracZ,
                alpha * 0.3f, GLOW_R, GLOW_G, GLOW_B);

        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.debugQuads());
    }

    /**
     * 半径 innerR..outerR の範囲を、ワールドの1/16格子に沿った四角ドットの集合で描画する。
     * fracX/fracZ は中心の格子内オフセット（0..PIXEL_SIZE）。
     */
    private static void renderPixelCircle(VertexConsumer buffer, Matrix4f matrix,
                                          float outerR, float innerR,
                                          float fracX, float fracZ,
                                          float alpha, float r, float g, float b) {
        int cellCount = Mth.ceil(outerR / PIXEL_SIZE) + 1;
        float outerSq = outerR * outerR;
        float innerSq = innerR * innerR;

        for (int i = -cellCount; i <= cellCount; i++) {
            float cellCenterX = (i + 0.5f) * PIXEL_SIZE - fracX;
            float x0 = i * PIXEL_SIZE - fracX;
            float x1 = x0 + PIXEL_SIZE;

            for (int j = -cellCount; j <= cellCount; j++) {
                float cellCenterZ = (j + 0.5f) * PIXEL_SIZE - fracZ;
                float distSq = cellCenterX * cellCenterX + cellCenterZ * cellCenterZ;
                if (distSq > outerSq || distSq < innerSq) continue;

                float z0 = j * PIXEL_SIZE - fracZ;
                float z1 = z0 + PIXEL_SIZE;

                buffer.vertex(matrix, x0, 0, z0).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x0, 0, z1).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x1, 0, z1).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x1, 0, z0).color(r, g, b, alpha).endVertex();
            }
        }
    }
}