package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.state.PlayerRotationState;
import com.topdownview.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlayerRotationController {

    private static final float MIN_INPUT_THRESHOLD = 0.01f;
    private static final double MIN_VELOCITY_THRESHOLD = 0.001;
    // 水平距離がこれ未満だと atan2 が不定になり照準が暴れる
    private static final double MIN_AIM_HORIZONTAL_DISTANCE = 0.5;

    private PlayerRotationController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderTick(Minecraft mc, float partialTick) {
        if (!ModState.STATUS.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;
        if (!com.topdownview.Config.isHeadBodyRotationEnabled()) return;
        if (mc.player.isPassenger() || mc.player.isFallFlying()) return;
        if (ModState.CAMERA.isDragging() || ModState.CAMERA.isFreeCameraMode()) return;

        // 登るべき壁方向がある場合は、プレイヤーの向きを強制
        net.minecraft.core.Direction climbDir = ClimbableHelper.getClimbingDirection();
        if (climbDir != null) {
            float targetYaw = climbDir.toYRot();
            float targetPitch = 0.0f;

            PlayerRotationState state = ModState.PLAYER_ROTATION;
            state.updateTargetHeadYawDirect(targetYaw);
            state.updateTargetPitch(targetPitch);

            // 描画用の角度をプレイヤーに適用
            float headYaw = state.getLerpHeadYaw(partialTick);
            float bodyYaw = state.getLerpBodyYaw(partialTick);
            float pitch = targetPitch;

            mc.player.setYHeadRot(headYaw);
            mc.player.setYRot(state.getAimYaw());
            mc.player.setXRot(pitch);
            mc.player.xRotO = pitch;

            if (!state.isUsingItem()) {
                mc.player.setYBodyRot(bodyYaw);
            }
            return;
        }

        // 描画フレームの正確なカメラ位置とマウス方向で照準を更新
        PlayerRotationState state = ModState.PLAYER_ROTATION;
        if (!updateAimFromMouse(mc, state, partialTick)) return;

        // 描画用の角度をプレイヤーに適用
        float headYaw = state.getLerpHeadYaw(partialTick);
        float bodyYaw = state.getLerpBodyYaw(partialTick);
        float pitch = state.getTargetPitch();

        mc.player.setYHeadRot(headYaw);
        mc.player.setYRot(state.getAimYaw());
        mc.player.setXRot(pitch);
        // xRotO = pitch にして lerp(xRotO, xRot, partialTick) の補間ノイズを排除する
        mc.player.xRotO = pitch;

        if (!state.isUsingItem()) {
            mc.player.setYBodyRot(bodyYaw);
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        if (!com.topdownview.Config.isHeadBodyRotationEnabled()) return;

        if (mc.player.isPassenger() || mc.player.isFallFlying()) {
            return;
        }

        PlayerRotationState state = ModState.PLAYER_ROTATION;

        // 登るべき壁方向がある場合は、プレイヤーの向きを強制
        net.minecraft.core.Direction climbDir = ClimbableHelper.getClimbingDirection();
        if (climbDir != null) {
            float targetYaw = climbDir.toYRot();
            state.updateTargetHeadYawDirect(targetYaw);
            state.updateTargetPitch(0.0f);
            state.updateTargetBodyYaw(targetYaw, true);
            handleItemUsage(mc, state);
            state.tick();
            applyToPlayer(mc.player, state);
            return;
        }

        updateHeadRotationFromMouse(mc, state);
        updateBodyYawFromMovement(mc, state);
        handleItemUsage(mc, state);

        state.tick();

        applyToPlayer(mc.player, state);
    }

    private static void updateHeadRotationFromMouse(Minecraft mc, PlayerRotationState state) {
        if (ModState.CAMERA.isDragging() || ModState.CAMERA.isFreeCameraMode()) {
            return;
        }

        // ティック時点の正確なカメラ位置とマウス方向で照準を更新する
        updateAimFromMouse(mc, state, 1.0f);
    }

    /**
     * カーソル位置から照準を求めて state の目標ヨー/ピッチを更新する。
     * 水平距離が極小のときは atan2 が不定になり照準が暴れるため、更新せず false を返す。
     */
    private static boolean updateAimFromMouse(Minecraft mc, PlayerRotationState state, float partialTick) {
        MouseRaycast.INSTANCE.update(mc, partialTick, MouseRaycast.getCustomReachDistance());

        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        float targetYaw;
        float targetPitch;

        Vec3 targetPos = MouseRaycast.INSTANCE.getAimTarget(mc, partialTick, hitResult);
        if (targetPos != null) {
            Vec3 playerEyePos = mc.player.getEyePosition(partialTick);

            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist < MIN_AIM_HORIZONTAL_DISTANCE) {
                return false;
            }

            targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            targetPitch = Mth.clamp((float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI)), -90.0f, 90.0f);

            // 投射武器は重力補正した発射角を使う
            Float trajectoryPitch = TrajectoryCalculator.calculateTrajectoryPitch(mc.player, horizontalDist, dy);
            if (trajectoryPitch != null) {
                targetPitch = trajectoryPitch;
            }
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, partialTick);
            if (yawPitch == null) return false;
            targetYaw = yawPitch[0];
            targetPitch = Mth.clamp(yawPitch[1], -90.0f, 90.0f);
        }

        targetPitch = calculateWaterPitch(mc, targetPitch);
        state.updateTargetHeadYawDirect(targetYaw);
        state.updateTargetPitch(targetPitch);
        return true;
    }

    public static float calculateWaterPitch(Minecraft mc, float defaultPitch) {
        if (!com.topdownview.Config.isWaterMovementControlEnabled() || mc.player == null) {
            return defaultPitch;
        }

        boolean inDeepWater = mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || mc.player.isSwimming();
        if (inDeepWater) {
            if (mc.player.input != null && mc.player.input.shiftKeyDown) {
                return 55.0f;
            } else if (mc.player.input != null && mc.player.input.jumping) {
                return -55.0f;
            } else {
                return 0.0f;
            }
        }
        return defaultPitch;
    }

    private static void updateBodyYawFromMovement(Minecraft mc, PlayerRotationState state) {
        boolean inDeepWater = com.topdownview.Config.isWaterMovementControlEnabled() && mc.player != null && (
            mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || mc.player.isSwimming()
        );

        if (inDeepWater) {
            state.setBodyLerpSpeed(0.25f);
            state.setHeadLerpSpeed(0.25f);

            float rawForward = 0.0f;
            float rawStrafe = 0.0f;
            if (mc.options.keyUp.isDown()) rawForward += 1.0f;
            if (mc.options.keyDown.isDown()) rawForward -= 1.0f;
            if (mc.options.keyLeft.isDown()) rawStrafe += 1.0f;
            if (mc.options.keyRight.isDown()) rawStrafe -= 1.0f;

            boolean hasRawInput = Math.abs(rawForward) > 0.01f || Math.abs(rawStrafe) > 0.01f;

            if (!hasRawInput) {
                state.updateTargetBodyYaw(0.0f, false);
                return;
            }

            float cameraYaw = ModState.CAMERA.getYaw();
            float inputAngle = (float) Math.toDegrees(Math.atan2(-rawStrafe, rawForward));
            float movementYaw = MathUtil.normalizeAngle(cameraYaw + inputAngle);

            state.updateTargetBodyYaw(movementYaw, true);
            state.updateTargetHeadYawDirect(movementYaw);
            return;
        }

        state.resetLerpSpeeds();

        Input input = mc.player.input;
        float forward = input.forwardImpulse;
        float strafe = input.leftImpulse;

        boolean hasInput = Math.abs(forward) > MIN_INPUT_THRESHOLD
                || Math.abs(strafe) > MIN_INPUT_THRESHOLD;

        if (!hasInput) {
            state.updateTargetBodyYaw(0.0f, false);
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        float movementYaw;

        if (horizontalSpeed > MIN_VELOCITY_THRESHOLD) {
            movementYaw = (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0f;
        } else {
            float cameraYaw = ModState.CAMERA.getYaw();
            float inputAngle = (float) Math.toDegrees(Math.atan2(-strafe, forward));
            movementYaw = MathUtil.normalizeAngle(cameraYaw + inputAngle);
        }

        state.updateTargetBodyYaw(movementYaw, true);
    }

    private static void handleItemUsage(Minecraft mc, PlayerRotationState state) {
        boolean isUsingItem = mc.player.isUsingItem()
                || mc.options.keyUse.isDown()
                || mc.options.keyAttack.isDown();

        state.setUsingItem(isUsingItem);
    }

    private static void applyToPlayer(Player player, PlayerRotationState state) {
        if (state.isAttackRotationLocked()) {
            float headYaw = state.getLockedHeadYaw();
            player.setYRot(headYaw);
            player.yRotO = headYaw;
            player.setYHeadRot(headYaw);
            player.yHeadRotO = headYaw;
            player.setYBodyRot(state.getLockedBodyYaw());
            player.setXRot(state.getLockedPitch());
            player.xRotO = state.getLockedPitch();
            return;
        }

        float headYaw = state.getCurrentHeadYaw();
        float bodyYaw = state.getCurrentBodyYaw();
        float pitch = state.getCurrentPitch();

        player.setYHeadRot(headYaw);
        player.setYRot(state.getAimYaw());
        player.yHeadRotO = state.getPrevHeadYaw();
        player.yRotO = state.getAimYaw();
        player.setXRot(pitch);
        // xRotO = pitch にして onRenderTick との競合によるジッターを防止する
        player.xRotO = pitch;

        if (!state.isUsingItem()) {
            player.setYBodyRot(bodyYaw);
        }
    }

    public static void initializeFromPlayer(Player player) {
        if (player == null) return;
        ModState.PLAYER_ROTATION.initializeFromPlayer(player.getYHeadRot(), player.getYRot(), player.getXRot());
    }
}