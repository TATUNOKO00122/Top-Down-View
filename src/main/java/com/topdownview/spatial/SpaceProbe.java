package com.topdownview.spatial;

import com.topdownview.util.PerfMonitor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 局所空間判定プローブ。
 *
 * <p>{@link RoomFloodFill} のフラッドフィル結果を基に、部屋内部の空気セルや壁殻セル情報を提供する。
 */
public final class SpaceProbe {

    private SpaceProbe() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 天井を探す上方スキャン距離 */
    public static final int CEILING_SCAN_HEIGHT = RoomFloodFill.CEILING_SCAN_HEIGHT;

    /** 壁を探す水平スキャン距離 */
    public static final int WALL_SCAN_DISTANCE = RoomFloodFill.MAX_RADIUS_XZ;

    /** 天井未検出を示すsentinel値 */
    public static final int NO_CEILING = Integer.MIN_VALUE;

    /** 壁未検出を示すsentinel値 */
    public static final int WALL_NONE = -1;

    /** 走査する4水平方向（N,S,E,W） */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * 作業バッファを再利用して空間を判定する。
     *
     * @param level   ワールド
     * @param feetPos プレイヤーの足元ブロック位置
     * @param scratch {@link RoomFloodFill.Scratch}。{@code null} なら内部で生成。
     * @return 判定結果
     */
    public static Result probe(BlockGetter level, BlockPos feetPos, RoomFloodFill.Scratch scratch) {
        return probe(level, feetPos, scratch, true);
    }

    /**
     * 作業バッファを再利用して空間を判定する。
     *
     * @param level   ワールド
     * @param feetPos プレイヤーの足元ブロック位置
     * @param scratch {@link RoomFloodFill.Scratch}。{@code null} なら内部で生成。
     * @param measurePhases {@code false} なら flood/segment の内部分単位計測をしない。
     *                      非同期ワーカーから呼ぶ場合、タイマーはティック/描画スレッド専用なので false にする。
     * @return 判定結果
     */
    public static Result probe(BlockGetter level, BlockPos feetPos, RoomFloodFill.Scratch scratch,
                               boolean measurePhases) {
        if (level == null || feetPos == null) {
            int[] noWalls = new int[HORIZONTAL.length];
            java.util.Arrays.fill(noWalls, WALL_NONE);
            return new Result(false, NO_CEILING, BlockPos.ZERO, noWalls, HORIZONTAL,
                    RoomFloodFill.Result.EMPTY, RoomSegmentation.Result.EMPTY);
        }

        long tFlood = measurePhases ? System.nanoTime() : 0L;
        RoomFloodFill.Result roomResult = RoomFloodFill.compute(level, feetPos, scratch);
        if (measurePhases) {
            PerfMonitor.FLOOD.add(System.nanoTime() - tFlood);
        }
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

        long tSegment = measurePhases ? System.nanoTime() : 0L;
        RoomSegmentation.Result segmentation = enclosed
                // 生の足元位置ではなく、flood が実際に起点にした有効セルを渡す。スラブ床の上では
                // blockPosition() が固体セルになり、そのセルはどの部屋にも含まれず PlayerRoom が
                // 失われるため (階段は踏面の上の空気セルなので問題が出ない)。
                ? RoomSegmentation.analyze(roomResult, roomResult.getSeed(),
                        scratch != null ? scratch.getBlockMap() : null)
                : RoomSegmentation.Result.EMPTY;
        if (measurePhases) {
            PerfMonitor.SEGMENT.add(System.nanoTime() - tSegment);
        }

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
