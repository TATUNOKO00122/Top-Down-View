package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class ClickToMoveController {

    private static final double STOP_THRESHOLD_SQ = 0.5 * 0.5;
    private static long lastAttackTick = -1;

    private ClickToMoveController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) return;
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ClickActionHandler.onClientTick(mc);

        if (!Config.clickToMoveEnabled) return;
        if (!ModState.CLICK_TO_MOVE.isMoving()) return;

        ModState.CLICK_TO_MOVE.updateEntityTargetPosition();

        if (ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            updateLongPressFollow(mc);
        } else if (ModState.CLICK_TO_MOVE.getTargetEntity() != null) {
            checkEntityTarget(mc);
        } else {
            checkArrival(mc);
        }
    }

    public static void setDestination(Vec3 targetPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ModState.CLICK_TO_MOVE.startMoveTo(targetPos, mc.player.position());
    }

    public static void setTargetEntity(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ModState.CLICK_TO_MOVE.startFollowEntity(entity, mc.player.position());
    }

    public static void startLongPressFollow() {
        ModState.CLICK_TO_MOVE.setLongPressFollow(true);
    }

    public static void stopLongPressFollow() {
        ModState.CLICK_TO_MOVE.setLongPressFollow(false);
    }

    public static void stop() {
        ModState.CLICK_TO_MOVE.stopMovement();
    }

    public static void reset() {
        ModState.CLICK_TO_MOVE.reset();
    }

    public static float[] calculateMovementInput(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.isMoving()) return null;
        if (mc.player == null) return null;

        Vec3 destination = getEffectiveDestination(mc);
        if (destination == null) return null;

        Vec3 playerPos = mc.player.position();
        Vec3 direction = destination.subtract(playerPos);

        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDist < STOP_THRESHOLD_SQ) return null;

        double moveAngle = Math.atan2(-direction.x, direction.z);
        float playerYaw = mc.player.getYRot();
        float relativeAngle = (float) Math.toDegrees(moveAngle) - playerYaw;

        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;

        float rad = (float) Math.toRadians(relativeAngle);
        float forward = Mth.cos(rad);
        float strafe = -Mth.sin(rad);

        float length = Mth.sqrt(forward * forward + strafe * strafe);
        if (length > 0) {
            forward /= length;
            strafe /= length;
        }

        return new float[]{forward, strafe};
    }

    private static Vec3 getEffectiveDestination(Minecraft mc) {
        if (ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            double reach = MouseRaycast.getCustomReachDistance();
            MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
            var hitResult = MouseRaycast.INSTANCE.getLastHitResult();
            if (hitResult != null && hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return hitResult.getLocation();
            }
            return null;
        }

        return ModState.CLICK_TO_MOVE.getTargetPosition();
    }

    private static void updateLongPressFollow(Minecraft mc) {
        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        var hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        if (hitResult == null || hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return;
        }

        ModState.CLICK_TO_MOVE.setTargetPosition(hitResult.getLocation());
    }

    private static void checkArrival(Minecraft mc) {
        if (mc.player == null) return;

        double threshold = Config.arrivalThreshold;
        if (ModState.CLICK_TO_MOVE.hasArrived(mc.player.position(), threshold)) {
            stop();
        }
    }

    private static void checkEntityTarget(Minecraft mc) {
        if (mc.player == null) return;

        Entity targetEntity = ModState.CLICK_TO_MOVE.getTargetEntity();
        if (targetEntity == null || !targetEntity.isAlive()) {
            stop();
            return;
        }

        ModState.CLICK_TO_MOVE.setTargetPosition(targetEntity.position());

        double attackRange = Config.attackRange;
        if (ModState.CLICK_TO_MOVE.hasArrivedAtEntity(mc.player.position(), attackRange)) {
            tryAttackEntity(mc, targetEntity);
        }
    }

    private static void tryAttackEntity(Minecraft mc, Entity target) {
        if (mc.level == null) return;

        long currentTick = mc.level.getGameTime();
        if (currentTick == lastAttackTick) return;

        lastAttackTick = currentTick;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (!target.isAlive()) {
            stop();
        }
    }
}
