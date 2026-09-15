package com.topdownview.spatial;

import com.topdownview.util.SpaceProfiler;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 局所空間判定プローブ。
 *
 * <p>従来は4方向の射線スキャンと天井スキャンを行っていましたが、
 * 新しく {@link RoomFloodFill} による 3D BFS フラッドフィル部屋認識エンジンへ移設されました。
 *
 * <p>外部 API の互換性を全方位で維持しつつ、部屋内部の空気セルや壁殻セル情報を提供します。
 */
public final class SpaceProbe {

    private SpaceProbe() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 天井を探す上方スキャン距離 */
    public static final int CEILING_SCAN_HEIGHT = RoomFloodFill.CEILING_SCAN_HEIGHT;

    /** 壁を探す水平スキャン距離 */
    public static final int WALL_SCAN_DISTANCE = RoomFloodFill.MAX_RADIUS_XZ;

    /** 屋内判定に必要な壁方向数（後方互換用） */
    public static final int MIN_WALLED_DIRS = 3;

    /** 天井未検出を示すsentinel値 */
    public static final int NO_CEILING = Integer.MIN_VALUE;

    /** 壁未検出を示すsentinel値 */
    public static final int WALL_NONE = -1;

    /** 走査する4水平方向（N,S,E,W） */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * 指定位置の空間を判定する（RoomFloodFill に委譲）。
     *
     * @param level   ワールド
     * @param feetPos プレイヤーの足元ブロック位置
     * @return 判定結果
     */
    public static Result probe(BlockGetter level, BlockPos feetPos) {
        return probe(level, feetPos, null);
    }

    /**
     * 作業バッファを再利用して空間を判定する。
     *
     * @param level   ワールド
     * @param feetPos プレイヤーの足元ブロック位置
     * @param scratch {@link RoomFloodFill.Scratch}。{@code null} なら内部で生成。
     * @return 判定結果
     */
    public static Result probe(BlockGetter level, BlockPos feetPos, RoomFloodFill.Scratch scratch) {
        if (level == null || feetPos == null) {
            int[] noWalls = new int[HORIZONTAL.length];
            java.util.Arrays.fill(noWalls, WALL_NONE);
            return new Result(false, NO_CEILING, BlockPos.ZERO, noWalls, HORIZONTAL,
                    RoomFloodFill.Result.EMPTY, RoomSegmentation.Result.EMPTY);
        }

        long tFlood = System.nanoTime();
        RoomFloodFill.Result roomResult = RoomFloodFill.compute(level, feetPos, scratch);
        SpaceProfiler.FLOOD.add(System.nanoTime() - tFlood);
        boolean enclosed = roomResult.isEnclosed();
        int ceilingY = roomResult.getCeilingY();

        // 4方向の壁までの疑似距離を算出（互換性およびデバッグHUD用）
        int[] wallDistances = new int[HORIZONTAL.length];
        if (enclosed) {
            BlockPos seed = roomResult.getSeed();
            LongSet airCells = roomResult.getAirCells();

            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction dir = HORIZONTAL[i];
                int dx = dir.getStepX();
                int dz = dir.getStepZ();
                int dist = WALL_NONE;

                for (int d = 1; d <= WALL_SCAN_DISTANCE; d++) {
                    long posLong = BlockPos.asLong(seed.getX() + dx * d, seed.getY(), seed.getZ() + dz * d);
                    if (!airCells.contains(posLong)) {
                        dist = d;
                        break;
                    }
                }
                wallDistances[i] = dist;
            }
        } else {
            java.util.Arrays.fill(wallDistances, WALL_NONE);
        }

        long tSegment = System.nanoTime();
        RoomSegmentation.Result segmentation = enclosed
                ? RoomSegmentation.analyze(roomResult, feetPos)
                : RoomSegmentation.Result.EMPTY;
        SpaceProfiler.SEGMENT.add(System.nanoTime() - tSegment);

        return new Result(enclosed, ceilingY, feetPos, wallDistances, HORIZONTAL, roomResult, segmentation);
    }

    /**
     * 空間判定結果。
     */
    public static final class Result {
        private final boolean enclosed;
        private final int ceilingY;
        private final BlockPos origin;
        private final int[] wallDistances;
        private final Direction[] directions;
        private final RoomFloodFill.Result roomResult;
        private final RoomSegmentation.Result segmentation;

        Result(boolean enclosed, int ceilingY, BlockPos origin,
               int[] wallDistances, Direction[] directions,
               RoomFloodFill.Result roomResult, RoomSegmentation.Result segmentation) {
            this.enclosed = enclosed;
            this.ceilingY = ceilingY;
            this.origin = origin;
            this.wallDistances = wallDistances.clone();
            this.directions = directions.clone();
            this.roomResult = roomResult;
            this.segmentation = segmentation;
        }

        /** 屋内判定結果 */
        public boolean isEnclosed() {
            return enclosed;
        }

        /** 天井が見つかったY座標。{@link SpaceProbe#NO_CEILING} = 天井なし */
        public int getCeilingY() {
            return ceilingY;
        }

        /** 天井が検出されたかどうか */
        public boolean hasCeiling() {
            return ceilingY != NO_CEILING;
        }

        /** 判定の起点位置 */
        public BlockPos getOrigin() {
            return origin;
        }

        /**
         * 各方向の壁までの距離。インデックスは {@link #getDirections()} に対応。
         */
        public int[] getWallDistances() {
            return wallDistances.clone();
        }

        /** 走査した4水平方向（N, S, E, W） */
        public Direction[] getDirections() {
            return directions.clone();
        }

        /** 壁が検出された方向数 */
        public int getWalledCount() {
            int count = 0;
            for (int d : wallDistances) {
                if (d >= 0) count++;
            }
            return count;
        }

        /** 詳細な 3D 部屋探索結果を取得 */
        public RoomFloodFill.Result getRoomResult() {
            return roomResult;
        }

        /** 階・部屋への分割結果を取得 */
        public RoomSegmentation.Result getSegmentation() {
            return segmentation;
        }
    }
}
