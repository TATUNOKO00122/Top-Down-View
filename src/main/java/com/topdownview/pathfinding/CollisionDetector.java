package com.topdownview.pathfinding;

import com.topdownview.Config;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CollisionDetector {
    
    private CollisionDetector() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    public static List<Entity> getNearbyEntities(Minecraft mc, Vec3 playerPos, double radius) {
        List<Entity> result = new ArrayList<>();
        
        if (mc.level == null || mc.player == null) {
            return result;
        }
        
        Level level = mc.level;
        AABB searchBox = new AABB(
            playerPos.x - radius, playerPos.y - 1, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + 2, playerPos.z + radius
        );
        
        for (Entity entity : level.getEntities(mc.player, searchBox)) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;
            if (entity.isSpectator()) continue;
            
            double distance = entity.position().distanceTo(playerPos);
            if (distance <= radius) {
                result.add(entity);
            }
        }
        
        return result;
    }
    
    public static List<LivingEntity> getNearbyLivingEntities(Minecraft mc, Vec3 playerPos, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        
        if (mc.level == null || mc.player == null) {
            return result;
        }
        
        Level level = mc.level;
        AABB searchBox = new AABB(
            playerPos.x - radius, playerPos.y - 1, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + 2, playerPos.z + radius
        );
        
        for (Entity entity : level.getEntities(mc.player, searchBox)) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;
            if (entity.isSpectator()) continue;
            if (!(entity instanceof LivingEntity)) continue;
            
            double distance = entity.position().distanceTo(playerPos);
            if (distance <= radius) {
                result.add((LivingEntity) entity);
            }
        }
        
        return result;
    }
    
    public static List<Entity> getBlockingEntities(Minecraft mc, Vec3 playerPos, Vec3 moveDirection, double radius) {
        List<Entity> blocking = new ArrayList<>();
        
        List<Entity> nearby = getNearbyEntities(mc, playerPos, radius);
        
        if (moveDirection.lengthSqr() < 0.001) {
            return blocking;
        }
        
        Vec3 normalizedDir = moveDirection.normalize();
        
        for (Entity entity : nearby) {
            Vec3 toEntity = entity.position().subtract(playerPos);
            
            if (toEntity.length() < 1.0) {
                blocking.add(entity);
                continue;
            }
            
            Vec3 toEntityNorm = toEntity.normalize();
            double dot = normalizedDir.dot(toEntityNorm);
            
            if (dot > 0.5) {
                blocking.add(entity);
            }
        }
        
        return blocking;
    }
    
    public static Entity getClosestEntity(List<Entity> entities, Vec3 pos) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        
        for (Entity entity : entities) {
            double dist = entity.position().distanceToSqr(pos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        
        return closest;
    }
    
    public static boolean isEntityInPath(Vec3 from, Vec3 to, Entity entity, double margin) {
        Vec3 entityPos = entity.position();
        
        Vec3 pathDir = to.subtract(from);
        double pathLength = pathDir.length();
        if (pathLength < 0.001) return false;
        
        Vec3 pathNorm = pathDir.normalize();
        Vec3 toEntity = entityPos.subtract(from);
        
        double projection = toEntity.dot(pathNorm);
        
        if (projection < -margin || projection > pathLength + margin) {
            return false;
        }
        
        Vec3 closestPoint = from.add(pathNorm.scale(Math.max(0, Math.min(pathLength, projection))));
        double perpendicularDist = entityPos.distanceTo(closestPoint);
        
        double entityRadius = entity.getBbWidth() / 2.0;
        return perpendicularDist < entityRadius + margin;
    }
}
