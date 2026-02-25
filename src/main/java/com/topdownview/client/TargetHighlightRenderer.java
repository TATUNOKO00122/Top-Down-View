package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public class TargetHighlightRenderer {

    private static Entity lastGlowingEntity = null;

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
            return;
        if (!ClientForgeEvents.isTopDownView())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        // カスタムリーチ距離を使用（カリング距離と同期）
        double reach = MouseRaycast.getCustomReachDistance();

        // レイキャスト結果を更新
        MouseRaycast.INSTANCE.update(mc, event.getPartialTick(), reach);

        net.minecraft.world.phys.HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        Entity currentEntity = null;
        BlockPos topBlockPos = null;

        if (hitResult != null) {
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                currentEntity = ((EntityHitResult) hitResult).getEntity();
            } else if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                topBlockPos = ((net.minecraft.world.phys.BlockHitResult) hitResult).getBlockPos();
            }
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // --- エンティティハイライト処理 ---
        // 障害物がある場合はターゲットを無効化
        if (currentEntity != null && mc.player != null) {
            if (!hasLineOfSight(mc, mc.player, currentEntity)) {
                currentEntity = null;
            }
        }
        
        // 発光エフェクトの更新
        if (currentEntity != lastGlowingEntity) {
            // 前回のエンティティの発光をオフにする
            if (lastGlowingEntity != null) {
                ((com.topdownview.mixin.EntityAccessor) lastGlowingEntity).callSetSharedFlag(6, false);
            }
            // 今回のエンティティの発光をオンにする
            if (currentEntity != null) {
                ((com.topdownview.mixin.EntityAccessor) currentEntity).callSetSharedFlag(6, true);
            }
            lastGlowingEntity = currentEntity;
        }

        // エンティティがターゲットになっている場合はブロックハイライトを出さない
        if (currentEntity != null) {
            return;
        }

        // --- ブロックハイライト処理 ---
        if (topBlockPos != null) {
            renderBlockHighlight(poseStack, topBlockPos, cameraPos, mc);
        }
    }

    private static void renderBlockHighlight(PoseStack poseStack, BlockPos blockPos, Vec3 cameraPos, Minecraft mc) {
        // ブロックの位置をカメラ相対に変換
        double x = blockPos.getX() - cameraPos.x;
        double y = blockPos.getY() - cameraPos.y;
        double z = blockPos.getZ() - cameraPos.z;

        // ブロックの形状を取得
        VoxelShape shape = mc.level.getBlockState(blockPos).getShape(mc.level, blockPos);
        if (shape.isEmpty()) {
            // 空の形状の場合はフルブロックとして描画
            shape = net.minecraft.world.phys.shapes.Shapes.block();
        }

        // VoxelShape全体の境界ボックス(AABB)を取得してシンプルにする
        AABB box = shape.bounds();

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // 黒色(0.0f) + 透明度(0.4f) で細くシンプルに見せる (バニラ準拠のスタイル)
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        LevelRenderer.renderLineBox(poseStack, vertices, box, 0.0f, 0.0f, 0.0f, 0.4f);

        poseStack.popPose();

        // バッファをフラッシュ
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    /**
     * プレイヤーとエンティティの間に視線があるかチェック
     * ブロックが遮っている場合はfalseを返す
     */
    private static boolean hasLineOfSight(Minecraft mc, Entity player, Entity target) {
        Vec3 playerEyePos = player.getEyePosition(1.0f);
        Vec3 targetPos = target.getBoundingBox().getCenter();
        
        // レイキャストで障害物をチェック
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
            playerEyePos,
            targetPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        
        // ブロックにヒットしなかった場合は視線が通っている
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

}
