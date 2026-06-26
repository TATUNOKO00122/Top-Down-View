package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.spatial.Opening;
import com.topdownview.spatial.OpeningType;
import com.topdownview.spatial.SpaceRegion;
import com.topdownview.spatial.SpaceType;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.ModState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * 空間探索デバッグ可視化レンダラー。
 *
 * <p>SpaceExplorer/SpaceAnalyzer の動作確認用。
 * 既存のCulling等には統合せず、独立して描画する。
 *
 * <p>描画内容：
 * <ul>
 *   <li>境界ボックス：太線、色＝SpaceType（ROOM=緑/CORRIDOR=青/OUTDOOR=黄/CAVE=紫/UNKNOWN=灰）</li>
 *   <li>シード位置：白の小立方体</li>
 *   <li>開口部：黄（小穴）/オレンジ（大穴）の小立方体</li>
 *   <li>壁ブロック：サンプリング赤細線（上限150個）</li>
 *   <li>階段：シアン(StairBlock含む)/マゼンタ(通常ブロック)の小立方体</li>
 *   <li>HUD：画面左上に空間情報テキスト</li>
 * </ul>
 */
public final class SpaceDebugRenderer {

    private static final float SEED_BOX_SIZE = 0.5f;
    private static final float OPENING_BOX_SIZE = 0.4f;
    private static final float STAIR_BOX_SIZE = 0.45f;
    private static final float WALL_BOX_SIZE = 0.15f;
    private static final int OPENING_SAMPLE_LIMIT = 30;
    private static final int WALL_SAMPLE_LIMIT = 50;
    /** プレイヤーがこのブロック数以上移動したら再探索 */
    private static final int REEXPLORE_DISTANCE = 2;

    private static BlockPos lastExploreSeed = null;

