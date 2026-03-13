package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class DestinationHighlightRenderer {

    private static final int CIRCLE_SEGMENTS = 48;
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

        double x = targetPos.x - cameraPos.x;
        double y = targetPos.y - cameraPos.y + HEIGHT_OFFSET;
        double z = targetPos.z - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        float centerProgress = Math.max(0.0f, (progress - CENTER_SHRINK_DELAY) / (1.0f - CENTER_SHRINK_DELAY));
        float centerRadius = CENTER_INITIAL_RADIUS * (1.0f - centerProgress);
        if (centerRadius > 0.005f) {
            renderFilledCircle(buffer, matrix, centerRadius, alpha);
        }

        float outerRadius = OUTER_INITIAL_RADIUS + (OUTER_MAX_RADIUS - OUTER_INITIAL_RADIUS) * progress;
        renderRing(buffer, matrix, outerRadius - RING_WIDTH, outerRadius, alpha * 0.9f, false);
        renderRing(buffer, matrix, outerRadius, outerRadius + RING_WIDTH * 2, alpha * 0.3f, true);

        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.debugQuads());
    }

    private static void renderFilledCircle(VertexConsumer buffer, Matrix4f matrix, float radius, float alpha) {
        float a = alpha;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float angle1 = (float) (2 * Math.PI * i / CIRCLE_SEGMENTS);
            float angle2 = (float) (2 * Math.PI * (i + 1) / CIRCLE_SEGMENTS);

            float x1 = radius * (float) Math.cos(angle1);
            float z1 = radius * (float) Math.sin(angle1);
            float x2 = radius * (float) Math.cos(angle2);
            float z2 = radius * (float) Math.sin(angle2);

            buffer.vertex(matrix, 0, 0, 0).color(COLOR_R, COLOR_G, COLOR_B, a).endVertex();
            buffer.vertex(matrix, x1, 0, z1).color(COLOR_R, COLOR_G, COLOR_B, a).endVertex();
            buffer.vertex(matrix, x2, 0, z2).color(COLOR_R, COLOR_G, COLOR_B, a).endVertex();
            buffer.vertex(matrix, 0, 0, 0).color(COLOR_R, COLOR_G, COLOR_B, a).endVertex();
        }
    }

    private static void renderRing(VertexConsumer buffer, Matrix4f matrix, float innerR, float outerR, float alpha, boolean isGlow) {
        float r = isGlow ? GLOW_R : COLOR_R;
        float g = isGlow ? GLOW_G : COLOR_G;
        float b = isGlow ? GLOW_B : COLOR_B;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float angle1 = (float) (2 * Math.PI * i / CIRCLE_SEGMENTS);
            float angle2 = (float) (2 * Math.PI * (i + 1) / CIRCLE_SEGMENTS);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float x1In = innerR * cos1;
            float z1In = innerR * sin1;
            float x2In = innerR * cos2;
            float z2In = innerR * sin2;

            float x1Out = outerR * cos1;
            float z1Out = outerR * sin1;
            float x2Out = outerR * cos2;
            float z2Out = outerR * sin2;

            buffer.vertex(matrix, x1In, 0, z1In).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x1Out, 0, z1Out).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x2Out, 0, z2Out).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x2In, 0, z2In).color(r, g, b, alpha).endVertex();
        }
    }
}