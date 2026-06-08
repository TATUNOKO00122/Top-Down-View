package com.topdownview.client;

import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import com.topdownview.state.TargetLockState;
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

import com.topdownview.Config;

import static com.topdownview.state.ModState.CAMERA;

/**
 * トップダウン視点用のマウスレイキャスト処理
 * 使用パターン：
 * MouseRaycast.INSTANCE.update(mc, partialTick, reachDistance);
 * HitResult result = MouseRaycast.INSTANCE.getLastHitResult();
 */
public final class MouseRaycast {

    private record RaycastResult(Vec3 start, Vec3 end, Vec3 direction, BlockHitResult blockHit) {
    }

    private static final double SCREEN_TO_NDC_FACTOR = 2.0;
    private static final double NDC_OFFSET = 1.0;
    private static final double MIN_SCREEN_DIMENSION = 1.0;
    private static final double REACH_DISTANCE = 512.0;
    

    public static final MouseRaycast INSTANCE = new MouseRaycast();

    private static final double EPSILON = 1e-6;

    private BlockHitResult lastBlockHit = null;
    private EntityHitResult lastEntityHit = null;
    private net.minecraft.world.phys.HitResult lastHitResult = null;

    private Vec3 cachedDirection = null;
    private double lastFov = -1;
    private double lastAspectRatio = -1;
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    private double lastYaw = -1;
    private double lastPitch = -1;

    private long lastUpdateGameTime = -1;
    private float lastPartialTick = -1;

    private MouseRaycast() {
    }

