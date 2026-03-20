package com.topdownview.client;

import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * ターゲット発光レンダラー
 * EpicFight式のアウトラインシステム
 * - 射程内：白色アウトライン
 * - 射程外：赤色アウトライン
 */
public final class TargetHighlightRenderer {

    private TargetHighlightRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
            return;
        if (!ModState.STATUS.isEnabled())
            return;

        // フリーカム中はハイライトを非表示
        if (ModState.CAMERA.isFreeCameraMode())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        net.minecraft.world.phys.HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        LivingEntity currentEntity = null;
        BlockPos topBlockPos = null;

        if (hitResult != null) {
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
                if (hitEntity instanceof LivingEntity living && !(hitEntity instanceof Player)) {
                    currentEntity = living;
                }
            } else if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                topBlockPos = ((BlockHitResult) hitResult).getBlockPos();
            }
        }

        // ターゲット状態を更新
        updateTargetState(mc, mc.player, currentEntity);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // エンティティがいない場合はブロックハイライトを描画
        if (currentEntity == null && topBlockPos != null) {
            renderBlockHighlight(poseStack, topBlockPos, cameraPos, mc);
        }
    }

    /**
     * ターゲット状態を更新（射程判定含む）
     */
    private static void updateTargetState(Minecraft mc, Player player, @Nullable LivingEntity target) {
        // 前回のターゲットをクリア
        LivingEntity lastTarget = ModState.TARGET_HIGHLIGHT.getCurrentTarget();
        if (lastTarget != null && lastTarget != target) {
            ((com.topdownview.mixin.EntityAccessor) lastTarget).callSetSharedFlag(6, false);
        }

        // 新しいターゲットを設定
        ModState.TARGET_HIGHLIGHT.setCurrentTarget(target);

        if (target != null) {
            // 弓・クロスボウを持っている場合は射程判定をスキップ（常に白色表示）
            boolean inRange;
            if (com.topdownview.state.TargetHighlightState.isRangedWeapon(player)) {
                inRange = true;
            } else {
                // 通常の射程判定（プレイヤーの装備に基づく動的射程）
                double distance = player.distanceTo(target);
                inRange = distance <= ModState.TARGET_HIGHLIGHT.getAttackRange(player) 
                    && hasLineOfSight(mc, player, target);
            }
            
            ModState.TARGET_HIGHLIGHT.setInRange(inRange);

            // 発光フラグを設定（Mixinが色を適用）
            ((com.topdownview.mixin.EntityAccessor) target).callSetSharedFlag(6, true);
        } else {
            ModState.TARGET_HIGHLIGHT.setInRange(false);
        }
    }

    /**
     * 視線が通っているかチェック
     */
    private static boolean hasLineOfSight(Minecraft mc, Entity player, Entity target) {
        Vec3 playerEyePos = player.getEyePosition(1.0f);
        Vec3 targetPos = target.getBoundingBox().getCenter();
        
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
            playerEyePos,
            targetPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /**
     * ブロックハイライトを描画（従来通り）
     */
    private static void renderBlockHighlight(PoseStack poseStack, BlockPos blockPos, Vec3 cameraPos, Minecraft mc) {
        double x = blockPos.getX() - cameraPos.x;
        double y = blockPos.getY() - cameraPos.y;
        double z = blockPos.getZ() - cameraPos.z;

        VoxelShape shape = mc.level.getBlockState(blockPos).getShape(mc.level, blockPos);
        if (shape.isEmpty()) {
            shape = net.minecraft.world.phys.shapes.Shapes.block();
        }

        AABB box = shape.bounds();

        VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        RenderSystem.lineWidth(2.0f);

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        LevelRenderer.renderLineBox(poseStack, vertices, box, 0.0f, 0.0f, 0.0f, 0.4f);

        poseStack.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }
}
