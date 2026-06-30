package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
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
        if (mc.player == null) {
            ClimbableHelper.setClimbingDirection(null);
            return;
        }

        if (mc.player.isPassenger()) {
            ClimbableHelper.setClimbingDirection(null);
            return;
        }

        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            ClimbableHelper.setClimbingDirection(null);
            return;
        }

        float originalForward = event.getInput().forwardImpulse;
        float originalStrafe = event.getInput().leftImpulse;
        boolean hasManualInput = Math.abs(originalForward) > 0.01f || Math.abs(originalStrafe) > 0.01f;

        // ハシゴ・ツタを登るべき壁の方向があるか判定
        net.minecraft.core.Direction climbDir = ClimbableHelper.calculateClimbingWallDirection(mc, originalForward, originalStrafe);
        ClimbableHelper.setClimbingDirection(climbDir);

        if (climbDir != null) {
            // ハシゴ登り中はキー入力を前進に集約してバニラの上昇を誘発
            float speed = (float) Math.sqrt(originalForward * originalForward + originalStrafe * originalStrafe);
            event.getInput().forwardImpulse = Math.min(speed, 1.0f);
            event.getInput().leftImpulse = 0.0f;
            return;
        }

        if (Config.isClickToMoveEnabled() && ModState.CLICK_TO_MOVE.isMoving()) {
            if (hasManualInput) {
                ClickToMoveController.stop();
                mc.player.setSprinting(false);
            } else {
                float[] moveInput = ClickToMoveController.calculateMovementInput(mc);
                if (moveInput != null) {
                    event.getInput().forwardImpulse = moveInput[0];
                    event.getInput().leftImpulse = moveInput[1];
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
