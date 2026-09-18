package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** XZ 投影がセル全域とみなせる許容誤差。 */
    private static final double FULL_XZ_EPS = 1.0E-5;

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

    /**
     * 衝突形状の XZ 投影がセル全域に達する（＝天井・覆いとして機能しうる）か。
     *
     * <p>フェンス・垣根・鉄柵・チェーンのような細い縦構造は真下のセルを遮らない。
     * これを天井とみなすと装飾フェンスの下の通路が「屋内」となり、隣の建物まで
     * 一つの閉空間として連結されてしまう。上から見て隙間の空くブロックは覆いから除外する。
     */
    public static boolean isCeilingLike(VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        return shape.min(Direction.Axis.X) <= FULL_XZ_EPS
                && shape.max(Direction.Axis.X) >= 1.0 - FULL_XZ_EPS
                && shape.min(Direction.Axis.Z) <= FULL_XZ_EPS
                && shape.max(Direction.Axis.Z) >= 1.0 - FULL_XZ_EPS;
    }

    /** 指定位置のブロックが天井・覆いとして機能しうるか。衝突形状が空なら false。 */
    public static boolean isCeilingLike(BlockGetter level, BlockPos pos, BlockState state) {
        return isCeilingLike(state.getCollisionShape(level, pos, CollisionContext.empty()));
    }
}

