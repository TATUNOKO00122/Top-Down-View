package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class MovementController {

    private MovementController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Baritone使用時は移動制御を委譲（干渉しない）
        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            return;
        }

        float originalForward = event.getInput().forwardImpulse;
        float originalStrafe = event.getInput().leftImpulse;
        boolean hasManualInput = Math.abs(originalForward) > 0.01f || Math.abs(originalStrafe) > 0.01f;

        if (Config.isClickToMoveEnabled() && ModState.CLICK_TO_MOVE.isMoving()) {
            if (hasManualInput) {
                ClickToMoveController.stop();
                mc.player.setSprinting(false);
            } else {
                float[] moveInput = ClickToMoveController.calculateMovementInput(mc);
                if (moveInput != null) {
                    event.getInput().forwardImpulse = moveInput[0];
                    event.getInput().leftImpulse = moveInput[1];

                    if (Config.isForceAutoJump() && shouldAutoJump(mc, moveInput[0], moveInput[1])) {
                        event.getInput().jumping = true;
                    }

                    updateSprint(mc);
                    return;
                }
            }
        }

        if (mc.player.isSprinting() && !hasManualInput) {
            mc.player.setSprinting(false);
        }

        float cameraYaw = ModState.CAMERA.getYaw();
        float playerYaw = mc.player.getYRot();
        float diffYaw = cameraYaw - playerYaw;
        float rad = (float) Math.toRadians(diffYaw);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        float newForward = originalForward * cos + originalStrafe * sin;
        float newStrafe = originalStrafe * cos - originalForward * sin;

        event.getInput().forwardImpulse = newForward;
        event.getInput().leftImpulse = newStrafe;
    }

    private static boolean shouldAutoJump(Minecraft mc, float forward, float strafe) {
        if (mc.player == null || mc.level == null) return false;
        if (!mc.player.onGround()) return false;

        float speed = Mth.sqrt(forward * forward + strafe * strafe);
        if (speed < 0.1f) return false;

        float moveYaw = mc.player.getYRot() + (float) Math.toDegrees(Math.atan2(-strafe, forward));
        float moveYawRad = (float) Math.toRadians(moveYaw);

        double dx = -Math.sin(moveYawRad) * 0.5;
        double dz = Math.cos(moveYawRad) * 0.5;

        Vec3 playerPos = mc.player.position();
        Vec3 aheadPos = playerPos.add(dx, 0, dz);

        BlockPos aheadBlockPos = BlockPos.containing(aheadPos);
        BlockPos feetPos = BlockPos.containing(playerPos);

        BlockState aheadState = mc.level.getBlockState(aheadBlockPos);
        BlockState aboveAheadState = mc.level.getBlockState(aheadBlockPos.above());

        if (aheadState.isAir() || aheadState.getCollisionShape(mc.level, aheadBlockPos).isEmpty()) {
            return false;
        }

        if (aheadState.getBlock() instanceof StairBlock) {
            return false;
        }

        double playerFeetY = Math.floor(playerPos.y);
        double blockTopY = aheadState.getCollisionShape(mc.level, aheadBlockPos).max(Direction.Axis.Y) + aheadBlockPos.getY();

        if (blockTopY - playerFeetY <= 1.2 && blockTopY - playerFeetY > 0.6) {
            if (aboveAheadState.isAir() || aboveAheadState.getCollisionShape(mc.level, aheadBlockPos.above()).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static void updateSprint(Minecraft mc) {
        if (mc.player == null) return;

        Vec3 destination = ClickToMoveController.getEffectiveDestination(mc);
        if (destination == null) {
            mc.player.setSprinting(false);
            return;
        }

        double distance = mc.player.position().distanceTo(destination);
        double threshold = Config.getSprintDistanceThreshold();

        if (distance >= threshold && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        } else {
            mc.player.setSprinting(false);
        }
    }
}