    public static double getCustomReachDistance() {
        return REACH_DISTANCE;
    }

public void update(Minecraft mc, float partialTick, double reachDistance) {
        // フリーカム中またはドラッグ回転中はレイキャストを停止
        if (ModState.CAMERA.isFreeCameraMode() || ModState.CAMERA.isDragging()) {
            clearResults();
            return;
        }
        
        if (mc == null || mc.level == null || mc.player == null) {
            clearResults();
            return;
        }

        long currentGameTime = mc.level.getGameTime();
        if (currentGameTime == lastUpdateGameTime && partialTick == lastPartialTick) {
            return;
        }
        lastUpdateGameTime = currentGameTime;
        lastPartialTick = partialTick;

        RaycastResult result = performRaycast(mc, reachDistance, partialTick);
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

    private RaycastResult performRaycast(Minecraft mc, double reachDistance, float partialTick) {
        Vec3 start = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = getMouseRayDirection(mc, partialTick);
        if (direction == null)
            return null;

        Vec3 end = start.add(direction.scale(reachDistance));
        BlockHitResult blockHit = rayTraceBlocks(mc, start, end);
        if (blockHit == null)
            return null;

        return new RaycastResult(start, end, direction, blockHit);
    }

    private EntityHitResult performEntityRaycast(Minecraft mc, Vec3 start, BlockHitResult blockHit, Vec3 end) {
        if (mc.player == null)
            return null;

        Vec3 direction = end.subtract(start).normalize();
        double maxDistance = start.distanceTo(end);

        AABB searchBox = new AABB(start, end).inflate(2.0);

        var entities = mc.level.getEntities(mc.player, searchBox,
                (entity) -> entity != null && !entity.isSpectator() && entity.isPickable() && entity != mc.player);

        Entity closestEntity = null;
        double closestT = Double.MAX_VALUE;
        Vec3 closestHitPoint = null;

        for (Entity entity : entities) {
            AABB aabb = entity.getBoundingBox();
            TargetLockState lockState = ModState.TARGET_LOCK;
            if (lockState.isLockedTo(entity)) {
                double expansion = Config.getTargetHitboxExpansion();
                aabb = aabb.inflate(expansion);
            }
            double t = rayAABBIntersection(start, direction, aabb);
            
            if (t < 0 || t > maxDistance || t >= closestT)
                continue;

            Vec3 hitPoint = start.add(direction.scale(t));
            
            if (!hasLineOfSight(mc, mc.player.getEyePosition(1.0f), entity))
                continue;

            closestT = t;
            closestEntity = entity;
            closestHitPoint = hitPoint;
        }

        return closestEntity != null ? new EntityHitResult(closestEntity, closestHitPoint) : null;
    }

    private double rayAABBIntersection(Vec3 rayOrigin, Vec3 rayDir, AABB aabb) {
        double tMin = 0.0;
        double tMax = Double.MAX_VALUE;

        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? rayOrigin.x : (axis == 1 ? rayOrigin.y : rayOrigin.z);
            double dir = axis == 0 ? rayDir.x : (axis == 1 ? rayDir.y : rayDir.z);
            double min = axis == 0 ? aabb.minX : (axis == 1 ? aabb.minY : aabb.minZ);
            double max = axis == 0 ? aabb.maxX : (axis == 1 ? aabb.maxY : aabb.maxZ);

            if (Math.abs(dir) < 1e-8) {
                if (origin < min || origin > max)
                    return -1;
            } else {
                double t1 = (min - origin) / dir;
                double t2 = (max - origin) / dir;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax)
                    return -1;
            }
        }
        return tMin >= 0 ? tMin : tMax;
    }

    private boolean hasLineOfSight(Minecraft mc, Vec3 fromPos, Entity target) {
        Vec3 targetPos = target.getBoundingBox().getCenter();
        BlockHitResult blockHit = mc.level.clip(new ClipContext(fromPos, targetPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private BlockHitResult rayTraceBlocks(Minecraft mc, Vec3 start, Vec3 end) {
        if (!ModState.STATUS.isEnabled()) {
            return mc.level
                    .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        }

        double distance = start.distanceTo(end);
        if (distance <= 0 || !Double.isFinite(distance)) {
            return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        }

        return ddaRayTrace(mc, start, end, distance);
    }

    /**
     * DDAボクセルトラバーサルによるレイキャスト。
     * レイ上の全ブロックを漏れなく訪問し、スキップによる誤ヒットを防止する。
     */
    private BlockHitResult ddaRayTrace(Minecraft mc, Vec3 start, Vec3 end, double maxDistance) {
        double dirX = end.x - start.x;
        double dirY = end.y - start.y;
        double dirZ = end.z - start.z;
        double lenSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (lenSq < 1e-12) {
            return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        }

        int x = (int) Math.floor(start.x);
        int y = (int) Math.floor(start.y);
        int z = (int) Math.floor(start.z);

        int stepX = Double.compare(dirX, 0);
        int stepY = Double.compare(dirY, 0);
        int stepZ = Double.compare(dirZ, 0);

        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dirX) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dirY) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dirZ) : Double.MAX_VALUE;

        double tMaxX = stepX > 0 ? ((x + 1) - start.x) * tDeltaX
                : stepX < 0 ? (start.x - x) * tDeltaX
                : Double.MAX_VALUE;
        double tMaxY = stepY > 0 ? ((y + 1) - start.y) * tDeltaY
                : stepY < 0 ? (start.y - y) * tDeltaY
                : Double.MAX_VALUE;
        double tMaxZ = stepZ > 0 ? ((z + 1) - start.z) * tDeltaZ
                : stepZ < 0 ? (start.z - z) * tDeltaZ
                : Double.MAX_VALUE;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        TopDownCuller culler = TopDownCuller.getInstance();
        int maxSteps = (int) Math.min(maxDistance * 3, 3000);

        for (int i = 0; i < maxSteps; i++) {
            mutablePos.set(x, y, z);

            if (culler.isBlockCulled(mutablePos, mc.level) && !culler.isHittableFadeBlock(mutablePos, mc.level)) {
                // カリング済みブロックは透過として扱う
            } else {
                BlockState state = mc.level.getBlockState(mutablePos);
                if (!state.isAir()) {
                    var shape = state.getShape(mc.level, mutablePos, CollisionContext.of(mc.player));
                    if (!shape.isEmpty()) {
                        var clipResult = shape.clip(start, end, mutablePos);
                        if (clipResult != null) {
                            return clipResult;
                        }
                    }
                }
            }

            // 次のボクセル境界へ進む
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > 1.0) break;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > 1.0) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    if (tMaxY > 1.0) break;
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > 1.0) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }

    public float[] getMouseTargetYawPitch(Minecraft mc, float partialTick) {
        Vec3 dir = getMouseRayDirection(mc, partialTick);
        if (dir == null) return null;

        double horizontalLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (horizontalLen < 1e-8) return null;

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-dir.y, horizontalLen));
        return new float[]{yaw, pitch};
    }

    public net.minecraft.world.phys.HitResult getLastHitResult() {
        return lastHitResult;
    }

    public BlockHitResult getLastBlockHit() {
        return lastBlockHit;
    }

    public EntityHitResult getLastEntityHit() {
        return lastEntityHit;
    }

    private Vec3 getMouseRayDirection(Minecraft mc, float partialTick) {
        if (mc.mouseHandler == null || mc.options == null || mc.getWindow() == null)
            return null;

        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        if (screenWidth < MIN_SCREEN_DIMENSION || screenHeight < MIN_SCREEN_DIMENSION)
            return null;

        double fov;
        if (ModState.STATUS.isEnabled()) {
            fov = com.topdownview.Config.getTopDownFov();
        } else {
            fov = mc.options.fov().get();
        }
        double aspectRatio = screenWidth / screenHeight;
        double pitch;
        if (ModState.CAMERA.isFreeCameraMode()) {
            pitch = ModState.CAMERA.getLerpFreeCameraPitch(partialTick);
        } else if (ModState.CAMERA.isFreeCameraPitchAdjusted()) {
            pitch = ModState.CAMERA.getFreeCameraPitch();
        } else if (ModState.STATUS.isMiningMode()) {
            pitch = com.topdownview.Config.getMiningModePitch();
        } else {
            pitch = com.topdownview.Config.getCameraPitch();
        }
        double yaw = ModState.CAMERA.getLerpYaw(partialTick);

        if (cachedDirection != null
                && Math.abs(mouseX - lastMouseX) < EPSILON
                && Math.abs(mouseY - lastMouseY) < EPSILON
                && Math.abs(fov - lastFov) < EPSILON
                && Math.abs(aspectRatio - lastAspectRatio) < EPSILON
                && Math.abs(yaw - lastYaw) < EPSILON
                && Math.abs(pitch - lastPitch) < EPSILON) {
            return cachedDirection;
        }

        double ndcX = -((SCREEN_TO_NDC_FACTOR * mouseX / screenWidth) - NDC_OFFSET);
        double ndcY = ((SCREEN_TO_NDC_FACTOR * mouseY / screenHeight) - NDC_OFFSET);
        double fovRad = Math.toRadians(fov);
        double tanHalfFov = Math.tan(fovRad / SCREEN_TO_NDC_FACTOR);
        double offsetX = ndcX * tanHalfFov * aspectRatio;
        double offsetY = ndcY * tanHalfFov;

        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

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
        if (length > 0) {
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }

        cachedDirection = new Vec3(dirX, dirY, dirZ);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastFov = fov;
        lastAspectRatio = aspectRatio;
        lastYaw = yaw;
        lastPitch = pitch;
        return cachedDirection;
    }

    private void clearResults() {
        lastBlockHit = null;
        lastEntityHit = null;
        lastHitResult = null;
    }

    public void clearCache() {
        cachedDirection = null;
        lastFov = -1;
        lastAspectRatio = -1;
        lastMouseX = -1;
        lastMouseY = -1;
        lastYaw = -1;
        lastPitch = -1;
        lastUpdateGameTime = -1;
        lastPartialTick = -1;
    }
}


