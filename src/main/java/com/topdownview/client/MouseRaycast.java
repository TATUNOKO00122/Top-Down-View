package com.topdownview.client;

import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import com.topdownview.state.CameraState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * トップダウン視点用のマウスレイキャスト処理
 * 使用パターン：
 *   MouseRaycast.INSTANCE.update(mc, partialTick, reachDistance);
 *   HitResult result = MouseRaycast.INSTANCE.getLastHitResult();
 */
public final class MouseRaycast {

    private record RaycastResult(Vec3 start, Vec3 end, Vec3 direction, BlockHitResult blockHit) {}

    private static final double RAYCAST_STEP = 0.5;
    private static final double MIN_RAYCAST_STEP = 0.25;
    private static final double MAX_RAYCAST_STEP = 1.0;
    private static final double FAR_DISTANCE_THRESHOLD = 50.0;
    private static final double MEDIUM_DISTANCE_THRESHOLD = 20.0;
    private static final double FIXED_PITCH_DEG = 45.0;
    private static final double SCREEN_TO_NDC_FACTOR = 2.0;
    private static final double NDC_OFFSET = 1.0;
    private static final double MIN_SCREEN_DIMENSION = 1.0;
    private static final double MIN_REACH_DISTANCE = 30.0;
    private static final double REACH_DISTANCE_MULTIPLIER = 1.5;
    private static final double LINE_PROXIMITY_THRESHOLD = 2.0;

    public static final MouseRaycast INSTANCE = new MouseRaycast();

    private BlockHitResult lastBlockHit = null;
    private EntityHitResult lastEntityHit = null;
    private net.minecraft.world.phys.HitResult lastHitResult = null;

    private Vec3 cachedDirection = null;
    private double lastFov = -1;
    private double lastAspectRatio = -1;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private MouseRaycast() {}

    public static double getCustomReachDistance() {
        double cameraDistance = CameraState.INSTANCE.getCameraDistance();
        return Math.max(MIN_REACH_DISTANCE, cameraDistance * REACH_DISTANCE_MULTIPLIER);
    }

    public void update(Minecraft mc, float partialTick, double reachDistance) {
        if (mc == null || mc.level == null || mc.player == null) {
            clearResults();
            return;
        }

        RaycastResult result = performRaycast(mc, reachDistance);
        if (result == null) {
            clearResults();
            return;
        }

        lastBlockHit = result.blockHit;
        lastEntityHit = performEntityRaycast(mc, result.start, result.blockHit, result.end);

        if (lastEntityHit != null) {
            double blockDist = lastBlockHit.getLocation().distanceToSqr(result.start);
            double entityDist = result.start.distanceToSqr(lastEntityHit.getLocation());
            lastHitResult = (entityDist < blockDist) ? lastEntityHit : lastBlockHit;
        } else {
            lastHitResult = lastBlockHit;
        }
    }

    private RaycastResult performRaycast(Minecraft mc, double reachDistance) {
        Vec3 start = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = getMouseRayDirection(mc);
        if (direction == null) return null;

        Vec3 end = start.add(direction.scale(reachDistance));
        BlockHitResult blockHit = rayTraceBlocks(mc, start, end);
        if (blockHit == null) return null;

        return new RaycastResult(start, end, direction, blockHit);
    }

    private EntityHitResult performEntityRaycast(Minecraft mc, Vec3 start, BlockHitResult blockHit, Vec3 end) {
        if (mc.player == null) return null;

        Vec3 mousePos = calculateGroundIntersection(mc, start, end);
        if (mousePos == null) {
            mousePos = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : blockHit.getLocation();
        }

        Vec3 playerEyePos = mc.player.getEyePosition(1.0f);
        AABB searchBox = new AABB(mousePos, mousePos).inflate(LINE_PROXIMITY_THRESHOLD);

        var entities = mc.level.getEntities(mc.player, searchBox,
                (entity) -> entity != null && !entity.isSpectator() && entity.isPickable() && entity != mc.player);

        Entity closestEntity = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Vec3 closestHitPoint = null;

        for (Entity entity : entities) {
            double dx = entity.getBoundingBox().getCenter().x - mousePos.x;
            double dz = entity.getBoundingBox().getCenter().z - mousePos.z;
            if (Math.sqrt(dx * dx + dz * dz) > LINE_PROXIMITY_THRESHOLD) continue;

            if (!hasLineOfSight(mc, playerEyePos, entity)) continue;

            double distToPlayerSq = entity.distanceToSqr(playerEyePos);
            if (distToPlayerSq < closestDistanceSq) {
                closestDistanceSq = distToPlayerSq;
                closestEntity = entity;
                closestHitPoint = entity.getBoundingBox().getCenter();
            }
        }

        return closestEntity != null ? new EntityHitResult(closestEntity, closestHitPoint) : null;
    }

