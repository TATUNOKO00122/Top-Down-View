package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * トップダウン視点用のマウスレイキャスト処理
 * カメラは固定角度（ピッチ60度、ヨー0度）であることを前提とする
 */
public class MouseRaycast {

    public static double getCustomReachDistance() {
        return Math.max(30.0, ClientForgeEvents.cameraDistance * 1.5);
    }

    private static BlockHitResult lastBlockHit = null;
    private static EntityHitResult lastEntityHit = null;
    private static net.minecraft.world.phys.HitResult lastHitResult = null;

    public static void update(Minecraft mc, float partialTick, double reachDistance) {
        if (mc.level == null || mc.player == null) {
            lastBlockHit = null;
            lastEntityHit = null;
            lastHitResult = null;
            return;
        }

        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        // Block Hit
        lastBlockHit = mc.level
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double blockDist = lastBlockHit.getLocation().distanceToSqr(start);

        // Entity Hit
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity != null) {
            Vec3 entitySearchEnd = lastBlockHit.getLocation();
            if (lastBlockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                entitySearchEnd = end;
                blockDist = reachDistance * reachDistance;
            }

            AABB searchBox = new AABB(start, entitySearchEnd).inflate(1.0D);
            lastEntityHit = ProjectileUtil.getEntityHitResult(
                    mc.level,
                    cameraEntity,
                    start,
                    entitySearchEnd,
                    searchBox,
                    (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);
        } else {
            lastEntityHit = null;
        }

        if (lastEntityHit != null) {
            double entityDist = start.distanceToSqr(lastEntityHit.getLocation());
            if (entityDist < blockDist) {
                lastHitResult = lastEntityHit;
            } else {
                lastHitResult = lastBlockHit;
            }
        } else {
            lastHitResult = lastBlockHit;
        }
    }

    public static net.minecraft.world.phys.HitResult getLastHitResult() {
        return lastHitResult;
    }

    public static BlockHitResult getLastBlockHit() {
        return lastBlockHit;
    }

    public static EntityHitResult getLastEntityHit() {
        return lastEntityHit;
    }

    public static BlockHitResult rayTraceBlock(Minecraft mc, float partialTick, double reachDistance) {
        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }
        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    public static EntityHitResult rayTraceEntity(Minecraft mc, float partialTick, double reachDistance) {
        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }

        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null)
            return null;

        AABB searchBox = new AABB(start, end).inflate(1.0D);

        return ProjectileUtil.getEntityHitResult(
                mc.level,
                cameraEntity,
                start,
                end,
                searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);
    }

    public static net.minecraft.world.phys.HitResult getHitResult(Minecraft mc, float partialTick,
            double reachDistance) {
        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }

        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        BlockHitResult blockHit = mc.level
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double blockDist = blockHit.getLocation().distanceToSqr(start);

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null)
            return blockHit;

        Vec3 entitySearchEnd = blockHit.getLocation();
        if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            entitySearchEnd = end;
            blockDist = reachDistance * reachDistance;
        }

        AABB searchBox = new AABB(start, entitySearchEnd).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.level,
                cameraEntity,
                start,
                entitySearchEnd,
                searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);

        if (entityHit != null) {
            double entityDist = start.distanceToSqr(entityHit.getLocation());
            if (entityDist < blockDist) {
                return entityHit;
            }
        }

        return blockHit;
    }

    private static Vec3 getCameraPosition(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    private static Vec3 getMouseRayDirection(Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        double ndcX = -((2.0 * mouseX / screenWidth) - 1.0);
        double ndcY = ((2.0 * mouseY / screenHeight) - 1.0);

        double fov = mc.options.fov().get();
        double aspectRatio = (double) screenWidth / screenHeight;
        double fovRad = Math.toRadians(fov);
        double tanHalfFov = Math.tan(fovRad / 2.0);

        double offsetX = ndcX * tanHalfFov * aspectRatio;
        double offsetY = ndcY * tanHalfFov;

        double pitchDeg = 60.0;
        double yawDeg = 0.0;
        double pitch = Math.toRadians(pitchDeg);
        double yaw = Math.toRadians(yawDeg);

        double forwardX = -Math.sin(yaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * Math.cos(pitch);

        double rightX = Math.cos(yaw);
        double rightY = 0;
        double rightZ = Math.sin(yaw);

        double upX = -Math.sin(yaw) * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = Math.cos(yaw) * Math.sin(pitch);

        double dirX = forwardX + offsetX * rightX - offsetY * upX;
        double dirY = forwardY + offsetX * rightY - offsetY * upY;
        double dirZ = forwardZ + offsetX * rightZ - offsetY * upZ;

        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0) {
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }

        return new Vec3(dirX, dirY, dirZ);
    }
}
