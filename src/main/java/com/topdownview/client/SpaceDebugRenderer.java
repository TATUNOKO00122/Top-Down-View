package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.ModState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * 空間判定デバッグ可視化レンダラー。
 *
 * <p>SpaceProbe（天井+壁スキャン）の動作確認用。
 *
 * <p>描画内容：
 * <ul>
 *   <li>天井位置：緑の小立方体（enclosed）/黄色（not enclosed）</li>
 *   <li>壁位置：各方向の壁検出位置に小立方体（緑=検出/赤=未検出）</li>
 *   <li>階段：シアン(StairBlock含む)/マゼンタ(通常ブロック)の小立方体</li>
 *   <li>HUD：画面左上に判定結果テキスト</li>
 * </ul>
 */
public final class SpaceDebugRenderer {

    private static final float BOX_SIZE = 0.4f;
    /** プレイヤーがこのブロック数以上移動したら再判定 */
    private static final int REPROBE_DISTANCE = 2;

    private static BlockPos lastProbeSeed = null;

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

        // プレイヤー移動に応じて再判定
        BlockPos playerPos = mc.player.blockPosition();
        if (lastProbeSeed == null || lastProbeSeed.distManhattan(playerPos) > REPROBE_DISTANCE) {
            ModState.SPACE_DEBUG.update(mc.level, playerPos);
            lastProbeSeed = playerPos;
        }

        SpaceProbe.Result result = ModState.SPACE_DEBUG.getCurrentResult();
        if (result == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        renderProbeResult(poseStack, mc, result, cameraPos);
        renderStaircases(poseStack, mc, cameraPos);
    }

    /**
     * 2D HUD 描画。RenderGuiEvent.Pre から呼ばれる。
     */
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!ModState.SPACE_DEBUG.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        GuiGraphics gg = event.getGuiGraphics();
        SpaceProbe.Result result = ModState.SPACE_DEBUG.getCurrentResult();

