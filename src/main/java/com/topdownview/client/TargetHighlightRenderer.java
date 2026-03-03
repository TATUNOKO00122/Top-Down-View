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
        if (!ModState.STATUS.isEnabled())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        double reach = MouseRaycast.getCustomReachDistance();

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

        if (currentEntity != null && mc.player != null) {
            if (!hasLineOfSight(mc, mc.player, currentEntity)) {
                currentEntity = null;
            }
        }
        
        if (currentEntity != lastGlowingEntity) {
            if (lastGlowingEntity != null) {
                ((com.topdownview.mixin.EntityAccessor) lastGlowingEntity).callSetSharedFlag(6, false);
            }
            if (currentEntity != null) {
                ((com.topdownview.mixin.EntityAccessor) currentEntity).callSetSharedFlag(6, true);
            }
            lastGlowingEntity = currentEntity;
        }

        if (currentEntity != null) {
            return;
        }

        if (topBlockPos != null) {
            renderBlockHighlight(poseStack, topBlockPos, cameraPos, mc);
        }
    }

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

}
