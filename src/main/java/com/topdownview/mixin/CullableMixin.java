package com.topdownview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.topdownview.culling.Cullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(value = { Entity.class, BlockEntity.class })
public class CullableMixin implements Cullable {

    @Unique
    private boolean topdownview_culled = false;

    @Override
    public void topdownview_setCulled(boolean value) {
        this.topdownview_culled = value;
    }

    @Override
    public boolean topdownview_isCulled() {
        return this.topdownview_culled;
    }
}