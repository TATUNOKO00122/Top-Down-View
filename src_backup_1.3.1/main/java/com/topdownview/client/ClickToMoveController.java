package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.baritone.BaritoneIntegration;
// import com.topdownview.pathfinding.CollisionDetector;
// import com.topdownview.pathfinding.LocalAvoidanceEngine;
// import com.topdownview.pathfinding.Path;
// import com.topdownview.pathfinding.PathfindingEngine;
import com.topdownview.state.ModState;
import com.mojang.logging.LogUtils;
// import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class ClickToMoveController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double WAYPOINT_THRESHOLD = 1.0;
    private static final double STOP_THRESHOLD = 0.5;

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

        // ModState.PATHFINDING.tickCooldown();

        if (!Config.clickToMoveEnabled) return;
        if (!ModState.CLICK_TO_MOVE.isMoving()) return;

        ModState.CLICK_TO_MOVE.updateEntityTargetPosition();
        ModState.CLICK_TO_MOVE.tickBaritone();

        // updateNearbyEntities(mc);

        if (ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            updateLongPressFollow(mc);
        } else if (ModState.CLICK_TO_MOVE.getTargetEntity() != null) {
            checkEntityTarget(mc);
        } else if (ModState.CLICK_TO_MOVE.useBaritone()) {
            checkBaritoneArrival(mc);
        } else {
            checkArrival(mc);
        }
    }
    
    private static void checkBaritoneArrival(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.canCheckBaritoneArrival()) {
            return;
        }
        if (!BaritoneIntegration.isPathing()) {
            LOGGER.info("[ClickToMove] Baritone経路完了");
            stop();
        }
    }

    public static void setDestination(Vec3 targetPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LOGGER.info("[ClickToMove] 目的地設定: {}", BlockPos.containing(targetPos));
        ModState.CLICK_TO_MOVE.startMoveTo(targetPos, mc.player.position());

        // Baritone使用時
        if (BaritoneIntegration.isBaritoneAvailable()) {
            LOGGER.info("[ClickToMove] Baritone経路探索使用");
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathTo(targetPos);
            return;
        }

        // Baritoneなし - 直線移動のみ
        LOGGER.info("[ClickToMove] 直線移動モード");
        ModState.CLICK_TO_MOVE.setUseBaritone(false);
    }

    public static void setTargetEntity(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(entity instanceof LivingEntity)) return;
        if (entity instanceof Player) return;
        ModState.CLICK_TO_MOVE.startFollowEntity(entity, mc.player.position());
        
        if (BaritoneIntegration.isBaritoneAvailable()) {
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathToEntity(entity);
        }
    }

    public static void startLongPressFollow() {
        ModState.CLICK_TO_MOVE.setLongPressFollow(true);
        // ModState.PATHFINDING.clearPath();
    }

    public static void stopLongPressFollow() {
        ModState.CLICK_TO_MOVE.setLongPressFollow(false);
    }

    public static void stop() {
        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            BaritoneIntegration.stop();
        }
        ModState.CLICK_TO_MOVE.stopMovement();
    }

    public static void reset() {
        BaritoneIntegration.stop();
        ModState.CLICK_TO_MOVE.reset();
    }

    public static float[] calculateMovementInput(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.isMoving()) return null;
        if (mc.player == null) return null;
        
        // Baritone使用時はBaritoneが移動を制御
        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            return null;
        }

        Vec3 destination = getEffectiveDestination(mc);
        if (destination == null) return null;

        Vec3 playerPos = mc.player.position();
        Vec3 direction = calculateDirection(playerPos, destination);

        if (direction == null) return null;

        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDist < STOP_THRESHOLD) return null;

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

    private static Vec3 calculateDirection(Vec3 playerPos, Vec3 destination) {
        Vec3 direction = destination.subtract(playerPos);
        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDist < 0.1) return null;
        return direction.normalize();
    }

    // private static Vec3 calculateDirectionWithAvoidance(Minecraft mc, Vec3 playerPos, Vec3 finalDestination) {
    //     Vec3 targetWaypoint = getTargetWaypoint(mc, playerPos, finalDestination);
    //     if (targetWaypoint == null) return null;
    // 
    //     Vec3 baseDirection = targetWaypoint.subtract(playerPos);
    //     double horizontalDist = Math.sqrt(baseDirection.x * baseDirection.x + baseDirection.z * baseDirection.z);
    //     if (horizontalDist < 0.1) return null;
    // 
    //     baseDirection = baseDirection.normalize();
    // 
    //     if (Config.pathfindingEnabled) {
    //         List<Entity> nearbyEntities = ModState.PATHFINDING.getNearbyEntities();
    //         if (!nearbyEntities.isEmpty()) {
    //             Vec3 avoidanceVec = LocalAvoidanceEngine.calculateAvoidanceVectorSimple(
    //                 playerPos,
    //                 targetWaypoint,
    //                 nearbyEntities
    //             );
    // 
    //             Vec3 adjustedDir = baseDirection.scale(0.7).add(avoidanceVec.scale(0.3));
    //             return adjustedDir.normalize();
    //         }
    //     }
    // 
    //     return baseDirection;
    // }

    // private static Vec3 getTargetWaypoint(Minecraft mc, Vec3 playerPos, Vec3 finalDestination) {
    //     if (ModState.CLICK_TO_MOVE.isLongPressFollow()) {
    //         return finalDestination;
    //     }
    // 
    //     if (ModState.CLICK_TO_MOVE.getTargetEntity() != null) {
    //         return finalDestination;
    //     }
    // 
    //     Path path = ModState.PATHFINDING.getCurrentPath();
    //     if (path != null && !path.isEmpty() && !path.isFinished()) {
    //         Vec3 currentWaypoint = path.getCurrentNodePosition();
    //         if (currentWaypoint != null) {
    //             double distToWaypoint = playerPos.distanceTo(currentWaypoint);
    // 
    //             if (distToWaypoint < WAYPOINT_THRESHOLD) {
    //                 int prevIndex = path.getCurrentNodeIndex();
    //                 path.advance();
    //                 Vec3 nextWaypoint = path.getCurrentNodePosition();
    //                 if (nextWaypoint != null) {
    //                     LOGGER.debug("[ClickToMove] ウェイポイント進行: {} -> {}", prevIndex, path.getCurrentNodeIndex());
    //                     return nextWaypoint;
    //                 }
    //             } else {
    //                 return currentWaypoint;
    //             }
    //         }
    // 
    //         if (path.isFinished()) {
    //             LOGGER.info("[ClickToMove] パス完了");
    //             ModState.PATHFINDING.clearPath();
    //         }
    //     }
    // 
    //     return finalDestination;
    // }

    // private static void updateNearbyEntities(Minecraft mc) {
    //     if (mc.player == null) return;
    // 
    //     List<Entity> nearby = CollisionDetector.getNearbyEntities(
    //         mc,
    //         mc.player.position(),
    //         Config.avoidanceRadius + 1.0
    //     );
    //     ModState.PATHFINDING.setNearbyEntities(nearby);
    // }

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

        // プレイヤーの装備に基づく動的射程を取得
        double attackRange = ModState.TARGET_HIGHLIGHT.getAttackRange(mc.player);
        if (ModState.CLICK_TO_MOVE.hasArrivedAtEntity(mc.player.position(), attackRange)) {
            tryAttackEntity(mc, targetEntity);
        }
    }

    private static void tryAttackEntity(Minecraft mc, Entity target) {
        if (mc.level == null) return;
        if (!(target instanceof Enemy)) {
            stop();
            return;
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        stop();
    }
}
