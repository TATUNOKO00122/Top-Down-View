package com.topdownview.state;

import net.minecraft.world.entity.Entity;

public final class TargetLockState {

    public static final TargetLockState INSTANCE = new TargetLockState();

    private Entity lockedTarget = null;
    private int remainingTicks = 0;

    private TargetLockState() {}

    public Entity getLockedTarget() {
        return lockedTarget;
    }

    public boolean isLocked() {
        return lockedTarget != null && lockedTarget.isAlive() && remainingTicks > 0;
    }

    public boolean isLockedTo(Entity entity) {
        return isLocked() && lockedTarget.getId() == entity.getId();
    }

    public void lock(Entity entity, int durationTicks) {
        if (entity == null) return;
        this.lockedTarget = entity;
        this.remainingTicks = durationTicks;
    }

    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (lockedTarget != null && (!lockedTarget.isAlive() || remainingTicks <= 0)) {
            clear();
        }
    }

    public void clear() {
        lockedTarget = null;
        remainingTicks = 0;
    }

    public void reset() {
        clear();
    }
}
