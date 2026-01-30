package com.topdownview.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("onGround")
    boolean isOnGround();

    @Invoker("setSharedFlag")
    void callSetSharedFlag(int flag, boolean set);
}