        renderHudText(gg, mc, result);
    }

    /** プレイヤーが次元移動等した時にキャッシュをクリア */
    public static void clearCache() {
        lastProbeSeed = null;
    }

    /**
     * プローブ結果（天井位置・壁位置）を可視化。
     */
    private static void renderProbeResult(PoseStack poseStack, Minecraft mc,
                                          SpaceProbe.Result result, Vec3 cameraPos) {
        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        float enclosedR = result.isEnclosed() ? 0.0f : 1.0f;
        float enclosedG = result.isEnclosed() ? 1.0f : 1.0f;
        float enclosedB = 0.0f;

        // 3D BFS 空気セルと壁殻セルのワイヤーフレーム描画
        com.topdownview.spatial.RoomFloodFill.Result roomRes = result.getRoomResult();
        BuildingClassifier.Result cls = ModState.SPACE_DEBUG.getCurrentClassification();
        RoomSegmentation.Result seg = ModState.SPACE_DEBUG.getCurrentSegmentation();
        if (roomRes != null && roomRes.isEnclosed()) {
            // 空気セル: プレイヤーの部屋は明るい緑、それ以外は暗い緑
            RoomSegmentation.Room playerRoom = seg.getPlayerRoom();
            for (long posLong : roomRes.getAirCells()) {
                int ax = BlockPos.getX(posLong);
                int ay = BlockPos.getY(posLong);
                int az = BlockPos.getZ(posLong);
                if (playerRoom != null && playerRoom.getAirCells().contains(posLong)) {
                    drawBox(poseStack, vertices, ax, ay, az, cameraPos, 0.2f, 1.0f, 0.4f);
                } else {
                    drawBox(poseStack, vertices, ax, ay, az, cameraPos, 0.1f, 0.5f, 0.2f);
                }
            }
            // 壁殻セル: BuildingClassifier のタグ色 (ROOF=青, WALL=橙, FLOOR=緑, UNKNOWN=灰)
            for (long posLong : roomRes.getShellCells()) {
                int sx = BlockPos.getX(posLong);
                int sy = BlockPos.getY(posLong);
                int sz = BlockPos.getZ(posLong);
                float r = 1.0f, g = 0.2f, b = 0.2f;
                if (cls.isValid()) {
                    BuildingClassifier.Label label = cls.getLabels().get(posLong);
                    if (label != null) {
                        switch (label) {
                            case ROOF -> { r = 0.3f; g = 0.5f; b = 1.0f; }
                            case WALL -> { r = 1.0f; g = 0.6f; b = 0.1f; }
                            case FLOOR -> { r = 0.2f; g = 0.9f; b = 0.2f; }
                            case UNKNOWN -> { r = 0.6f; g = 0.6f; b = 0.6f; }
                        }
                    }
                }
                drawBox(poseStack, vertices, sx, sy, sz, cameraPos, r, g, b);
            }
        }

        // 天井位置
        if (result.hasCeiling()) {
            BlockPos origin = result.getOrigin();
            drawBox(poseStack, vertices, origin.getX(), result.getCeilingY(), origin.getZ(),
                    cameraPos, enclosedR, enclosedG, enclosedB);
        }

        // 各方向の壁位置
        Direction[] dirs = result.getDirections();
        int[] distances = result.getWallDistances();
        for (int i = 0; i < dirs.length; i++) {
            if (distances[i] < 0) continue;
            BlockPos origin = result.getOrigin();
            int wx = origin.getX() + dirs[i].getStepX() * distances[i];
            int wz = origin.getZ() + dirs[i].getStepZ() * distances[i];
            // 壁は3レベルのいずれかで検出 — 目線レベル(Y+1)に代表として描画
            drawBox(poseStack, vertices, wx, origin.getY() + 1, wz,
                    cameraPos, enclosedR, enclosedG, enclosedB);
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
                drawBox(poseStack, vertices, pos.getX(), pos.getY(), pos.getZ(),
                        cameraPos, r, g, b);
            }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    /** 小立方体を描画するヘルパー */
    private static void drawBox(PoseStack poseStack, VertexConsumer vertices,
                                int blockX, int blockY, int blockZ, Vec3 cameraPos,
                                float r, float g, float b) {
        double x = blockX + (1.0 - BOX_SIZE) / 2.0 - cameraPos.x;
        double y = blockY + (1.0 - BOX_SIZE) / 2.0 - cameraPos.y;
        double z = blockZ + (1.0 - BOX_SIZE) / 2.0 - cameraPos.z;
        AABB box = new AABB(0, 0, 0, BOX_SIZE, BOX_SIZE, BOX_SIZE);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        LevelRenderer.renderLineBox(poseStack, vertices, box, r, g, b, 1.0f);
        poseStack.popPose();
    }

    private static void renderHudText(GuiGraphics gg, Minecraft mc, SpaceProbe.Result result) {
        int x = 8;
        int y = 8;
        int lineHeight = 11;

        // ガイド
        gg.drawString(mc.font, "[Space Debug] F6: toggle", x, y, 0xFFFFFFFF, false);
        y += lineHeight;

        if (!com.topdownview.Config.isStaircaseExclusionEnabled()) {
            gg.drawString(mc.font, "階段除外機能がオフのため空間判定は無効です", x, y, 0xFFFF5555, false);
            return;
        }

        if (result == null) {
            gg.drawString(mc.font, "(no result)", x, y, 0xFFAAAAAA, false);
            return;
        }

        // 判定結果
        int resultColor = result.isEnclosed() ? 0xFF00FF00 : 0xFFFFFF00;
        gg.drawString(mc.font, "Enclosed: " + result.isEnclosed(), x, y, resultColor, false);
        y += lineHeight;

        // 天井情報
        String ceilingStr = result.hasCeiling()
                ? "Y=" + result.getCeilingY() + " (dist=" + (result.getCeilingY() - result.getOrigin().getY()) + ")"
                : "none";
        gg.drawString(mc.font, "Ceiling: " + ceilingStr, x, y, 0xFFCCCCCC, false);
        y += lineHeight;

        // 壁情報
        Direction[] dirs = result.getDirections();
        int[] distances = result.getWallDistances();
        StringBuilder wallStr = new StringBuilder("Walls (");
        wallStr.append(result.getWalledCount()).append("/").append(dirs.length).append("): ");
        for (int i = 0; i < dirs.length; i++) {
            if (i > 0) wallStr.append("  ");
            wallStr.append(dirs[i].name().charAt(0));
            wallStr.append(distances[i] >= 0 ? distances[i] : "-");
        }
        gg.drawString(mc.font, wallStr.toString(), x, y, 0xFFCCCCCC, false);
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

        // 空間セル情報
        com.topdownview.spatial.RoomFloodFill.Result roomRes = result.getRoomResult();
        if (roomRes != null && roomRes.isEnclosed()) {
            gg.drawString(mc.font, "Room Cells: Air=" + roomRes.getAirCells().size()
                    + "  Shell=" + roomRes.getShellCells().size(), x, y, 0xFF88FF88, false);
            y += lineHeight;
            BlockPos minP = roomRes.getMinPos();
            BlockPos maxP = roomRes.getMaxPos();
            int dx = maxP.getX() - minP.getX() + 1;
            int dy = maxP.getY() - minP.getY() + 1;
            int dz = maxP.getZ() - minP.getZ() + 1;
            gg.drawString(mc.font, "Room AABB: " + dx + "x" + dy + "x" + dz, x, y, 0xFFCCCCCC, false);
            y += lineHeight;

            RoomSegmentation.Result seg = ModState.SPACE_DEBUG.getCurrentSegmentation();
            if (seg.isValid()) {
                RoomSegmentation.Room pr = seg.getPlayerRoom();
                String playerRoom = (pr != null)
                        ? seg.getPlayerRoomIndex() + " (storey " + pr.getStorey() + ", " + pr.size() + " cells)"
                        : "-";
                gg.drawString(mc.font, "Storeys: " + seg.getStoreyCount() + "  Rooms: " + seg.getRooms().size()
                        + "  PlayerRoom: " + playerRoom, x, y, 0xFF88DDFF, false);
                y += lineHeight;
            }

            BuildingClassifier.Result cls = ModState.SPACE_DEBUG.getCurrentClassification();
            if (cls.isValid()) {
                int roof = 0, wall = 0, floor = 0, unknown = 0;
                for (BuildingClassifier.Label label : cls.getLabels().values()) {
                    switch (label) {
                        case ROOF -> roof++;
                        case WALL -> wall++;
                        case FLOOR -> floor++;
                        default -> unknown++;
                    }
                }
                gg.drawString(mc.font, "Tags: ROOF=" + roof + " WALL=" + wall + " FLOOR=" + floor
                        + " UNK=" + unknown + "  Shape=" + cls.getRoofShape(), x, y, 0xFFDDCC88, false);
                y += lineHeight;
            }
        }

        int sliceY = TopDownCuller.getInstance().getCeilingSliceY();
        String sliceText = (sliceY == Integer.MIN_VALUE)
                ? "none"
                : "Y=" + sliceY + " cols=" + TopDownCuller.getInstance().getCeilingSliceColumns();
        gg.drawString(mc.font, "CeilingSlice: " + sliceText
                + "  playerY=" + mc.player.blockPosition().getY(), x, y, 0xFFFFAA66, false);
        y += lineHeight;

        gg.drawString(mc.font, "Seed: [" + result.getOrigin().getX() + ","
                + result.getOrigin().getY() + "," + result.getOrigin().getZ() + "]", x, y, 0xFFCCCCCC, false);
        y += lineHeight;
        gg.drawString(mc.font, "Probe: " + ModState.SPACE_DEBUG.getLastProbeTimeMs() + "ms", x, y, 0xFFCCCCCC, false);
    }
}
