package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.spatial.Opening;
import com.topdownview.spatial.OpeningType;
import com.topdownview.spatial.SpaceRegion;
import com.topdownview.spatial.SpaceType;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
 *   <li>空気ブロック：サンプリング薄色細線（上限200個）</li>
 *   <li>壁ブロック：サンプリング赤細線（上限150個）</li>
 *   <li>HUD：画面左上に空間情報テキスト</li>
 * </ul>
 */
public final class SpaceDebugRenderer {

    private static final float SEED_BOX_SIZE = 0.5f;
    private static final float OPENING_BOX_SIZE = 0.4f;
    private static final int AIR_SAMPLE_LIMIT = 200;
    private static final int WALL_SAMPLE_LIMIT = 150;
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

        renderAirBlocks(poseStack, mc, region, cameraPos);
        renderWallBlocks(poseStack, mc, region, cameraPos);
        renderOpenings(poseStack, mc, region, cameraPos);
        renderSeed(poseStack, mc, region, cameraPos);
        renderBoundingBox(poseStack, mc, region, cameraPos);
        renderFloatingInfo(poseStack, mc, region, cameraPos);
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
        RenderSystem.lineWidth(3.0f);

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
        RenderSystem.lineWidth(4.0f);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        LevelRenderer.renderLineBox(poseStack, vertices, box, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderOpenings(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        if (region.getOpenings().isEmpty()) return;

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.5f);

        for (Opening opening : region.getOpenings()) {
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
            LevelRenderer.renderLineBox(poseStack, vertices, box, r, g, b, 1.0f);
            poseStack.popPose();
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void renderAirBlocks(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        if (region.getAirBlocks().isEmpty()) return;
        int step = Math.max(1, region.getAirBlockCount() / AIR_SAMPLE_LIMIT);
        float[] color = getTypeColor(region.getType());

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        RenderSystem.lineWidth(1.0f);

        int idx = 0;
        int drawn = 0;
        for (BlockPos pos : region.getAirBlocks()) {
            if (idx++ % step != 0) continue;
            if (drawn++ >= AIR_SAMPLE_LIMIT) break;

            double x = pos.getX() - cameraPos.x;
            double y = pos.getY() - cameraPos.y;
            double z = pos.getZ() - cameraPos.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            LevelRenderer.renderLineBox(poseStack, vertices, 0, 0, 0, 1, 1, 1,
                    color[0] * 0.4f, color[1] * 0.4f, color[2] * 0.4f, 0.5f);
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

            double x = pos.getX() - cameraPos.x;
            double y = pos.getY() - cameraPos.y;
            double z = pos.getZ() - cameraPos.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            LevelRenderer.renderLineBox(poseStack, vertices, 0, 0, 0, 1, 1, 1,
                    1.0f, 0.2f, 0.2f, 0.5f);
            poseStack.popPose();
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    /**
     * シード位置の上に空間情報テキストを3D空間内に浮かべて表示（ビルボード）。
     */
    private static void renderFloatingInfo(PoseStack poseStack, Minecraft mc, SpaceRegion region, Vec3 cameraPos) {
        BlockPos seed = region.getSeed();
        double x = seed.getX() + 0.5 - cameraPos.x;
        double y = seed.getY() + 2.5 - cameraPos.y;
        double z = seed.getZ() + 0.5 - cameraPos.z;

        Font font = mc.font;
        String[] lines = {
                "Type: " + region.getType(),
                "Air: " + region.getAirBlockCount(),
                "Walls: " + region.getWallBlockCount(),
                "Openings: " + region.getOpenings().size(),
                "Bounds: [" + region.getMinX() + "," + region.getMinY() + "," + region.getMinZ()
                        + "]->[" + region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + "]",
                "Time: " + ModState.SPACE_DEBUG.getLastExploreTimeMs() + "ms"
        };

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // カメラに向ける（ビルボード）
        poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        // テキストサイズを縮小。Y反転で上向き
        float scale = -0.025f;
        poseStack.scale(scale, scale, scale);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float lineHeight = font.lineHeight + 1;
        float startY = -(lines.length * lineHeight) / 2.0f;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float textX = -font.width(line) / 2.0f;
            float textY = startY + i * lineHeight;
            font.drawInBatch(line, textX, textY, 0xFFFFFFFF, false,
                    poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL,
                    0x90000000, 15728880);
        }
        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void renderHudText(GuiGraphics gg, Minecraft mc, SpaceRegion region) {
        int x = 8;
        int y = 8;
        int lineHeight = 11;

        // ガイド
        gg.drawString(mc.font, "[Space Debug] F6: toggle", x, y, 0xFFFFFFFF, false);
        y += lineHeight;

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
