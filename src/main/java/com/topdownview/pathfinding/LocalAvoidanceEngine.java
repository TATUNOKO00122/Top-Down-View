package com.topdownview.pathfinding;

// import com.topdownview.Config;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class LocalAvoidanceEngine {
    
    private static final double AVOIDANCE_STRENGTH = 1.0;
    private static final double MIN_AVOIDANCE_DISTANCE = 0.5;
    private static final double AVOIDANCE_RADIUS = 2.0; // Hardcoded since config is disabled
    
    private LocalAvoidanceEngine() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    public static Vec3 calculateAvoidanceVector(Vec3 playerPos, Vec3 desiredVelocity, List<Entity> nearbyEntities) {
        // Pathfinding disabled
        // if (!Config.pathfindingEnabled || nearbyEntities.isEmpty()) {
        //     return desiredVelocity;
        // }
        return desiredVelocity;
        
        // double avoidanceRadius = Config.avoidanceRadius;
        // Vec3 avoidance = Vec3.ZERO;
        // int avoidanceCount = 0;
        // 
        // for (Entity entity : nearbyEntities) {
        //     Vec3 entityPos = entity.position();
        //     Vec3 toEntity = entityPos.subtract(playerPos);
        //     double distance = toEntity.length();
        //     
        //     if (distance < avoidanceRadius && distance > MIN_AVOIDANCE_DISTANCE) {
        //         double urgency = 1.0 - (distance / avoidanceRadius);
        //         urgency = urgency * urgency;
        //         
        //         Vec3 awayFromEntity = toEntity.normalize().scale(-urgency * AVOIDANCE_STRENGTH);
        //         avoidance = avoidance.add(awayFromEntity);
        //         avoidanceCount++;
        //     }
        // }
        // 
        // if (avoidanceCount > 0) {
        //     avoidance = avoidance.scale(1.0 / avoidanceCount);
        // }
        // 
        // double maxAvoidance = 0.5;
        // if (avoidance.length() > maxAvoidance) {
        //     avoidance = avoidance.normalize().scale(maxAvoidance);
        // }
        // 
        // Vec3 result = desiredVelocity.add(avoidance);
        // 
        // double maxSpeed = desiredVelocity.length();
        // if (result.length() > maxSpeed && maxSpeed > 0) {
        //     result = result.normalize().scale(maxSpeed);
        // }
        // 
        // return result;
    }
    
    public static Vec3 calculateAvoidanceVectorSimple(Vec3 playerPos, Vec3 targetPos, List<Entity> nearbyEntities) {
        // Pathfinding disabled - direct movement
        // if (!Config.pathfindingEnabled || nearbyEntities.isEmpty()) {
        //     return targetPos.subtract(playerPos).normalize();
        // }
        return targetPos.subtract(playerPos).normalize();
        
        // double avoidanceRadius = Config.avoidanceRadius;
        // Vec3 desiredDir = targetPos.subtract(playerPos).normalize();
        // Vec3 avoidance = Vec3.ZERO;
        // 
        // for (Entity entity : nearbyEntities) {
        //     Vec3 entityPos = entity.position();
        //     Vec3 toEntity = entityPos.subtract(playerPos);
        //     double distance = toEntity.length();
        //     
        //     if (distance < avoidanceRadius && distance > MIN_AVOIDANCE_DISTANCE) {
        //         Vec3 toEntityNorm = toEntity.normalize();
        //         double dot = desiredDir.dot(toEntityNorm);
        //         
        //         if (dot > 0) {
        //             double urgency = 1.0 - (distance / avoidanceRadius);
        //             urgency = urgency * urgency;
        //             
        //             Vec3 perpendicular = new Vec3(-toEntityNorm.z, 0, toEntityNorm.x);
        //             
        //             if (perpendicular.dot(desiredDir) < 0) {
        //                 perpendicular = perpendicular.scale(-1);
        //             }
        //             
        //             avoidance = avoidance.add(perpendicular.scale(urgency * 0.5));
        //         }
        //     }
        // }
        // 
        // Vec3 result = desiredDir.add(avoidance);
        // return result.length() > 0 ? result.normalize() : desiredDir;
    }
    
    public static boolean needsAvoidance(Vec3 playerPos, Vec3 moveDir, List<Entity> nearbyEntities) {
        // Pathfinding disabled
        // if (!Config.pathfindingEnabled || nearbyEntities.isEmpty()) {
        //     return false;
        // }
        return false;
        
        // double avoidanceRadius = Config.avoidanceRadius;
        // 
        // for (Entity entity : nearbyEntities) {
        //     Vec3 toEntity = entity.position().subtract(playerPos);
        //     double distance = toEntity.length();
        //     
        //     if (distance < avoidanceRadius) {
        //         if (moveDir.length() > 0) {
        //             double dot = moveDir.normalize().dot(toEntity.normalize());
        //             if (dot > 0.3) {
        //                 return true;
        //             }
        //         }
        //     }
        // }
        // 
        // return false;
    }
}
