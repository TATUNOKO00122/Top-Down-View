package com.topdownview.state;

import com.topdownview.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClickToMoveState {

    public static final ClickToMoveState INSTANCE = new ClickToMoveState();

    private Vec3 targetPosition = null;
    private Vec3 originalLocation = null;
    private Entity targetEntity = null;
    private boolean isMoving = false;
    private boolean isLongPressFollow = false;
    private boolean useBaritone = false;
    private int baritoneStartTick = 0;
    private boolean baritonePathStarted = false;
    private static final int BARITONE_MIN_TICKS = 10;

    public static final double DEFAULT_ARRIVAL_THRESHOLD = 1.5;
    public static final double DEFAULT_ATTACK_RANGE = 3.0;

    private ClickToMoveState() {}

    public Vec3 getTargetPosition() { return targetPosition; }
    public Vec3 getOriginalLocation() { return originalLocation; }
    public Entity getTargetEntity() { return targetEntity; }
    public boolean isMoving() { return isMoving; }
    public boolean isLongPressFollow() { return isLongPressFollow; }
    public boolean useBaritone() { return useBaritone; }
    public int getBaritoneStartTick() { return baritoneStartTick; }
    
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

    public void setLongPressFollow(boolean follow) {
        this.isLongPressFollow = follow;
        if (follow) {
            this.isMoving = true;
        }
    }

    public void startMoveTo(Vec3 destination, Vec3 playerPos) {
        this.targetPosition = destination;
        this.originalLocation = playerPos;
        this.targetEntity = null;
        this.isMoving = true;
    }

    public void startFollowEntity(Entity entity, Vec3 playerPos) {
        this.targetEntity = entity;
        this.targetPosition = entity.position();
        this.originalLocation = playerPos;
        this.isMoving = true;
    }

    public boolean hasArrived(Vec3 playerPos, double threshold) {
        if (targetPosition == null || originalLocation == null) return true;
        if (!isMoving) return true;

        // 目的地への距離が閾値以内かチェック
        double distToTargetSq = playerPos.distanceToSqr(targetPosition);
        if (distToTargetSq <= threshold * threshold) {
            return true;
        }

        // 出発点から目的地への距離
        double totalDistSq = originalLocation.distanceToSqr(targetPosition);
        // 出発点から現在位置への距離
        double distFromOriginalSq = playerPos.distanceToSqr(originalLocation);

        // オーバーシュート検出：現在位置が目的地を通り過ぎているか
        // totalDistSqが小さい場合（短距離移動）はオーバーシュート検出を緩和
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
        isLongPressFollow = false;
        useBaritone = false;
        baritoneStartTick = 0;
        baritonePathStarted = false;
    }

    public void stopMovement() {
        isMoving = false;
        targetPosition = null;
        originalLocation = null;
        targetEntity = null;
        useBaritone = false;
        baritoneStartTick = 0;
        baritonePathStarted = false;
    }
}
