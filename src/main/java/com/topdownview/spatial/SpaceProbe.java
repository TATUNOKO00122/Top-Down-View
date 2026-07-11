package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 局所空間判定プローブ。
 *
 * <p>プレイヤー位置から天井・壁を射线スキャンし、屋内か屋外かを判定する。
 * BFS flood-fill と異なり、O(1) の射线走査で確実かつ高速に判定する。
 *
 * <p>アルゴリズム：
 * <ol>
 *   <li>Step 1: 上方に {@value #CEILING_SCAN_HEIGHT} ブロック以内に天井（固体）があるか
 *     → なければ即 OUTDOOR（壁スキャンを省略）</li>
 *   <li>Step 2: 4水平方向それぞれについて、3レベルの高さ（足元・目線・頭上）で
 *     {@value #WALL_SCAN_DISTANCE} ブロック以内に壁（固体）があるかを走査</li>
 *   <li>固体が見つかったら壁の連続性を確認: 6方向隣接の固体数が {@value #MIN_WALL_NEIGHBORS} 以上なら壁の一部。
 *     木の幹等の孤立ブロックはスキップして奥の壁を探す</li>
 *   <li>4方向中 {@value #MIN_WALLED_DIRS} 方向以上で壁が検出されれば ENCLOSED</li>
 * </ol>
 *
 * <p>3レベルの高さで走査することで、ドア（2ブロック高の開口）や窓（1ブロック高の開口）
 * があっても壁を確実に検出する。壁の連続性チェックにより、偶然4方向にブロックがあっても
 * （木々、柱など）誤って屋内と判定されるのを防ぐ。
 */
public final class SpaceProbe {

    private SpaceProbe() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 天井を探す上方スキャン距離（足元+1 から走査）。高い天井の建物にも対応するよう十分な高さを確保。 */
    public static final int CEILING_SCAN_HEIGHT = 10;

    /** 壁を探す水平スキャン距離 */
    public static final int WALL_SCAN_DISTANCE = 10;

    /** 屋内判定に必要な壁方向数（4方向中） */
    public static final int MIN_WALLED_DIRS = 3;

    /** 天井未検出を示すsentinel値。Y座標が負のワールド(1.18+)でも安全に判定可能。 */
    public static final int NO_CEILING = Integer.MIN_VALUE;

    /** 壁未検出を示すsentinel値。壁までの距離は常に1以上のため、-1は重複しない。 */
    public static final int WALL_NONE = -1;

    /** 壁の一部と認定するために必要な6方向隣接の最低固体数。孤立ブロック（木の幹等）との判別用。 */
    public static final int MIN_WALL_NEIGHBORS = 3;

    /** 壁スキャンの高さレベル: 足元(0)・目線(1)・頭上(2) */
    private static final int[] WALL_Y_OFFSETS = {0, 1, 2};

    /** 走査する4水平方向（N,S,E,W） */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * 指定位置の空間を判定する。
     *
     * @param level   ワールド
     * @param feetPos プレイヤーの足元ブロック位置
     * @return 判定結果（enclosed, ceilingY, wallDistances 含む）
     */
    public static Result probe(BlockGetter level, BlockPos feetPos) {
        if (level == null || feetPos == null) {
            return new Result(false, NO_CEILING, BlockPos.ZERO, new int[HORIZONTAL.length], HORIZONTAL);
        }

        // Step 1: 天井判定 — 天井がなければ即OUTDOOR（壁スキャン省略）
        int ceilingY = findCeiling(level, feetPos);
        if (ceilingY == NO_CEILING) {
            int[] noWalls = new int[HORIZONTAL.length];
            java.util.Arrays.fill(noWalls, WALL_NONE);
            return new Result(false, NO_CEILING, feetPos, noWalls, HORIZONTAL);
        }

        // Step 2: 壁判定 — 4方向 × 3高さで射线スキャン
        int[] wallDistances = new int[HORIZONTAL.length];
        int walledCount = 0;
        for (int i = 0; i < HORIZONTAL.length; i++) {
            wallDistances[i] = findWallDistance(level, feetPos, HORIZONTAL[i]);
            if (wallDistances[i] >= 0) walledCount++;
        }

        boolean enclosed = walledCount >= MIN_WALLED_DIRS;
        return new Result(enclosed, ceilingY, feetPos, wallDistances, HORIZONTAL);
    }

    /**
     * 上方に天井（固体ブロック）があるか走査する。
     *
     * @return 天井が見つかったY座標。見つからなければ {@link #NO_CEILING}
     */
    private static int findCeiling(BlockGetter level, BlockPos feetPos) {
        int x = feetPos.getX();
        int z = feetPos.getZ();
        int baseY = feetPos.getY();
        for (int dy = 1; dy <= CEILING_SCAN_HEIGHT; dy++) {
            BlockPos pos = new BlockPos(x, baseY + dy, z);
            if (WallAnalyzer.isSolid(level, pos)) {
                return baseY + dy;
            }
        }
        return NO_CEILING;
    }

    /**
     * 指定方向に壁（固体ブロック）があるか走査する。
     * 3レベルの高さ（足元・目線・頭上）のいずれかで壁が見つかればOK。
     *
     * <p>壁の連続性チェック: 固体ブロックが見つかっても、それが孤立ブロック（木の幹、柱など）
     * であればスキップして、さらに奥の壁を探す。壁の一部と認定するには、
     * そのブロックの6方向隣接のうち {@value #MIN_WALL_NEIGHBORS} 個以上が固体である必要がある。
     *
     * @return 壁までの距離（1以上）。見つからなければ {@link #WALL_NONE}
     */
    private static int findWallDistance(BlockGetter level, BlockPos feetPos, Direction dir) {
        int dx = dir.getStepX();
        int dz = dir.getStepZ();
        int x = feetPos.getX();
        int z = feetPos.getZ();
        int baseY = feetPos.getY();

        for (int dist = 1; dist <= WALL_SCAN_DISTANCE; dist++) {
            int bx = x + dx * dist;
            int bz = z + dz * dist;
            for (int yOff : WALL_Y_OFFSETS) {
                BlockPos pos = new BlockPos(bx, baseY + yOff, bz);
                if (WallAnalyzer.isSolid(level, pos) && isWallSegment(level, pos)) {
                    return dist;
                }
            }
        }
        return WALL_NONE;
    }

    /**
     * 指定ブロックが壁の一部（連続した壁面の構成ブロック）かどうかを判定する。
     *
     * <p>6方向（上下南北東西）の隣接ブロックのうち {@value #MIN_WALL_NEIGHBORS} 個以上が固体なら
     * 壁の一部とみなす。これにより、孤立したブロック（木の幹、単独の柱）を壁と誤認するのを防ぐ。
     *
     * <p>典型例:
     * <ul>
     *   <li>壁の中間ブロック: 左右+上下 = 4個以上 → 壁 ✓</li>
     *   <li>壁の端ブロック: 一方向+上下 = 3個 → 壁 ✓</li>
     *   <li>木の幹: 上下のみ = 2個 → 非壁 ✗</li>
     *   <li>孤立ブロック: 0〜1個 → 非壁 ✗</li>
     * </ul>
     */
    private static boolean isWallSegment(BlockGetter level, BlockPos pos) {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (WallAnalyzer.isSolid(level, pos.relative(d))) {
                count++;
                if (count >= MIN_WALL_NEIGHBORS) return true;
            }
        }
        return false;
    }

    /**
     * 空間判定結果。
     * 不変オブジェクト。デバッグ可視化に使用する詳細情報を含む。
     */
    public static final class Result {
        private final boolean enclosed;
        private final int ceilingY;
        private final BlockPos origin;
        private final int[] wallDistances;
        private final Direction[] directions;

        Result(boolean enclosed, int ceilingY, BlockPos origin,
               int[] wallDistances, Direction[] directions) {
            this.enclosed = enclosed;
            this.ceilingY = ceilingY;
            this.origin = origin;
            this.wallDistances = wallDistances.clone();
            this.directions = directions.clone();
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
         * {@link SpaceProbe#WALL_NONE} = その方向に壁が見つからなかった
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
    }
}
