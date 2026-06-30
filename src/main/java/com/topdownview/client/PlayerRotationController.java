package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.state.PlayerRotationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlayerRotationController {

    private static final float MIN_INPUT_THRESHOLD = 0.01f;
    private static final double MIN_VELOCITY_THRESHOLD = 0.001;

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
            mc.player.setYRot(bodyYaw);
            mc.player.setXRot(pitch);
            mc.player.xRotO = pitch;

            if (!state.isUsingItem()) {
                mc.player.setYBodyRot(bodyYaw);
            }
            return;
        }

        // 描画フレームの正確なカメラ位置とマウス方向でレイキャストを更新
        MouseRaycast.INSTANCE.update(mc, partialTick, MouseRaycast.getCustomReachDistance());

        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        float targetYaw;
        float targetPitch;

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 playerEyePos = mc.player.getEyePosition(partialTick);
            Vec3 targetPos = hitResult.getLocation();

            if (hitResult instanceof EntityHitResult entityHit) {
                Entity hitEntity = entityHit.getEntity();
                if (ModState.TARGET_LOCK.isLockedTo(hitEntity)) {
                    targetPos = hitEntity.getPosition(partialTick).add(0, hitEntity.getEyeHeight() * 0.8, 0);
                }
            }

            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            targetPitch = Mth.clamp((float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI)), -90.0f, 90.0f);
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, partialTick);
            if (yawPitch == null) return;
            targetYaw = yawPitch[0];
            targetPitch = Mth.clamp(yawPitch[1], -90.0f, 90.0f);
        }

        // 目標値を更新
        PlayerRotationState state = ModState.PLAYER_ROTATION;
        state.updateTargetHeadYawDirect(targetYaw);
        state.updateTargetPitch(targetPitch);

        // 描画用の角度をプレイヤーに適用
        float headYaw = state.getLerpHeadYaw(partialTick);
        float bodyYaw = state.getLerpBodyYaw(partialTick);
        float pitch = targetPitch;

        mc.player.setYHeadRot(headYaw);
        mc.player.setYRot(bodyYaw);
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

        // ティック時点の正確なカメラ位置とマウス方向でレイキャストを更新する
        MouseRaycast.INSTANCE.update(mc, 1.0f, MouseRaycast.getCustomReachDistance());

        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        float targetYaw;
        float targetPitch;

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 playerEyePos = mc.player.getEyePosition(1.0f);
            Vec3 targetPos = hitResult.getLocation();

            if (hitResult instanceof EntityHitResult entityHit) {
                Entity hitEntity = entityHit.getEntity();
                if (ModState.TARGET_LOCK.isLockedTo(hitEntity)) {
                    targetPos = hitEntity.getPosition(1.0f).add(0, hitEntity.getEyeHeight() * 0.8, 0);
                }
            }

            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            targetPitch = Mth.clamp((float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI)), -90.0f, 90.0f);
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, 1.0f);
            if (yawPitch == null) return;
            targetYaw = yawPitch[0];
            targetPitch = Mth.clamp(yawPitch[1], -90.0f, 90.0f);
        }

        state.updateTargetHeadYawDirect(targetYaw);
        state.updateTargetPitch(targetPitch);
    }

    private static void updateBodyYawFromMovement(Minecraft mc, PlayerRotationState state) {
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
            movementYaw = normalizeAngle(cameraYaw + inputAngle);
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
            float yaw = state.getLockedBodyYaw();
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setYHeadRot(state.getLockedHeadYaw());
            player.yHeadRotO = state.getLockedHeadYaw();
            player.setYBodyRot(yaw);
            player.setXRot(state.getLockedPitch());
            player.xRotO = state.getLockedPitch();
            return;
        }

        float headYaw = state.getCurrentHeadYaw();
        float bodyYaw = state.getCurrentBodyYaw();
        float pitch = state.getCurrentPitch();

        player.setYHeadRot(headYaw);
        player.setYRot(bodyYaw);
        player.yHeadRotO = state.getPrevHeadYaw();
        player.yRotO = state.getPrevBodyYaw();
        player.setXRot(pitch);
        // xRotO = pitch にして onRenderTick との競合によるジッターを防止する
        player.xRotO = pitch;

        if (!state.isUsingItem()) {
            player.setYBodyRot(bodyYaw);
        }
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 0.0f;
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    public static void initializeFromPlayer(Player player) {
        if (player == null) return;
        ModState.PLAYER_ROTATION.initializeFromPlayer(player.getYHeadRot(), player.getYRot(), player.getXRot());
    }
}