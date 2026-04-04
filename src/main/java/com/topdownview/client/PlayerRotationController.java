package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.state.PlayerRotationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlayerRotationController {

    private static final float MIN_INPUT_THRESHOLD = 0.01f;
    private static final double MIN_VELOCITY_THRESHOLD = 0.001;

    private PlayerRotationController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        if (mc.player.isPassenger() || mc.player.isFallFlying()) {
            return;
        }

        PlayerRotationState state = ModState.PLAYER_ROTATION;

        updateHeadYawFromMouse(mc, state);
        updateBodyYawFromMovement(mc, state);
        handleItemUsage(mc, state);

        state.tick();

        applyToPlayer(mc.player, state);
    }

    private static void updateHeadYawFromMouse(Minecraft mc, PlayerRotationState state) {
        if (ModState.CAMERA.isDragging() || ModState.CAMERA.isFreeCameraMode()) {
            return;
        }

        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        Vec3 playerEyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetPos = hitResult.getLocation();

        state.updateTargetHeadYaw(playerEyePos, targetPos);
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
        float headYaw = state.getCurrentHeadYaw();
        float bodyYaw = state.getCurrentBodyYaw();

        player.setYHeadRot(headYaw);
        player.setYRot(bodyYaw);
        player.yHeadRotO = state.getPrevHeadYaw();
        player.yRotO = state.getPrevBodyYaw();

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
        ModState.PLAYER_ROTATION.initializeFromPlayer(player.getYHeadRot(), player.getYRot());
    }
}