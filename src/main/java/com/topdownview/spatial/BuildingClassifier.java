package com.topdownview.spatial;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
 *   <li><b>Phase 3 多始点距離比較</b> — 建物マス上で ROOF/WALL/FLOOR を1本の多始点 BFS で
 *       同時伝播し、各セルの最小距離と到達種マスクからラベルを決定 (従来の種別3回 BFS と等価)。
 *       同点時は y 座標で tiebreak。</li>
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

    /** 多始点 BFS で「そのセルに最小距離で到達した種」を表すビット。 */
    private static final int BIT_ROOF = 1;
    private static final int BIT_WALL = 2;
    private static final int BIT_FLOOR = 4;
    /** 種マスクのビット幅 (3bit)。 */
    private static final int TYPE_MASK = BIT_ROOF | BIT_WALL | BIT_FLOOR;

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
     * @param room     {@link RoomFloodFill} による部屋認識結果。{@code null} または
     *                 非閉空間の場合は {@link Result#EMPTY} を返します。
     * @param blockMap 固体判定キャッシュ。この探索中に構築されたものを再利用する。
     * @return 分類結果。入力が無効な場合は {@link Result#EMPTY}。
     */
    public static Result classify(RoomFloodFill.Result room, BlockMap blockMap) {
        if (room == null || blockMap == null || !room.isEnclosed()) {
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
                if (!blockMap.isSolid(nx, ny, nz)) {
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

        // ==================== Phase 3: 多始点 BFS (距離 + 種マスク) + ラベル決定 ====================
        // ROOF/WALL/FLOOR を1本の多始点 BFS で伝播し、各セルが最小距離で到達した種のビットマスクを
        // 記録する。シード群ごとに BFS を3回走らせる従来方式と等価な結果を1回で得る。
        // 同点時は y 座標で tiebreak (y > ceilingY → ROOF, y < floorY → FLOOR, 中間 → WALL)。
        Long2LongOpenHashMap arrival = bfsArrival(mass.keySet(), roofSeeds, wallSeeds, floorSeeds);

        final Long2ObjectOpenHashMap<Label> labels = new Long2ObjectOpenHashMap<>(mass.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        LongIterator labelIt = mass.keySet().iterator();
        while (labelIt.hasNext()) {
            long b = labelIt.nextLong();
            int bx = BlockPos.getX(b);
            int by = BlockPos.getY(b);
            int bz = BlockPos.getZ(b);
            if (bx < minX) minX = bx;
            if (by < minY) minY = by;
            if (bz < minZ) minZ = bz;
            if (bx > maxX) maxX = bx;
            if (by > maxY) maxY = by;
            if (bz > maxZ) maxZ = bz;

            long packed = arrival.get(b);
            Label lbl;
            if (packed == 0L) {
                // 全シードから到達不能 — y で初期ラベル
                lbl = (by > ceilingY) ? Label.ROOF : (by < floorY ? Label.FLOOR : Label.UNKNOWN);
            } else {
                int mask = (int) (packed & TYPE_MASK);
                boolean rEq = (mask & BIT_ROOF) != 0;
                boolean wEq = (mask & BIT_WALL) != 0;
                boolean fEq = (mask & BIT_FLOOR) != 0;
                int ties = (rEq ? 1 : 0) + (wEq ? 1 : 0) + (fEq ? 1 : 0);

                if (ties == 1) {
                    lbl = rEq ? Label.ROOF : (wEq ? Label.WALL : Label.FLOOR);
                } else if (ties == 2) {
                    if (rEq && wEq) {
                        lbl = (by > ceilingY) ? Label.ROOF : Label.WALL;
                    } else if (rEq && fEq) {
                        lbl = (by > ceilingY) ? Label.ROOF : Label.FLOOR;
                    } else { // wEq && fEq
                        lbl = (by < floorY) ? Label.FLOOR : Label.WALL;
                    }
                } else {
                    // 3 シードとも同点
                    if (by > ceilingY) {
                        lbl = Label.ROOF;
                    } else if (by < floorY) {
                        lbl = Label.FLOOR;
                    } else {
                        lbl = Label.WALL;
                    }
                }
            }
            labels.put(b, lbl);
        }

        // 厚い屋根/床を垂直方向へ伸長して補正する。
        // 距離 BFS だけでは厚い構造の中間層が、より近い壁シードに引っ張られて WALL に誤分類される。
        extendVerticalLayers(labels, mass.keySet(), roofSeeds, wallSeeds, seedY - ry);

        // ==================== Phase 4: 屋根形状識別 (heightmap) ====================
        RoofShape roofShape = classifyRoofShape(labels);

        return new Result(true, labels, roofShape, seed,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /**
     * 建物マス上で ROOF/WALL/FLOOR の3種を同時に多始点 BFS し、各セルの
     * 「最小距離」と「その距離で到達した種のビットマスク」を1パスで求める。
     *
     * <p>パック形式: {@code (distance << 3) | typeMask}。未到達セルは 0 (距離0は必ずビットが立つため区別できる)。
     *
     * @param mass       建物固体マス (Phase 1 の keySet)
     * @param roofSeeds  ROOF シード
     * @param wallSeeds  WALL シード
     * @param floorSeeds FLOOR シード
     * @return ブロック long → パック済み到達情報。
     */
    private static Long2LongOpenHashMap bfsArrival(LongSet mass,
            LongSet roofSeeds, LongSet wallSeeds, LongSet floorSeeds) {
        final Long2LongOpenHashMap arrival = new Long2LongOpenHashMap(Math.max(8, mass.size() / 2));
        arrival.defaultReturnValue(0L);
        final LongArrayList q = new LongArrayList();

        seedGroup(mass, roofSeeds, BIT_ROOF, arrival, q);
        seedGroup(mass, wallSeeds, BIT_WALL, arrival, q);
        seedGroup(mass, floorSeeds, BIT_FLOOR, arrival, q);

        int head = 0;
        while (head < q.size()) {
            long cur = q.getLong(head++);
            long packed = arrival.get(cur);
            int cd = (int) (packed >>> 3);
            int mask = (int) (packed & TYPE_MASK);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : ALL6) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();
                long nlong = BlockPos.asLong(nx, ny, nz);
                if (!mass.contains(nlong)) {
                    continue;
                }
                long nbPacked = arrival.get(nlong);
                if (nbPacked == 0L) {
                    arrival.put(nlong, ((long) (cd + 1) << 3) | mask);
                    q.add(nlong);
                } else if ((int) (nbPacked >>> 3) == cd + 1) {
                    // 同距離で別種も到達 → マスクを統合
                    int merged = (int) (nbPacked & TYPE_MASK) | mask;
                    if (merged != (int) (nbPacked & TYPE_MASK)) {
                        arrival.put(nlong, ((long) (cd + 1) << 3) | merged);
                    }
                }
                // 既に短い距離で到達済みならスキップ
            }
        }
        return arrival;
    }

    /** シード群を BFS 初期キューへ登録する。既に登録済みなら種ビットを統合する。 */
    private static void seedGroup(LongSet mass, LongSet seeds, int bit,
            Long2LongOpenHashMap arrival, LongArrayList q) {
        LongIterator it = seeds.iterator();
        while (it.hasNext()) {
            long s = it.nextLong();
            if (!mass.contains(s)) {
                continue;
            }
            long cur = arrival.get(s);
            if (cur == 0L) {
                arrival.put(s, bit);
                q.add(s);
            } else if ((cur & bit) == 0L) {
                arrival.put(s, cur | bit);
            }
        }
    }

    /**
     * 列ごとにラベルを並べ、ROOF を上方へ、FLOOR を下方へ連続する層まで伸長する。
     *
     * <p>多始点距離 BFS だけでは、厚い屋根や床の中間層がより近い壁シードに引っ張られて
     * WALL と誤分類される。屋根/床から垂直に連続し、かつ壁シードでない層を同じラベルへ
     * そろえることで、厚みのある構造でも天井・床のタグが正しく付く。
     */
    private static void extendVerticalLayers(Long2ObjectOpenHashMap<Label> labels, LongSet mass,
            LongSet roofSeeds, LongSet wallSeeds, int baseY) {
        // 各列の Y 占有を bitmask 化する。mass は seedY±(MAX_RADIUS_Y+T_MAX) に収まるため
        // 1列あたり最大 2*(12+4)+1 = 33 bit で long に収まり、リスト生成とソートが不要になる。
        final Long2LongOpenHashMap columnMask = new Long2LongOpenHashMap();
        LongIterator it = mass.iterator();
        while (it.hasNext()) {
            long b = it.nextLong();
            int idx = BlockPos.getY(b) - baseY;
            if (idx < 0 || idx >= Long.SIZE) {
                continue;
            }
            long col = BlockPos.asLong(BlockPos.getX(b), 0, BlockPos.getZ(b));
            columnMask.put(col, columnMask.get(col) | (1L << idx));
        }

        for (var entry : columnMask.long2LongEntrySet()) {
            final int x = BlockPos.getX(entry.getLongKey());
            final int z = BlockPos.getZ(entry.getLongKey());
            final long mask = entry.getLongValue();

            int lastY = Integer.MIN_VALUE;
            boolean roofRun = false;
            long m = mask;
            while (m != 0L) {
                int i = Long.numberOfTrailingZeros(m);
                m &= ~(1L << i);
                int y = baseY + i;
                long b = BlockPos.asLong(x, y, z);
                Label lbl = labels.get(b);
                if (lastY == Integer.MIN_VALUE || y - lastY != 1) {
                    roofRun = false;
                }
                lastY = y;
                if (lbl == Label.ROOF) {
                    roofRun = true;
                } else if (roofRun && (lbl == Label.WALL || lbl == Label.UNKNOWN) && !wallSeeds.contains(b)) {
                    labels.put(b, Label.ROOF);
                } else {
                    roofRun = false;
                }
            }

            lastY = Integer.MIN_VALUE;
            boolean floorRun = false;
            m = mask;
            while (m != 0L) {
                int i = 63 - Long.numberOfLeadingZeros(m);
                m &= ~(1L << i);
                int y = baseY + i;
                long b = BlockPos.asLong(x, y, z);
                Label lbl = labels.get(b);
                if (lastY == Integer.MIN_VALUE || lastY - y != 1) {
                    floorRun = false;
                }
                lastY = y;
                if (lbl == Label.FLOOR) {
                    floorRun = true;
                } else if (floorRun && (lbl == Label.WALL || lbl == Label.UNKNOWN)
                        && !wallSeeds.contains(b) && !roofSeeds.contains(b)) {
                    labels.put(b, Label.FLOOR);
                } else {
                    floorRun = false;
                }
            }
        }
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