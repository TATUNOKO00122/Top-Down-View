package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ブロック固体判定ユーティリティ。
 *
 * <p>SpaceProbe（天井・壁スキャン）と StairAnalyzer（階段検出）で使用する。
 */
public final class WallAnalyzer {

    private WallAnalyzer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * ブロックが固体（衝突判定あり）かどうか。
     *
     * <p>空気ブロックや草花などの非衝突ブロックは非固体。
     * 壁、ファルス、ガラスなどの衝突判定ありブロックは固体。
     */
    public static boolean isSolid(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return !shape.isEmpty();
    }
}