    private SpaceDebugRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 3D 描画。RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS から呼ばれる。
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ModState.SPACE_DEBUG.isEnabled()) return;
        if (!com.topdownview.Config.isStaircaseExclusionEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // プレイヤー移動に応じて再探索
        BlockPos playerPos = mc.player.blockPosition();
        if (lastExploreSeed == null || lastExploreSeed.distManhattan(playerPos) > REEXPLORE_DISTANCE) {
            ModState.SPACE_DEBUG.update(mc.level, playerPos);
            lastExploreSeed = playerPos;
        }

        SpaceRegion region = ModState.SPACE_DEBUG.getCurrentRegion();
        if (region == null || !region.isValid()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        renderWallBlocks(poseStack, mc, region, cameraPos);
        renderOpenings(poseStack, mc, region, cameraPos);
        renderStaircases(poseStack, mc, cameraPos);
        renderSeed(poseStack, mc, region, cameraPos);
        renderBoundingBox(poseStack, mc, region, cameraPos);
    }

    /**
     * 2D HUD 描画。RenderGuiEvent.Pre から呼ばれる。
     */
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!ModState.SPACE_DEBUG.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        GuiGraphics gg = event.getGuiGraphics();
        SpaceRegion region = ModState.SPACE_DEBUG.getCurrentRegion();

        renderHudText(gg, mc, region);
    }

    /** プレイヤーが次元移動等した時にキャッシュをクリア */
    public static void clearCache() {
        lastExploreSeed = null;
    }

    private static void renderBoundingBox(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        float[] rgb = getTypeColor(region.getType());
        double x = region.getMinX() - cameraPos.x;
        double y = region.getMinY() - cameraPos.y;
        double z = region.getMinZ() - cameraPos.z;
        double dx = region.getMaxX() - region.getMinX() + 1;
        double dy = region.getMaxY() - region.getMinY() + 1;
        double dz = region.getMaxZ() - region.getMinZ() + 1;

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(1.5f);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        LevelRenderer.renderLineBox(poseStack, vertices, 0, 0, 0, dx, dy, dz,
                rgb[0], rgb[1], rgb[2], 1.0f);
        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderSeed(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        BlockPos seed = region.getSeed();
        double x = seed.getX() + (1.0 - SEED_BOX_SIZE) / 2.0 - cameraPos.x;
        double y = seed.getY() + (1.0 - SEED_BOX_SIZE) / 2.0 - cameraPos.y;
        double z = seed.getZ() + (1.0 - SEED_BOX_SIZE) / 2.0 - cameraPos.z;
        AABB box = new AABB(0, 0, 0, SEED_BOX_SIZE, SEED_BOX_SIZE, SEED_BOX_SIZE);

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        LevelRenderer.renderLineBox(poseStack, vertices, box, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderOpenings(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        if (region.getOpenings().isEmpty()) return;
        int step = Math.max(1, region.getOpenings().size() / OPENING_SAMPLE_LIMIT);

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(1.0f);

        int idx = 0;
        int drawn = 0;
        for (Opening opening : region.getOpenings()) {
            if (idx++ % step != 0) continue;
            if (drawn++ >= OPENING_SAMPLE_LIMIT) break;

            BlockPos rep = opening.getRepresentativePos();
            double x = rep.getX() + (1.0 - OPENING_BOX_SIZE) / 2.0 - cameraPos.x;
            double y = rep.getY() + (1.0 - OPENING_BOX_SIZE) / 2.0 - cameraPos.y;
            double z = rep.getZ() + (1.0 - OPENING_BOX_SIZE) / 2.0 - cameraPos.z;

            float r, g, b;
            if (opening.getType() == OpeningType.BOUNDARY_HOLE) {
                r = 1.0f; g = 1.0f; b = 0.0f; // 黄：境界内の小穴
            } else {
                r = 1.0f; g = 0.5f; b = 0.0f; // オレンジ：別空間への接続
            }

            AABB box = new AABB(0, 0, 0, OPENING_BOX_SIZE, OPENING_BOX_SIZE, OPENING_BOX_SIZE);
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            LevelRenderer.renderLineBox(poseStack, vertices, box, r, g, b, 0.4f);
            poseStack.popPose();
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderWallBlocks(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        if (region.getWallBlocks().isEmpty()) return;
        int step = Math.max(1, region.getWallBlockCount() / WALL_SAMPLE_LIMIT);

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(1.0f);

        int idx = 0;
        int drawn = 0;
        for (BlockPos pos : region.getWallBlocks()) {
            if (idx++ % step != 0) continue;
            if (drawn++ >= WALL_SAMPLE_LIMIT) break;

            double x = pos.getX() + (1.0 - WALL_BOX_SIZE) / 2.0 - cameraPos.x;
            double y = pos.getY() + (1.0 - WALL_BOX_SIZE) / 2.0 - cameraPos.y;
            double z = pos.getZ() + (1.0 - WALL_BOX_SIZE) / 2.0 - cameraPos.z;
            AABB box = new AABB(0, 0, 0, WALL_BOX_SIZE, WALL_BOX_SIZE, WALL_BOX_SIZE);

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            LevelRenderer.renderLineBox(poseStack, vertices, box, 1.0f, 0.2f, 0.2f, 0.3f);
            poseStack.popPose();
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    /**
     * 検出された階段を描画。
     * StairBlock含む＝シアン、通常ブロックのみ＝マゼンタ。
     */
    private static void renderStaircases(PoseStack poseStack, Minecraft mc, Vec3 cameraPos) {
        List<Staircase> staircases = ModState.SPACE_DEBUG.getCurrentStaircases();
        if (staircases.isEmpty()) return;

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(1.25f);

        for (Staircase stair : staircases) {
            float r, g, b;
            if (stair.containsStairBlocks()) {
                r = 0.0f; g = 1.0f; b = 1.0f; // シアン：StairBlock含む
            } else {
                r = 1.0f; g = 0.0f; b = 1.0f; // マゼンタ：通常ブロックのみ
            }

            for (BlockPos pos : stair.getSteps()) {
                double x = pos.getX() + (1.0 - STAIR_BOX_SIZE) / 2.0 - cameraPos.x;
                double y = pos.getY() + (1.0 - STAIR_BOX_SIZE) / 2.0 - cameraPos.y;
                double z = pos.getZ() + (1.0 - STAIR_BOX_SIZE) / 2.0 - cameraPos.z;
                AABB box = new AABB(0, 0, 0, STAIR_BOX_SIZE, STAIR_BOX_SIZE, STAIR_BOX_SIZE);
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                LevelRenderer.renderLineBox(poseStack, vertices, box, r, g, b, 1.0f);
                poseStack.popPose();
            }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderHudText(GuiGraphics gg, Minecraft mc, SpaceRegion region) {
        int x = 8;
        int y = 8;
        int lineHeight = 11;

        // ガイド
        gg.drawString(mc.font, "[Space Debug] F6: toggle", x, y, 0xFFFFFFFF, false);
        y += lineHeight;

        if (!com.topdownview.Config.isStaircaseExclusionEnabled()) {
            gg.drawString(mc.font, "階段除外機能がオフのため空間探索は無効です", x, y, 0xFFFF5555, false);
            return;
        }

        if (region == null || !region.isValid()) {
            gg.drawString(mc.font, "(no region)", x, y, 0xFFAAAAAA, false);
            return;
        }

        // 空間情報
        int typeColor = getTypeTextColor(region.getType());
        gg.drawString(mc.font, "Type: " + region.getType(), x, y, typeColor, false);
        y += lineHeight;
        gg.drawString(mc.font, "Air: " + region.getAirBlockCount()
                + "  Walls: " + region.getWallBlockCount()
                + "  Openings: " + region.getOpenings().size(), x, y, 0xFFFFFFFF, false);
        y += lineHeight;

        // 階段情報
        List<Staircase> staircases = ModState.SPACE_DEBUG.getCurrentStaircases();
        if (!staircases.isEmpty()) {
            int stairStepsTotal = 0;
            int stairBlocksTotal = 0;
            for (Staircase s : staircases) {
                stairStepsTotal += s.getStepCount();
                if (s.containsStairBlocks()) stairBlocksTotal++;
            }
            int stairColor = stairBlocksTotal > 0 ? 0xFF00FFFF : 0xFFFF00FF;
            gg.drawString(mc.font, "Stairs: " + staircases.size()
                    + "  Steps: " + stairStepsTotal
                    + "  StairBlock: " + stairBlocksTotal, x, y, stairColor, false);
            y += lineHeight;
        }
        gg.drawString(mc.font, "Bounds: [" + region.getMinX() + "," + region.getMinY() + "," + region.getMinZ()
                + "]->[" + region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + "]", x, y, 0xFFCCCCCC, false);
        y += lineHeight;
        gg.drawString(mc.font, "Seed: [" + region.getSeed().getX() + ","
                + region.getSeed().getY() + "," + region.getSeed().getZ() + "]", x, y, 0xFFCCCCCC, false);
        y += lineHeight;
        gg.drawString(mc.font, "Explore: " + ModState.SPACE_DEBUG.getLastExploreTimeMs() + "ms", x, y, 0xFFCCCCCC, false);
    }

    private static float[] getTypeColor(SpaceType type) {
        return switch (type) {
            case ROOM -> new float[]{0.0f, 1.0f, 0.0f};
            case CORRIDOR -> new float[]{0.3f, 0.6f, 1.0f};
            case OUTDOOR -> new float[]{1.0f, 1.0f, 0.0f};
            case CAVE -> new float[]{1.0f, 0.4f, 1.0f};
            case UNKNOWN -> new float[]{0.5f, 0.5f, 0.5f};
        };
    }

    private static int getTypeTextColor(SpaceType type) {
        return switch (type) {
            case ROOM -> 0xFF00FF00;
            case CORRIDOR -> 0xFF66AAFF;
            case OUTDOOR -> 0xFFFFFF00;
            case CAVE -> 0xFFFF66FF;
            case UNKNOWN -> 0xFFAAAAAA;
        };
    }
}
