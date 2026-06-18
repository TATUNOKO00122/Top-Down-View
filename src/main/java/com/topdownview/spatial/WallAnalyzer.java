package com.topdownview.spatial;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 壁解析ユーティリティ。
 *
 * <p>壁厚測定・開口部サイズ測定を担当。
 * 壁厚が設定以下なら壁の向こう側を調査し、
 * 向こう側の空気領域サイズが閾値以下なら「境界内の小穴」、
 * 超過なら「別空間への接続開口」と判定する。
 */
public final class WallAnalyzer {

    private WallAnalyzer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** ブロックが固体（衝突判定あり）かどうか */
    public static boolean isSolid(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return !shape.isEmpty();
    }

    /**
     * 壁厚を測定：wallPos から dir 方向に固体が連続する長さを返す。
     * wallPos 自体を 1 とする。wallPos が固体でなければ 0。
     *
     * @param maxThickness 測定上限。これに達したら「厚すぎ」とみなす
     */
    public static int measureWallThickness(BlockGetter level, BlockPos wallPos, Direction dir, int maxThickness) {
        if (!isSolid(level, wallPos)) return 0;
        int thickness = 1;
        BlockPos cursor = wallPos.relative(dir);
        while (thickness < maxThickness) {
            if (!isSolid(level, cursor)) break;
            thickness++;
            cursor = cursor.relative(dir);
        }
        return thickness;
    }

    /**
     * 壁の向こう側の開口部サイズを測定。
     *
     * <p>wallPos から dir 方向に固体を掘り進め、最初に空気に到達した位置で
     * 壁面に平行な平面上の空気連結成分を数える。
     *
     * @return 開口部のサイズ（ブロック数）。0 = 開口部なし、または壁が厚すぎ
     */
    public static int measureOpeningSize(BlockGetter level,
                                         BlockPos wallPos,
                                         Direction dir,
                                         int maxThickness,
                                         int maxHoleScan) {
        BlockPos surfacePos = wallPos;
        int thickness = 0;
        while (thickness < maxThickness) {
            if (!isSolid(level, surfacePos)) break;
            surfacePos = surfacePos.relative(dir);
            thickness++;
        }
        if (thickness == 0) return 0;
        if (thickness >= maxThickness) return 0;
        if (isSolid(level, surfacePos)) return 0;

        return countConnectedAirOnPlane(level, surfacePos, dir, maxHoleScan);
    }

    /**
     * 指定方向に垂直な平面上で、空気ブロックの連結成分を収集。
     *
     * <p>dir=NORTH/SOUTH → X-Y 平面、dir=UP/DOWN → X-Z 平面、dir=EAST/WEST → Y-Z 平面。
     * 平面から外れないよう、dir 軸座標が start と同じブロックのみ探索する。
     * 固体ブロックは結果に含めない（壁面の穴を構成する空気のみ）。
     *
     * <p>visited を long のハッシュ集合で管理し、BlockPos の equals/hashCode
     * オーバーヘッドを回避する。result は呼び出し元が Opening 記録に使うため
     * BlockPos のまま維持。
     */
    public static Set<BlockPos> collectAirOnPlane(BlockGetter level, BlockPos start, Direction dir, int maxScan) {
        Direction axis1 = getPerpendicularAxis1(dir);
        Direction axis2 = getPerpendicularAxis2(dir);
        int fixedAxisValue = getAxisCoordinate(start, dir);

        Set<Long> visited = new HashSet<>(maxScan * 2);
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> result = new HashSet<>(maxScan);
        queue.add(start);
        visited.add(start.asLong());

        while (!queue.isEmpty() && result.size() < maxScan) {
            BlockPos p = queue.poll();
            if (isSolid(level, p)) continue;
            result.add(p);

            enqueuePlaneNeighbor(queue, visited, p, axis1, dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis1.getOpposite(), dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis2, dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis2.getOpposite(), dir, fixedAxisValue);
        }
        return result;
    }

    /**
     * collectAirOnPlane と同じ平面 BFS を行うが、結果 Set を生成せず size だけ返す。
     *
     * <p>呼び出し元が「size > maxHoleSize か？」だけ知りたい場合に Set 生成を丸ごと
     * 省略できる。SpaceExplorer の空気→空気遷移の大半（開放ケース）で有効。
     * size <= maxHoleSize の場合は collectAirOnPlane を呼び直して Opening 記録に
     * 必要なブロック集合を取得する（小サイズなので再 BFS コストも小）。
     */
    public static int countAirOnPlane(BlockGetter level, BlockPos start, Direction dir, int maxScan) {
        Direction axis1 = getPerpendicularAxis1(dir);
        Direction axis2 = getPerpendicularAxis2(dir);
        int fixedAxisValue = getAxisCoordinate(start, dir);

        Set<Long> visited = new HashSet<>(maxScan * 2);
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());
        int count = 0;

        while (!queue.isEmpty() && count < maxScan) {
            BlockPos p = queue.poll();
            if (isSolid(level, p)) continue;
            count++;
            if (count >= maxScan) break;

            enqueuePlaneNeighbor(queue, visited, p, axis1, dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis1.getOpposite(), dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis2, dir, fixedAxisValue);
            enqueuePlaneNeighbor(queue, visited, p, axis2.getOpposite(), dir, fixedAxisValue);
        }
        return count;
    }

    /** collectAirOnPlane の結果サイズを返すショートカット（Set 生成なし） */
    public static int countConnectedAirOnPlane(BlockGetter level, BlockPos start, Direction dir, int maxScan) {
        return countAirOnPlane(level, start, dir, maxScan);
    }

    /**
     * 平面 BFS の近傍をエンキュー。dir 軸座標が固定値と一致し、未訪問の場合のみ追加。
     * visited は long のハッシュ集合で重複排除する。
     */
    private static void enqueuePlaneNeighbor(Queue<BlockPos> queue, Set<Long> visited,
                                             BlockPos p, Direction side, Direction dir, int fixedAxisValue) {
        BlockPos np = p.relative(side);
        if (getAxisCoordinate(np, dir) != fixedAxisValue) return;
        if (visited.add(np.asLong())) queue.add(np);
    }

    /**
     * 開口部サイズから OpeningType を判定。
     *
     * @return 開口部なしは null、小穴は BOUNDARY_HOLE、大穴は PASSAGE
     */
    public static OpeningType classifyOpening(int size, int maxHoleSize) {
        if (size <= 0) return null;
        if (size <= maxHoleSize) return OpeningType.BOUNDARY_HOLE;
        return OpeningType.PASSAGE;
    }

    private static Direction getPerpendicularAxis1(Direction dir) {
        return switch (dir.getAxis()) {
            case X, Z -> Direction.UP;
            case Y -> Direction.NORTH;
        };
    }

    private static Direction getPerpendicularAxis2(Direction dir) {
        return switch (dir.getAxis()) {
            case X -> Direction.SOUTH;
            case Z -> Direction.EAST;
            case Y -> Direction.EAST;
        };
    }

    private static int getAxisCoordinate(BlockPos pos, Direction dir) {
        return switch (dir.getAxis()) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }
}
