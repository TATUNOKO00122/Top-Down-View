package com.topdownview.spatial;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.Objects;

/**
 * 建物構造分類器: 部屋内部空間 ( {@link RoomFloodFill} ) を参照点として、
 * 建物を構成する固体ブロック群を {@link Label#ROOF} / {@link Label#WALL} / {@link Label#FLOOR}
 * に分類し、屋根形状 ({@link RoofShape}) を推定します。
 *
 * <p>アルゴリズムは 5 フェーズで構成されます:
 * <ol>
 *   <li><b>Phase 1 建物固体マス構築</b> — {@link RoomFloodFill.Result#getShellCells()} を種に、
 *       固体連結性に基づき BFS で外側・内側へ厚さ {@link #T_MAX} まで拡張。
 *       空気隣接ではなく固体連結で補捉するため、角の離脱ブロックも漏れません。</li>
 *   <li><b>Phase 2 シード・ラベリング</b> — 内部空気に直接接するブロックを ROOF/WALL/FLOOR
 *       シードとして印付けます (確信度の高い初期ラベル)。</li>
 *   <li><b>Phase 3 多始点距離比較</b> — 建物マス上で各シード群からの最短ホップ数を
 *       多始点 BFS で算出し、最小距離のラベルを採用。同点時は y 座標で tiebreak。</li>
 *   <li><b>Phase 4 屋根形状識別</b> — ROOF ラベルのブロックから heightmap を構築し、
 *       高低差の有無で {@link RoofShape#FLAT} / {@link RoofShape#SLOPE} を判定。
 *       形状の細分類 (切妻/寄棟等) は将来拡張余地として SLOPE に集約。</li>
 * </ol>
 *
 * <p>本クラスはスレッドセーフではありません。呼び出し側が単一スレッド (主に
 * クライアント/サーバーの tick スレッド) で利用してください。
 */
public final class BuildingClassifier {

    private BuildingClassifier() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** Phase 1 で固体マスを拡張する最大厚み (ブロック)。 */
    public static final int T_MAX = 4;

    /** 距離未到達を示す sentinel。{@code Integer.MAX_VALUE / 4} で加算時の桁あふれを防ぐ。 */
    private static final int INF = Integer.MAX_VALUE / 4;

    /** 水平4方向 (N, S, E, W)。WALL シード判定で使用。 */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** 探索・伝播で使用する6方向。 */
    private static final Direction[] ALL6 = Direction.values();

    /** 建物構造ラベル。 */
    public enum Label {
        /** 屋根 (天井面および頭上の構造物)。 */
        ROOF,
        /** 壁 (側面の垂直構造物)。 */
        WALL,
        /** 床 (足元の水平構造物)。 */
        FLOOR,
        /** 分類不能 (距離到達なし、または形状から確定できない)。 */
        UNKNOWN
    }

    /**
     * 屋根の大域形状。
     *
     * <p>現在は FLAT / SLOPE の2区分。SLOPE は勾配を伴う屋根 (切妻・寄棟・片流しなど) を
     * 総称し、後続のフェーズで GABLE / HIP / SINGLE_SLOPE 等へ細分類する余地を残します。
     */
    public enum RoofShape {
        /** 屋根ブロックなし。 */
        NONE,
        /** 平屋根 (heightmap 全て同高度)。 */
        FLAT,
        /** 勾配屋根 (heightmap に高低差あり、三角屋根等を含む)。 */
        SLOPE
    }

