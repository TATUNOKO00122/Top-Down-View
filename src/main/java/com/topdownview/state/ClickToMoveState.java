package com.topdownview.state;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClickToMoveState {

    public static final ClickToMoveState INSTANCE = new ClickToMoveState();

    private Vec3 targetPosition = null;
    private Entity targetEntity = null;
    private boolean isMoving = false;
    private boolean isLongPressFollow = false;

    public static final double DEFAULT_ARRIVAL_THRESHOLD = 1.5;
    public static final double DEFAULT_ATTACK_RANGE = 3.0;

    private ClickToMoveState() {}

    public Vec3 getTargetPosition() { return targetPosition; }
    public Entity getTargetEntity() { return targetEntity; }
    public boolean isMoving() { return isMoving; }
    public boolean isLongPressFollow() { return isLongPressFollow; }

    public void setTargetPosition(Vec3 pos) {
        if (pos != null && (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z))) {
            throw new IllegalArgumentException("Target position must be finite");
        }
        this.targetPosition = pos;
        this.isMoving = (pos != null);
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

    public void reset() {
        targetPosition = null;
        targetEntity = null;
        isMoving = false;
        isLongPressFollow = false;
    }

    public void stopMovement() {
        isMoving = false;
        targetPosition = null;
    }
}
