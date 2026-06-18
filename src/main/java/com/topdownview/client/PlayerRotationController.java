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

    public static void onClientTick(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        if (!com.topdownview.Config.isHeadBodyRotationEnabled()) return;

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
            state.clearTargetPlacementYaw();
            return;
        }

        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            state.clearTargetPlacementYaw();
            return;
        }

        Vec3 playerEyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetPos = hitResult.getLocation();

        // ターゲットロック中：拡大ヒットボックスによるYずれを補正
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity hitEntity = entityHit.getEntity();
            if (ModState.TARGET_LOCK.isLockedTo(hitEntity)) {
                targetPos = hitEntity.getPosition(1.0f).add(0, hitEntity.getEyeHeight() * 0.8, 0);
            }
        }

        state.updateTargetHeadYaw(playerEyePos, targetPos);

        updatePlacementDirection(hitResult, targetPos, state);
    }

    /**
     * ブロックのクリック位置から方向性ブロック（階段・ハーフブロック等）の
     * 配置方向を計算し、targetPlacementYaw に反映する。
     *
     * ブロック中心→ヒット位置の水平ベクトルから最も近い4方位を求め、
     * その方向をプレイヤーのbody yawとして設定する。
     * アイテム使用中、このyawがplayer.getDirection()経由で
     * BlockPlaceContext.getHorizontalDirection()に伝わる。
     */
    private static void updatePlacementDirection(HitResult hitResult, Vec3 hitPos,
                                                  PlayerRotationState state) {
        if (!com.topdownview.Config.isClickPositionPlacementEnabled()) {
            state.clearTargetPlacementYaw();
            return;
        }

        if (!(hitResult instanceof BlockHitResult blockHit)) {
            state.clearTargetPlacementYaw();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isHoldingPlaceableBlock(mc.player)) {
            state.clearTargetPlacementYaw();
            return;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        double centerX = blockPos.getX() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;
        double dx = hitPos.x - centerX;
        double dz = hitPos.z - centerZ;

        double horizDistSqr = dx * dx + dz * dz;
        if (horizDistSqr < 0.01) {
            state.clearTargetPlacementYaw();
            return;
        }

        Direction dir = Direction.getNearest(dx, 0.0, dz);
        state.setTargetPlacementYaw(dir.toYRot());
    }

    private static boolean isHoldingPlaceableBlock(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.getItem() instanceof BlockItem
                || offHand.getItem() instanceof BlockItem;
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
            return;
        }

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