    /**
     * 指定された部屋空間に基づき建物構造分類を実行します。
     *
     * @param level ワールド
     * @param room  {@link RoomFloodFill} による部屋認識結果。{@code null} または
     *              非閉空間の場合は {@link Result#EMPTY} を返します。
     * @return 分類結果。入力が無効な場合は {@link Result#EMPTY}。
     */
    public static Result classify(BlockGetter level, RoomFloodFill.Result room) {
        if (level == null || room == null || !room.isEnclosed()) {
            return Result.EMPTY;
        }
        final LongSet airCells = room.getAirCells();
        if (airCells.isEmpty()) {
            return Result.EMPTY;
        }

        final BlockPos seed = room.getSeed();
        final int seedX = seed.getX();
        final int seedY = seed.getY();
        final int seedZ = seed.getZ();
        final int rxz = RoomFloodFill.MAX_RADIUS_XZ + T_MAX;
        final int ry = RoomFloodFill.MAX_RADIUS_Y + T_MAX;

        // ==================== Phase 0: 空気セルから floorY / ceilingY を推定 ====================
        int floorY = Integer.MAX_VALUE;
        int ceilingY = Integer.MIN_VALUE;
        LongIterator ai = airCells.iterator();
        while (ai.hasNext()) {
            int y = BlockPos.getY(ai.nextLong());
            if (y < floorY) floorY = y;
            if (y > ceilingY) ceilingY = y;
        }
        if (floorY > ceilingY) {
            return Result.EMPTY;
        }

        // ==================== Phase 1: 建物固体マス構築 (固体連結 BFS, 厚さ上限 T_MAX) ====================
        // shellCells を種に6方向へ固体を辿り、厚さ T_MAX まで拡張。
        // 外気 (通過可能 && !airCells) に当たったら打切り、内部空気 (airCells) は質量外。
        // これにより「空気隣接に依存しない固体連結補捉」で角の離脱ブロックも取り込む。
        final Long2IntOpenHashMap mass = new Long2IntOpenHashMap(Math.max(8, room.getShellCells().size() * 2));
        mass.defaultReturnValue(-1);
        final LongArrayList queue = new LongArrayList();
        LongIterator shellIt = room.getShellCells().iterator();
        while (shellIt.hasNext()) {
            long l = shellIt.nextLong();
            if (mass.put(l, 0) == -1) {
                queue.add(l);
            }
        }
        final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int head = 0;
        while (head < queue.size()) {
            long cur = queue.getLong(head++);
            int d = mass.get(cur);
            if (d >= T_MAX) {
                continue;
            }
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : ALL6) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();
                if (Math.abs(nx - seedX) > rxz || Math.abs(ny - seedY) > ry || Math.abs(nz - seedZ) > rxz) {
                    continue;
                }
                long nlong = BlockPos.asLong(nx, ny, nz);
                if (mass.containsKey(nlong)) {
                    continue;
                }
                if (airCells.contains(nlong)) {
                    // 内部空気は建物マスに含めない
                    continue;
                }
                mpos.set(nx, ny, nz);
                if (!WallAnalyzer.isSolid(level, mpos)) {
                    // 外気 — ここで打切り、拡張しない
                    continue;
                }
                mass.put(nlong, d + 1);
                queue.add(nlong);
            }
        }
        if (mass.isEmpty()) {
            return Result.EMPTY;
        }

        // ==================== Phase 2: シード・ラベリング ====================
        // ROOF_SEED:  直下が内部空気 (天井面の内側)
        // WALL_SEED:  水平4方向のいずれかが内部空気、かつ y ∈ [floorY, ceilingY]
        // FLOOR_SEED: 直上が内部空気、かつ y < floorY (床面の内側)
        final LongOpenHashSet roofSeeds = new LongOpenHashSet();
        final LongOpenHashSet wallSeeds = new LongOpenHashSet();
        final LongOpenHashSet floorSeeds = new LongOpenHashSet();
        LongIterator massIt = mass.keySet().iterator();
        while (massIt.hasNext()) {
            long b = massIt.nextLong();
            int bx = BlockPos.getX(b);
            int by = BlockPos.getY(b);
            int bz = BlockPos.getZ(b);

            long below = BlockPos.asLong(bx, by - 1, bz);
            if (airCells.contains(below)) {
                roofSeeds.add(b);
            }

            for (Direction d : HORIZONTAL) {
                long nb = BlockPos.asLong(bx + d.getStepX(), by, bz + d.getStepZ());
                if (airCells.contains(nb)) {
                    if (by >= floorY && by <= ceilingY) {
                        wallSeeds.add(b);
                    }
                    break;
                }
            }

            long above = BlockPos.asLong(bx, by + 1, bz);
            if (airCells.contains(above) && by < floorY) {
                floorSeeds.add(b);
            }
        }

        // ==================== Phase 3: 多始点 BFS 距離 + ラベル決定 ====================
        // 各シード群から建物マス上の最短ホップ数を算出し、最小距離のラベルを採用。
        // 同点時は y 座標で tiebreak (y > ceilingY → ROOF, y < floorY → FLOOR, 中間 → WALL)。
        Long2IntOpenHashMap dR = bfsDistance(mass.keySet(), roofSeeds);
        Long2IntOpenHashMap dW = bfsDistance(mass.keySet(), wallSeeds);
        Long2IntOpenHashMap dF = bfsDistance(mass.keySet(), floorSeeds);

        final Long2ObjectOpenHashMap<Label> labels = new Long2ObjectOpenHashMap<>(mass.size());
        LongIterator labelIt = mass.keySet().iterator();
        while (labelIt.hasNext()) {
            long b = labelIt.nextLong();
            int dr = dR.get(b);
            int dw = dW.get(b);
            int df = dF.get(b);
            if (dr < 0) dr = INF;
            if (dw < 0) dw = INF;
            if (df < 0) df = INF;
            int min = Math.min(dr, Math.min(dw, df));
            int y = BlockPos.getY(b);

            Label lbl;
            if (min == INF) {
                // 全シードから到達不能 — y で初期ラベル
                lbl = (y > ceilingY) ? Label.ROOF : (y < floorY ? Label.FLOOR : Label.UNKNOWN);
            } else {
                boolean rEq = dr == min;
                boolean wEq = dw == min;
                boolean fEq = df == min;
                int ties = (rEq ? 1 : 0) + (wEq ? 1 : 0) + (fEq ? 1 : 0);

                if (ties == 1) {
                    lbl = rEq ? Label.ROOF : (wEq ? Label.WALL : Label.FLOOR);
                } else if (ties == 2) {
                    if (rEq && wEq) {
                        lbl = (y > ceilingY) ? Label.ROOF : Label.WALL;
                    } else if (rEq && fEq) {
                        lbl = (y > ceilingY) ? Label.ROOF : Label.FLOOR;
                    } else { // wEq && fEq
                        lbl = (y < floorY) ? Label.FLOOR : Label.WALL;
                    }
                } else {
                    // 3 シードとも同点
                    if (y > ceilingY) {
                        lbl = Label.ROOF;
                    } else if (y < floorY) {
                        lbl = Label.FLOOR;
                    } else {
                        lbl = Label.WALL;
                    }
                }
            }
            labels.put(b, lbl);
        }

        // ==================== Phase 4: 屋根形状識別 (heightmap) ====================
        RoofShape roofShape = classifyRoofShape(labels);

        // ==================== 結果 AABB ====================
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        LongIterator aabbIt = mass.keySet().iterator();
        while (aabbIt.hasNext()) {
            long b = aabbIt.nextLong();
            int bx = BlockPos.getX(b);
            int by = BlockPos.getY(b);
            int bz = BlockPos.getZ(b);
            if (bx < minX) minX = bx;
            if (by < minY) minY = by;
            if (bz < minZ) minZ = bz;
            if (bx > maxX) maxX = bx;
            if (by > maxY) maxY = by;
            if (bz > maxZ) maxZ = bz;
        }

        return new Result(true, labels, roofShape, seed,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /**
     * 建物マス上で指定シード群からの多始点 BFS 距離を算出します。
     *
     * @param mass  建物固体マス (Phase 1 の keySet)
     * @param seeds シード位置集合
     * @return ブロック long → 距離。到達不能は default -1。
     */
    private static Long2IntOpenHashMap bfsDistance(LongSet mass, LongSet seeds) {
        Long2IntOpenHashMap d = new Long2IntOpenHashMap(Math.max(1, seeds.size()));
        d.defaultReturnValue(-1);
        LongArrayList q = new LongArrayList();
        LongIterator it = seeds.iterator();
        while (it.hasNext()) {
            long s = it.nextLong();
            if (mass.contains(s) && d.put(s, 0) == -1) {
                q.add(s);
            }
        }
        int head = 0;
        while (head < q.size()) {
            long cur = q.getLong(head++);
            int cd = d.get(cur);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : ALL6) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();
                long nlong = BlockPos.asLong(nx, ny, nz);
                if (!mass.contains(nlong) || d.containsKey(nlong)) {
                    continue;
                }
                d.put(nlong, cd + 1);
                q.add(nlong);
            }
        }
        return d;
    }

    /**
     * ROOF ラベル ブロックから heightmap (x,z) → max Y を構築し、
     * 高低差の有無で {@link RoofShape#FLAT} / {@link RoofShape#SLOPE} を判定します。
     */
    private static RoofShape classifyRoofShape(Long2ObjectOpenHashMap<Label> labels) {
        // heightmap: (x, 0, z) packed long → max Y
        Long2IntOpenHashMap heightmap = new Long2IntOpenHashMap();
        heightmap.defaultReturnValue(Integer.MIN_VALUE);

        var it = labels.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue() != Label.ROOF) {
                continue;
            }
            long b = e.getLongKey();
            int bx = BlockPos.getX(b);
            int by = BlockPos.getY(b);
            int bz = BlockPos.getZ(b);
            long xz = BlockPos.asLong(bx, 0, bz);
            int cur = heightmap.get(xz);
            if (by > cur) {
                heightmap.put(xz, by);
            }
        }
        if (heightmap.isEmpty()) {
            return RoofShape.NONE;
        }

        int hmin = Integer.MAX_VALUE;
        int hmax = Integer.MIN_VALUE;
        IntIterator hIt = heightmap.values().iterator();
        while (hIt.hasNext()) {
            int v = hIt.nextInt();
            if (v < hmin) hmin = v;
            if (v > hmax) hmax = v;
        }
        return (hmax - hmin == 0) ? RoofShape.FLAT : RoofShape.SLOPE;
    }

    /**
     * 建物構造分類結果。
     */
    public static final class Result {

        public static final Result EMPTY = new Result(false,
                Long2ObjectMaps.<Label>emptyMap(), RoofShape.NONE,
                BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO);

        private final boolean valid;
        private final Long2ObjectMap<Label> labels;
        private final RoofShape roofShape;
        private final BlockPos seed;
        private final BlockPos minPos;
        private final BlockPos maxPos;

        Result(boolean valid, Long2ObjectMap<Label> labels, RoofShape roofShape,
               BlockPos seed, BlockPos minPos, BlockPos maxPos) {
            this.valid = valid;
            this.labels = Objects.requireNonNull(labels);
            this.roofShape = Objects.requireNonNull(roofShape);
            this.seed = Objects.requireNonNull(seed).immutable();
            this.minPos = Objects.requireNonNull(minPos).immutable();
            this.maxPos = Objects.requireNonNull(maxPos).immutable();
        }

        /** 有効な分類結果かどうか。入力が非閉空間等の場合は false。 */
        public boolean isValid() {
            return valid;
        }

        /** ブロック long → {@link Label} の不変マップ。 */
        public Long2ObjectMap<Label> getLabels() {
            return labels;
        }

        /** 屋根形状。 */
        public RoofShape getRoofShape() {
            return roofShape;
        }

        /** 探索起点 (RoomFloodFill seed)。 */
        public BlockPos getSeed() {
            return seed;
        }

        /** 分類対象の AABB 最小座標。 */
        public BlockPos getMinPos() {
            return minPos;
        }

        /** 分類対象の AABB 最大座標。 */
        public BlockPos getMaxPos() {
            return maxPos;
        }
    }
}