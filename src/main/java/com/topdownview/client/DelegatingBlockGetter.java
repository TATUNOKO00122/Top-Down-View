package com.topdownview.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * 実ワールドの {@link BlockAndTintGetter} に委譲し、{@link #getBlockState} だけ差し替えるプロキシ基底。
 * ゴースト描画用に一部ブロックを空気として見せたいレンダラーが継承する。
 */
public abstract class DelegatingBlockGetter implements BlockAndTintGetter {

    protected final BlockAndTintGetter delegate;

    protected DelegatingBlockGetter(BlockAndTintGetter delegate) {
        this.delegate = delegate;
    }

    @Override
    public abstract BlockState getBlockState(BlockPos pos);

    @Override
    public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return delegate.getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return delegate.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }
}