    private Vec3 calculateGroundIntersection(Minecraft mc, Vec3 start, Vec3 end) {
        if (mc.player == null) return null;

        double targetY = mc.player.getEyePosition(1.0f).y - 1.0;
        Vec3 direction = end.subtract(start);
        double dy = direction.y;

        if (Math.abs(dy) < 0.001) return null;

        double t = (targetY - start.y) / dy;
        if (t < 0) return null;

        return new Vec3(start.x + t * direction.x, targetY, start.z + t * direction.z);
    }

    private boolean hasLineOfSight(Minecraft mc, Vec3 fromPos, Entity target) {
        Vec3 targetPos = target.getBoundingBox().getCenter();
        BlockHitResult blockHit = mc.level.clip(new ClipContext(fromPos, targetPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private BlockHitResult rayTraceBlocks(Minecraft mc, Vec3 start, Vec3 end) {
        if (!ModState.STATUS.isEnabled()) {
            return mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        }

        double distance = start.distanceTo(end);
        if (distance <= 0 || !Double.isFinite(distance)) {
            return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        }

        Vec3 direction = end.subtract(start).normalize();
        double step = calculateAdaptiveStep(distance);
        BlockPos lastCheckedPos = null;

        for (double d = 0; d < distance; d += step) {
            Vec3 currentPos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);

            if (blockPos.equals(lastCheckedPos)) continue;
            lastCheckedPos = blockPos;

            if (TopDownCuller.getInstance().isBlockCulled(blockPos, mc.level)) continue;

            BlockState state = mc.level.getBlockState(blockPos);
            if (!state.isAir()) {
                var shape = state.getShape(mc.level, blockPos, CollisionContext.of(mc.player));
                if (!shape.isEmpty()) {
                    var clipResult = shape.clip(start, end, blockPos);
                    if (clipResult != null) return clipResult;
                }
            }
        }

        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }

    private double calculateAdaptiveStep(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return MIN_RAYCAST_STEP;
        if (distance > FAR_DISTANCE_THRESHOLD) return MAX_RAYCAST_STEP;
        if (distance > MEDIUM_DISTANCE_THRESHOLD) return RAYCAST_STEP;
        return MIN_RAYCAST_STEP;
    }

    public net.minecraft.world.phys.HitResult getLastHitResult() { return lastHitResult; }
    public BlockHitResult getLastBlockHit() { return lastBlockHit; }
    public EntityHitResult getLastEntityHit() { return lastEntityHit; }

    private Vec3 getMouseRayDirection(Minecraft mc) {
        if (mc.mouseHandler == null || mc.options == null || mc.getWindow() == null) return null;

        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        if (screenWidth < MIN_SCREEN_DIMENSION || screenHeight < MIN_SCREEN_DIMENSION) return null;

        double fov = mc.options.fov().get();
        double aspectRatio = screenWidth / screenHeight;

        if (cachedDirection != null && mouseX == lastMouseX && mouseY == lastMouseY
                && fov == lastFov && aspectRatio == lastAspectRatio) {
            return cachedDirection;
        }

        double ndcX = -((SCREEN_TO_NDC_FACTOR * mouseX / screenWidth) - NDC_OFFSET);
        double ndcY = ((SCREEN_TO_NDC_FACTOR * mouseY / screenHeight) - NDC_OFFSET);
        double fovRad = Math.toRadians(fov);
        double tanHalfFov = Math.tan(fovRad / SCREEN_TO_NDC_FACTOR);
        double offsetX = ndcX * tanHalfFov * aspectRatio;
        double offsetY = ndcY * tanHalfFov;

        double pitchRad = Math.toRadians(FIXED_PITCH_DEG);
        double yawRad = Math.toRadians(ModState.CAMERA.getYaw());

        double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double forwardY = -Math.sin(pitchRad);
        double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double upX = -Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

        double dirX = forwardX + offsetX * rightX - offsetY * upX;
        double dirY = forwardY - offsetY * upY;
        double dirZ = forwardZ + offsetX * rightZ - offsetY * upZ;

        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0) { dirX /= length; dirY /= length; dirZ /= length; }

        cachedDirection = new Vec3(dirX, dirY, dirZ);
        lastMouseX = mouseX; lastMouseY = mouseY; lastFov = fov; lastAspectRatio = aspectRatio;

        return cachedDirection;
    }

    private void clearResults() {
        lastBlockHit = null;
        lastEntityHit = null;
        lastHitResult = null;
    }

    public void clearCache() {
        cachedDirection = null;
        lastFov = -1; lastAspectRatio = -1; lastMouseX = -1; lastMouseY = -1;
    }
}
