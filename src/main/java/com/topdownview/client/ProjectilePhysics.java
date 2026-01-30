package com.topdownview.client;

import net.minecraft.world.item.*;

public record ProjectilePhysics(
    double gravity,
    double drag,
    double baseSpeed
) {
    private static final double DEFAULT_GRAVITY = 0.05;
    private static final double DEFAULT_DRAG = 0.99;

    public static final ProjectilePhysics BOW = new ProjectilePhysics(DEFAULT_GRAVITY, DEFAULT_DRAG, 3.0);
    public static final ProjectilePhysics CROSSBOW = new ProjectilePhysics(DEFAULT_GRAVITY, DEFAULT_DRAG, 3.15);
    public static final ProjectilePhysics TRIDENT = new ProjectilePhysics(DEFAULT_GRAVITY, DEFAULT_DRAG, 2.5);

    public static ProjectilePhysics fromItem(Item item) {
        if (item instanceof BowItem) return BOW;
        if (item instanceof CrossbowItem) return CROSSBOW;
        if (item instanceof TridentItem) return TRIDENT;
        return null;
    }
}