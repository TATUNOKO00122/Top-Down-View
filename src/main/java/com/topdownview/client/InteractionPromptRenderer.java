package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * インタラクト可能ブロックの周囲にSF風のターゲットブラケットと操作ガイドを表示するレンダラー。
 * ブロックの形状（ドアやベッド等の連結ブロック含む）に合わせてブラケットの大きさを自動調整します。
 */
public final class InteractionPromptRenderer {

    private record BlockTargetInfo(BlockPos pos, Component blockName, Component guideText, AABB localBounds) {}

    private InteractionPromptRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 3Dワールド内のターゲットブロックにSF風のターゲットUIを描画します。
     * RenderLevelStageEvent で呼び出されます。
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // AFTER_PARTICLES ステージで描画を行います
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        // トップダウンモードが有効、かつ設定でプロンプト表示がオンの場合のみ処理
        if (!ModState.STATUS.isEnabled() || !Config.isShowInteractionPrompt()) {
            return;
        }

        // 表示サイズ設定が0以下の場合は描画しない
        if (Config.getInteractionPromptScale() <= 0.0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        BlockTargetInfo info = getTargetInfo(mc);
        if (info == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        BlockPos pos = info.pos();
        AABB localBounds = info.localBounds();

        // 描画位置の計算（ブロックの結合境界を含むAABBの中心）
        Vec3 center = localBounds.getCenter();
        double x = pos.getX() + center.x - cameraPos.x;
        double y = pos.getY() + center.y - cameraPos.y;
        double z = pos.getZ() + center.z - cameraPos.z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // ビルボード処理：常にカメラの正面を向くように回転
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

        // スケール調整（カメラからの距離とサイズ設定（Config.getInteractionPromptScale）を適用）
        double distance = cameraPos.distanceTo(new Vec3(pos.getX() + center.x, pos.getY() + center.y, pos.getZ() + center.z));
        float scale = (float) (0.002F * distance * Config.getInteractionPromptScale());
        
        // 極端に小さくなったり大きくなったりするのを防ぐためのクランプ制限値
        scale = Math.max(0.005F * (float) Config.getInteractionPromptScale(), Math.min(0.15F * (float) Config.getInteractionPromptScale(), scale));
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        // 8つの頂点をビルボード空間（カメラの視線方向）に射影して、見かけ上のサイズ（幅・高さ）を計算する
        double ry = Math.toRadians(camera.getYRot());
        double rx = Math.toRadians(camera.getXRot());

        double[] xs = { localBounds.minX, localBounds.maxX };
        double[] ys = { localBounds.minY, localBounds.maxY };
        double[] zs = { localBounds.minZ, localBounds.maxZ };

        double minU = Double.MAX_VALUE;
        double maxU = -Double.MAX_VALUE;
        double minV = Double.MAX_VALUE;
        double maxV = -Double.MAX_VALUE;

        for (double vx : xs) {
            for (double vy : ys) {
                for (double vz : zs) {
                    double dx = vx - center.x;
                    double dy = vy - center.y;
                    double dz = vz - center.z;

                    // Y軸回転
                    double x1 = dx * Math.cos(ry) - dz * Math.sin(ry);
                    double z1 = dx * Math.sin(ry) + dz * Math.cos(ry);

                    // X軸回転
                    double y2 = dy * Math.cos(-rx) - z1 * Math.sin(-rx);

                    if (x1 < minU) minU = x1;
                    if (x1 > maxU) maxU = x1;
                    if (y2 < minV) minV = y2;
                    if (y2 > maxV) maxV = y2;
                }
            }
        }

        // ビルボード空間上でのサイズ（1ブロック = 20px 換算 + 少しの余白）
        float boxWidth = (float) (maxU - minU) * 20.0F + 4.0F;
        float boxHeight = (float) (maxV - minV) * 20.0F + 4.0F;

        // L字ブラケットのサイズ設計
        float xMin = -boxWidth / 2.0F;
        float xMax = boxWidth / 2.0F;
        float yMin = -boxHeight / 2.0F;
        float yMax = boxHeight / 2.0F;
        float len = Math.min(5.0F, Math.min(boxWidth, boxHeight) * 0.3F); // 枠が極端に小さい場合はL字の長さも縮小する
        float thickness = 1.0F;   // L字の線の太さ

        // 深度テストを一時的に無効化し、他のあらゆる3D要素（ブロック、プレビューなど）より手前に表示する
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        // 1. L字ブラケット（ターゲット枠）の描画
        VertexConsumer builder = bufferSource.getBuffer(RenderType.textBackgroundSeeThrough());
        drawLBracket(builder, matrix, xMin, xMax, yMin, yMax, len, thickness);

        boolean shadow = Config.isInteractionPromptShadow();

        // 2. オブジェクト名（中央上）の描画
        Component blockName = info.blockName();
        float nameX = -font.width(blockName) / 2.0F;
        float nameY = yMin - font.lineHeight - 2.0F; // 枠の少し上
        font.drawInBatch(blockName, nameX, nameY, 0xFFFFFFFF, shadow, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);

        // 3. 操作ガイドテキスト（左側、右揃え）の描画
        Component guideText = info.guideText();
        float guideWidth = font.width(guideText);
        float guideX = xMin - guideWidth - 6.0F; // 枠の左端から6px左に離す
        float guideY = -font.lineHeight / 2.0F;  // 縦軸中央
        font.drawInBatch(guideText, guideX, guideY, 0xFFFFFFFF, shadow, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);

        poseStack.popPose();

        // バッチのフラッシュ（深度テストが無効化された状態で描画が実行されます）
        bufferSource.endBatch(RenderType.textBackgroundSeeThrough());
        bufferSource.endBatch();

        // 深度テストを有効化に戻す
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /**
     * 四隅に白いL字ブラケットを描画するヘルパー
     */
    private static void drawLBracket(VertexConsumer builder, Matrix4f matrix, float xMin, float xMax, float yMin, float yMax, float len, float thickness) {
        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;
        float a = 0.8F; // 80%不透明
        int uv = 15728880;

        // 左上
        drawRect(builder, matrix, xMin, xMin + len, yMin, yMin + thickness, r, g, b, a, uv);
        drawRect(builder, matrix, xMin, xMin + thickness, yMin, yMin + len, r, g, b, a, uv);

        // 右上
        drawRect(builder, matrix, xMax - len, xMax, yMin, yMin + thickness, r, g, b, a, uv);
        drawRect(builder, matrix, xMax - thickness, xMax, yMin, yMin + len, r, g, b, a, uv);

        // 左下
        drawRect(builder, matrix, xMin, xMin + len, yMax - thickness, yMax, r, g, b, a, uv);
        drawRect(builder, matrix, xMin, xMin + thickness, yMax - len, yMax, r, g, b, a, uv);

        // 右下
        drawRect(builder, matrix, xMax - len, xMax, yMax - thickness, yMax, r, g, b, a, uv);
        drawRect(builder, matrix, xMax - thickness, xMax, yMax - len, yMax, r, g, b, a, uv);
    }

    private static void drawRect(VertexConsumer builder, Matrix4f matrix, float xMin, float xMax, float yMin, float yMax, float r, float g, float b, float a, int uv) {
        builder.vertex(matrix, xMin, yMin, 0.0F).color(r, g, b, a).uv2(uv).endVertex();
        builder.vertex(matrix, xMin, yMax, 0.0F).color(r, g, b, a).uv2(uv).endVertex();
        builder.vertex(matrix, xMax, yMax, 0.0F).color(r, g, b, a).uv2(uv).endVertex();
        builder.vertex(matrix, xMax, yMin, 0.0F).color(r, g, b, a).uv2(uv).endVertex();
    }

    /**
     * 現在ターゲットしているブロックの情報を取得します。
     */
    private static BlockTargetInfo getTargetInfo(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }

        net.minecraft.world.phys.HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        if (hitResult == null || hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return null;
        }

        net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // 看板（SignBlock）は除外（SignHoverRendererが別で動作するため）
        Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.SignBlock || 
            block instanceof net.minecraft.world.level.block.WallSignBlock) {
            return null;
        }

        Component action = getActionComponent(state, mc.level, pos);
        if (action == null) {
            return null;
        }

        // キー名を動的に取得
        String keyName = mc.options.keyUse.getTranslatedKeyMessage().getString();
        
        Component blockName = block.getName();
        // 「アクション名 - [キー名]」
        Component guideText = Component.literal(action.getString() + " - [" + keyName + "]");

        // ブロック形状の取得（ドアやベッドなどの結合ブロックに対応）
        AABB localBounds = getBlockInteractionBounds(state, mc.level, pos);

        return new BlockTargetInfo(pos, blockName, guideText, localBounds);
    }

