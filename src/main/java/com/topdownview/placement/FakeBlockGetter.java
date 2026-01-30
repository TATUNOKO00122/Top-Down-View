package com.topdownview.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ブロック配置シミュレーション用の軽量ワールドプロキシ。
 *
 * 変更されたブロック状態のみ独自のMapで管理し、
 * その他の情報（光量・バイオーム等）は実ワールドへ委譲する。
 * FakeServer/FakeWorld の代替として1.20.1向けに簡素化した実装。
 */
public final class FakeBlockGetter implements BlockAndTintGetter {

    private final BlockAndTintGetter delegate;
    /** 配置シミュレーション結果: 変更されたブロックのみ格納 */
    private final Map<BlockPos, BlockState> fakeBlocks = new HashMap<>();

    public FakeBlockGetter(BlockAndTintGetter delegate) {
        this.delegate = delegate;
    }

    // ==================== シミュレーション操作 ====================

    /**
     * 指定位置にブロック状態を書き込む（シミュレーション）
     */
    public void setFakeBlock(BlockPos pos, BlockState state) {
        fakeBlocks.put(pos.immutable(), state);
    }

    /**
     * シミュレーション結果をクリア
     */
    public void clear() {
        fakeBlocks.clear();
    }

    /**
     * 変更されたブロック位置のセットを返す
     */
    public Set<BlockPos> getChangedPositions() {
        return fakeBlocks.keySet();
    }

    /**
     * 指定位置にシミュレーション上のブロックが存在するか
     */
    public boolean hasFakeBlock(BlockPos pos) {
        return fakeBlocks.containsKey(pos);
    }

    // ==================== BlockAndTintGetter 実装 ====================

    @Override
    public BlockState getBlockState(BlockPos pos) {
        // シミュレーション上に変更があればそちらを返す
        BlockState fake = fakeBlocks.get(pos);
        return fake != null ? fake : delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        BlockState fake = fakeBlocks.get(pos);
        if (fake != null) {
            return fake.getFluidState();
        }
        return delegate.getFluidState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // ゴーストブロックにはBlockEntityを持たせない（クラッシュ防止）
        return delegate.getBlockEntity(pos);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }

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
}
