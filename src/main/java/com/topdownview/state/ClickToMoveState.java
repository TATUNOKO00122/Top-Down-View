package com.topdownview.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClickToMoveState {

    public static final ClickToMoveState INSTANCE = new ClickToMoveState();

    private volatile Vec3 targetPosition = null;
    private volatile Vec3 originalLocation = null;
    private volatile Entity targetEntity = null;
    private volatile boolean isMoving = false;
    private volatile boolean useBaritone = false;
    private volatile int baritoneStartTick = 0;
    private volatile boolean baritonePathStarted = false;
    private static final int BARITONE_MIN_TICKS = 10;

    private volatile boolean isAttacking = false;

    private volatile boolean isDestroying = false;
    private volatile BlockPos destroyTargetBlock = null;
    private volatile Direction destroyDirection = null;

    private volatile boolean isInteracting = false;
    private volatile BlockPos interactTargetBlock = null;

    public static final double DEFAULT_INTERACT_RANGE = 2.5;
    public static final double DEFAULT_DESTROY_RANGE = 4.5;

    private ClickToMoveState() {}

    public Vec3 getTargetPosition() { return targetPosition; }
    public Entity getTargetEntity() { return targetEntity; }
    public boolean isMoving() { return isMoving; }
    public boolean useBaritone() { return useBaritone; }
    public boolean isAttacking() { return isAttacking; }
    public boolean isDestroying() { return isDestroying; }
    public BlockPos getDestroyTargetBlock() { return destroyTargetBlock; }
    public Direction getDestroyDirection() { return destroyDirection; }
    public boolean isInteracting() { return isInteracting; }
    public BlockPos getInteractTargetBlock() { return interactTargetBlock; }

    public void setUseBaritone(boolean use) {
        if (use && !baritonePathStarted) {
            this.baritoneStartTick = 0;
            this.baritonePathStarted = true;
        }
        this.useBaritone = use;
    }

    public void tickBaritone() {
        if (useBaritone && baritoneStartTick < BARITONE_MIN_TICKS) {
            baritoneStartTick++;
        }
    }

    public boolean canCheckBaritoneArrival() {
        return baritoneStartTick >= BARITONE_MIN_TICKS;
    }

    public void setTargetPosition(Vec3 pos) {
        if (pos != null && (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z))) {
            throw new IllegalArgumentException("Target position must be finite");
        }
        this.targetPosition = pos;
        this.isMoving = (pos != null);
    }

    public void startFollowAndAttack(Entity entity, Vec3 playerPos) {
        clearAllTargets();
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isAttacking = true;
    }

    public void startMoveTo(Vec3 destination, Vec3 playerPos) {
        clearAllTargets();
        this.targetPosition = destination;
        this.originalLocation = playerPos;
        this.isMoving = true;
    }

    public void startDestroyBlock(BlockPos blockPos, Direction direction, Vec3 playerPos) {
        clearAllTargets();
        this.destroyTargetBlock = blockPos;
        this.destroyDirection = direction;
        this.targetPosition = Vec3.atCenterOf(blockPos);
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isDestroying = true;
    }

    public void startDirectDestroy(BlockPos blockPos, Direction direction) {
        this.destroyTargetBlock = blockPos;
        this.destroyDirection = direction;
        this.targetPosition = Vec3.atCenterOf(blockPos);
        this.isMoving = true;
        this.isDestroying = true;
    }

    public void startInteractBlock(BlockPos blockPos, Vec3 playerPos) {
        clearAllTargets();
        this.interactTargetBlock = blockPos;
        this.targetPosition = Vec3.atCenterOf(blockPos);
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isInteracting = true;
    }

    public void startInteractEntity(Entity entity, Vec3 playerPos) {
        clearAllTargets();
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isInteracting = true;
    }

    private void clearAllTargets() {
        this.targetEntity = null;
        this.destroyTargetBlock = null;
        this.destroyDirection = null;
        this.interactTargetBlock = null;
        this.isAttacking = false;
        this.isDestroying = false;
        this.isInteracting = false;
    }

    public boolean hasArrived(Vec3 playerPos, double threshold) {
        if (targetPosition == null || originalLocation == null) return true;
        if (!isMoving) return true;

        double distToTargetSq = playerPos.distanceToSqr(targetPosition);
        if (distToTargetSq <= threshold * threshold) {
            return true;
        }

        double totalDistSq = originalLocation.distanceToSqr(targetPosition);
        double distFromOriginalSq = playerPos.distanceToSqr(originalLocation);

        double overshootThreshold = Math.max(threshold * threshold, totalDistSq * 0.25);
        if (distFromOriginalSq >= totalDistSq + overshootThreshold) {
            return true;
        }

        return false;
    }

    public boolean hasArrivedAtEntity(Vec3 playerPos, double threshold) {
        if (targetEntity == null || !targetEntity.isAlive()) return true;
        if (!isMoving) return true;

        double distSq = playerPos.distanceToSqr(targetEntity.position());
        return distSq < threshold * threshold;
    }

    public void updateEntityTargetPosition() {
        if (targetEntity != null && targetEntity.isAlive()) {
            this.targetPosition = targetEntity.position();
        }
    }

    public void reset() {
        targetPosition = null;
        originalLocation = null;
        targetEntity = null;
        isMoving = false;
        useBaritone = false;
        baritoneStartTick = 0;
        baritonePathStarted = false;
        isAttacking = false;
        isDestroying = false;
        destroyTargetBlock = null;
        destroyDirection = null;
        isInteracting = false;
        interactTargetBlock = null;
    }

    public void stopMovement() {
        reset();
    }
}