package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
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
     * ブロックが固体（壁殻構造）かどうか。
     *
     * <p>空気ブロック、草花、および葉ブロックなどの透過性/非構造ブロックは非固体。
     * 壁、フェンス、ガラスなどの構造的衝突ブロックは固体。
     */
    public static boolean isSolid(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(BlockTags.LEAVES)) return false;
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return !shape.isEmpty();
    }
}