    /**
     * ドアやベッド、ダブルチェストなどの結合ブロックを考慮して、マージされたバウンディングボックスを取得します。
     */
    private static AABB getBlockInteractionBounds(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();
        AABB bounds = state.getShape(level, pos).bounds();
        
        // ワールド座標系（ブロック絶対座標）に一時的にマッピング
        AABB worldBounds = bounds.move(pos.getX(), pos.getY(), pos.getZ());

        // 1. ドアの場合 (上下マージ)
        if (block instanceof DoorBlock) {
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() == block) {
                AABB otherBounds = otherState.getShape(level, otherPos).bounds().move(otherPos.getX(), otherPos.getY(), otherPos.getZ());
                worldBounds = worldBounds.minmax(otherBounds);
            }
        }
        // 2. ベッドの場合 (前後マージ)
        else if (block instanceof BedBlock) {
            BedPart part = state.getValue(BedBlock.PART);
            Direction direction = state.getValue(BedBlock.FACING);
            BlockPos otherPos = (part == BedPart.FOOT) ? pos.relative(direction) : pos.relative(direction.getOpposite());
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() == block) {
                AABB otherBounds = otherState.getShape(level, otherPos).bounds().move(otherPos.getX(), otherPos.getY(), otherPos.getZ());
                worldBounds = worldBounds.minmax(otherBounds);
            }
        }
        // 3. ダブルチェストの場合 (左右マージ)
        else if (block instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction otherDir = (chestType == ChestType.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos otherPos = pos.relative(otherDir);
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.getBlock() == block) {
                    AABB otherBounds = otherState.getShape(level, otherPos).bounds().move(otherPos.getX(), otherPos.getY(), otherPos.getZ());
                    worldBounds = worldBounds.minmax(otherBounds);
                }
            }
        }

        // pos を原点 (0, 0, 0) としたローカル座標系に戻す
        return worldBounds.move(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    /**
     * ブロックの種類に応じたアクションテキストを取得します。
     */
    private static Component getActionComponent(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();

        // 1. ドア、トラップドア、ゲート
        if (state.is(net.minecraft.tags.BlockTags.DOORS) ||
            state.is(net.minecraft.tags.BlockTags.TRAPDOORS) ||
            state.is(net.minecraft.tags.BlockTags.FENCE_GATES)) {
            return Component.translatable("topdown_view.interaction.open_close");
        }

        // 2. ボタン、レバー、レッドストーン系スイッチ
        if (state.is(net.minecraft.tags.BlockTags.BUTTONS) ||
            block instanceof net.minecraft.world.level.block.LeverBlock ||
            block instanceof net.minecraft.world.level.block.RepeaterBlock ||
            block instanceof net.minecraft.world.level.block.ComparatorBlock) {
            return Component.translatable("topdown_view.interaction.toggle");
        }

        // 3. ベッド
        if (state.is(net.minecraft.tags.BlockTags.BEDS)) {
            return Component.translatable("topdown_view.interaction.sleep");
        }

        // 4. チェスト、樽、シュルカーボックスなどのコンテナで「開く」に相応しいもの
        if (block instanceof ChestBlock ||
            block instanceof net.minecraft.world.level.block.BarrelBlock ||
            block instanceof net.minecraft.world.level.block.ShulkerBoxBlock ||
            block instanceof net.minecraft.world.level.block.EnderChestBlock) {
            return Component.translatable("topdown_view.interaction.open");
        }

        // 5. MenuProvider（作業台、かまど、醸造台等）を持っているなら「使う」
        if (state.getMenuProvider(level, pos) != null) {
            return Component.translatable("topdown_view.interaction.use");
        }

        // 6. その他の特定のインタラクト可能ブロック
        if (block instanceof net.minecraft.world.level.block.BellBlock ||
            block instanceof net.minecraft.world.level.block.CakeBlock ||
            block instanceof net.minecraft.world.level.block.JukeboxBlock ||
            block instanceof net.minecraft.world.level.block.NoteBlock ||
            block instanceof net.minecraft.world.level.block.AnvilBlock) {
            return Component.translatable("topdown_view.interaction.interact");
        }

        return null;
    }
}
