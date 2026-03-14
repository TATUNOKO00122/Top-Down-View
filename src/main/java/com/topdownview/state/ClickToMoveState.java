package com.topdownview.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClickToMoveState {

    public static final ClickToMoveState INSTANCE = new ClickToMoveState();

    private Vec3 targetPosition = null;
    private Vec3 originalLocation = null;
    private Entity targetEntity = null;
    private boolean isMoving = false;
    private boolean useBaritone = false;
    private int baritoneStartTick = 0;
    private boolean baritonePathStarted = false;
    private static final int BARITONE_MIN_TICKS = 10;

    private boolean isAttacking = false;
    private int attackCooldown = 0;

    private boolean isDestroying = false;
    private BlockPos destroyTargetBlock = null;

    private boolean isInteracting = false;
    private BlockPos interactTargetBlock = null;

    private boolean isHoldMode = false;

    public static final double DEFAULT_ARRIVAL_THRESHOLD = 1.5;
    public static final double DEFAULT_ATTACK_RANGE = 3.0;
    public static final double DEFAULT_INTERACT_RANGE = 2.5;
    public static final double DEFAULT_DESTROY_RANGE = 4.5;

    private ClickToMoveState() {}

    public Vec3 getTargetPosition() { return targetPosition; }
    public Vec3 getOriginalLocation() { return originalLocation; }
    public Entity getTargetEntity() { return targetEntity; }
    public boolean isMoving() { return isMoving; }
    public boolean useBaritone() { return useBaritone; }
    public int getBaritoneStartTick() { return baritoneStartTick; }
    public boolean isAttacking() { return isAttacking; }
    public int getAttackCooldown() { return attackCooldown; }
    public boolean isDestroying() { return isDestroying; }
    public BlockPos getDestroyTargetBlock() { return destroyTargetBlock; }
    public boolean isInteracting() { return isInteracting; }
    public BlockPos getInteractTargetBlock() { return interactTargetBlock; }
    public boolean isHoldMode() { return isHoldMode; }

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

    public void setOriginalLocation(Vec3 pos) {
        if (pos != null && (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z))) {
            throw new IllegalArgumentException("Original location must be finite");
        }
        this.originalLocation = pos;
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
        if (entity != null) {
            this.isMoving = true;
        }
    }

    public void setAttacking(boolean attacking) {
        this.isAttacking = attacking;
    }

    public void setAttackCooldown(int cooldown) {
        this.attackCooldown = Math.max(0, cooldown);
    }

    public void tickAttackCooldown() {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
    }

    public boolean canAttack() {
        return attackCooldown == 0;
    }

    public void setDestroying(boolean destroying) {
        this.isDestroying = destroying;
    }

    public void setDestroyTargetBlock(BlockPos pos) {
        this.destroyTargetBlock = pos;
    }

    public void setInteracting(boolean interacting) {
        this.isInteracting = interacting;
    }

    public void setInteractTargetBlock(BlockPos pos) {
        this.interactTargetBlock = pos;
    }

    public void setHoldMode(boolean holdMode) {
        this.isHoldMode = holdMode;
    }

    public void startFollowAndAttack(Entity entity, Vec3 playerPos) {
        clearAllTargets();
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isAttacking = true;
        this.isHoldMode = true;
    }

    public void startMoveTo(Vec3 destination, Vec3 playerPos) {
        clearAllTargets();
        this.targetPosition = destination;
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isHoldMode = true;
    }

    public void startFollowEntity(Entity entity, Vec3 playerPos) {
        clearAllTargets();
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
    }

    public void startDestroyBlock(BlockPos blockPos, Vec3 playerPos) {
        clearAllTargets();
        this.destroyTargetBlock = blockPos;
        this.targetPosition = Vec3.atCenterOf(blockPos);
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isDestroying = true;
        this.isHoldMode = true;
    }

    public void startInteractBlock(BlockPos blockPos, Vec3 playerPos) {
        clearAllTargets();
        this.interactTargetBlock = blockPos;
        this.targetPosition = Vec3.atCenterOf(blockPos);
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isInteracting = true;
        this.isHoldMode = true;
    }

    public void startInteractEntity(Entity entity, Vec3 playerPos) {
        clearAllTargets();
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
        this.isInteracting = true;
        this.isHoldMode = true;
    }

    private void clearAllTargets() {
        this.targetEntity = null;
        this.destroyTargetBlock = null;
        this.interactTargetBlock = null;
        this.isAttacking = false;
        this.isDestroying = false;
        this.isInteracting = false;
        this.attackCooldown = 0;
        this.isHoldMode = false;
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

    public boolean hasArrivedAtBlock(Vec3 playerPos, BlockPos blockPos, double threshold) {
        if (blockPos == null) return true;
        if (!isMoving) return true;

        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        double distSq = playerPos.distanceToSqr(blockCenter);
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
        attackCooldown = 0;
        isDestroying = false;
        destroyTargetBlock = null;
        isInteracting = false;
        interactTargetBlock = null;
        isHoldMode = false;
    }

    public void stopMovement() {
        isMoving = false;
        targetPosition = null;
        originalLocation = null;
        targetEntity = null;
        useBaritone = false;
        baritoneStartTick = 0;
        baritonePathStarted = false;
        isAttacking = false;
        attackCooldown = 0;
        isDestroying = false;
        destroyTargetBlock = null;
        isInteracting = false;
        interactTargetBlock = null;
        isHoldMode = false;
    }
}