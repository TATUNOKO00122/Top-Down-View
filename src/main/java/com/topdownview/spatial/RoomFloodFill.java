package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.Objects;

/**
 * 3D BFS (幅優先探索) による部屋形状・空間自動認識クラス。
 *
 * <p>プレイヤー足元を起点に空間をフラッドフィルスキャンし、
 * 通過可能な空気セル領域と、それを囲む1ブロック厚の壁殻 (Shell) セル領域を特定します。
 *
 * <p>各セルにおいて直上に固体の覆い(天井)があるかをローカルに判定し (葉ブロック等は透過)、
 * 覆いの無いセルで探索を打ち切ることで、密閉度ではなく「覆われた空気領域」を空間として抽出します。
 * 空(Heightmap)に依存しないため、複雑な形の家・中庭・洞窟も扱えます。
 *
 * <p>さらに「屋外で頭上に覆いがあるだけ」の空間 (オーバーハング・木陰・屋根付き広場) を
 * 屋内と誤認しないよう、覆いの無い横方向の空気に直接面するセル (露出セル) を領域から除外した上で、
 * 残った領域の水平境界に占める開放面の割合が {@link #MAX_OPEN_FACE_PERCENT}% を超えないことを
 * 屋内の条件にします。2方向以上が大きく開いた屋根付き構造は開放面が壁面を上回るため除外されます。
 *
 * <p>覆い判定は「直上から最初の固体に当たるまで」だけ縦走査します (天井が低いほど安価)。
 * 覆いが見つからなかったセルは {@link Scratch} に記録して再走査を避けます。
 */
public final class RoomFloodFill {

    private RoomFloodFill() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** XZ方向の最大探索半径（ブロック） */
    public static final int MAX_RADIUS_XZ = 24;

    /** Y方向の最大探索半径（ブロック） */
    public static final int MAX_RADIUS_Y = 12;

    /** BFSで探索する最大空気セル数（安全弁） */
    public static final int MAX_FLOOD_CELLS = 8000;

    /** 各セルで直上に覆い(固体天井)を探す走査距離 */
    public static final int CEILING_SCAN_HEIGHT = 24;

    /** 水平境界のうち開放面が占めてよい割合 (壁面に対する%)。超過した場合は屋外とみなす */
    public static final int MAX_OPEN_FACE_PERCENT = 40;

    /** 探索を行う6方向 */
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * フラッドフィル中に再利用する作業バッファ。
     *
     * <p>呼び出し側がスレッドごとに1つ保持することで、毎tickの再確保を避けます。
     * スレッドセーフではないため、複数スレッドから同時に使わないでください。
     */
    public static final class Scratch {
        private final LongOpenHashSet visitedAir = new LongOpenHashSet();
        private final LongOpenHashSet visitedShell = new LongOpenHashSet();
        private final LongOpenHashSet noCover = new LongOpenHashSet();
        /** 開口 (覆いの無い横方向の空気) に直接面した非連結エアセル。後段で領域から除外する */
        private final LongOpenHashSet exposedAir = new LongOpenHashSet();
        private final LongArrayList queue = new LongArrayList();
        private final BlockMap blockMap = new BlockMap();

        public void clear() {
            visitedAir.clear();
            visitedShell.clear();
            noCover.clear();
            exposedAir.clear();
            queue.clear();
        }

        /** この探索で共有するブロック判定キャッシュ。 */
        public BlockMap getBlockMap() {
            return blockMap;
        }
    }

    /**
     * 指定された起点から空間フラッドフィルを実行する。
     *
     * @param level ワールド
     * @param seed  起点となるブロック座標（通常プレイヤー足元）
     * @return 空間探索結果
     */
    public static Result compute(BlockGetter level, BlockPos seed) {
        return compute(level, seed, null);
    }

    /**
     * 作業バッファを再利用して空間フラッドフィルを実行する。
     *
     * @param level   ワールド
     * @param seed    起点となるブロック座標
     * @param scratch 再利用バッファ。{@code null} の場合は内部で生成。
     * @return 空間探索結果
     */
    public static Result compute(BlockGetter level, BlockPos seed, Scratch scratch) {
        if (level == null || seed == null) {
            return Result.EMPTY;
        }
        final Scratch s = (scratch != null) ? scratch : new Scratch();
        s.clear();
        // 起点を中心にブロック判定キャッシュを構築 (flood/classifier/stair で共有)
        s.blockMap.reset(level, seed);

        // 起点が通過可能かチェック。壁に埋まっている場合は頭上などを探す
        BlockPos startPos = findValidSeed(s.blockMap, seed);
        if (startPos == null) {
            return Result.EMPTY;
        }

        final int seedX = startPos.getX();
        final int seedY = startPos.getY();
        final int seedZ = startPos.getZ();

        // 起点自体に直上の覆いが無い場合は屋外として扱う
        if (!isCovered(s, seedX, seedY, seedZ)) {
            return Result.EMPTY;
        }

        final LongOpenHashSet visitedAir = s.visitedAir;
        final LongOpenHashSet visitedShell = s.visitedShell;
        final LongArrayList queue = s.queue;

        long startLong = startPos.asLong();
        visitedAir.add(startLong);
        queue.add(startLong);

        int minX = seedX;
        int minY = seedY;
        int minZ = seedZ;
        int maxX = seedX;
        int maxY = seedY;
        int maxZ = seedZ;

        int head = 0;

        while (head < queue.size() && visitedAir.size() < MAX_FLOOD_CELLS) {
            long currentLong = queue.getLong(head++);
            int cx = BlockPos.getX(currentLong);
            int cy = BlockPos.getY(currentLong);
            int cz = BlockPos.getZ(currentLong);

            // AABB更新
            if (cx < minX) minX = cx;
            if (cy < minY) minY = cy;
            if (cz < minZ) minZ = cz;
            if (cx > maxX) maxX = cx;
            if (cy > maxY) maxY = cy;
            if (cz > maxZ) maxZ = cz;

            // 6方向へ隣接セルを探索
            for (Direction dir : DIRECTIONS) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();

                // 範囲制限チェック
                if (Math.abs(nx - seedX) > MAX_RADIUS_XZ ||
                    Math.abs(ny - seedY) > MAX_RADIUS_Y ||
                    Math.abs(nz - seedZ) > MAX_RADIUS_XZ) {
                    continue;
                }

                long nlong = BlockPos.asLong(nx, ny, nz);

                if (visitedAir.contains(nlong) || visitedShell.contains(nlong)) {
                    continue;
                }

                boolean horizontal = dir.getStepY() == 0;

                // 固体ブロック（壁・床・天井）か通過可能空間かを判定
                if (s.blockMap.isSolid(nx, ny, nz)) {
                    // 閉じた扉などの連結部は、直上に覆いがあるなら屋内の通路として空気同様に扱い
                    // 探索を継続する。これがないと閉じた扉で建物が部屋ごとに分断される。
                    // 屋外のフェンスゲートが外を建物内へ取り込まないよう covered を要求する。
                    if (s.blockMap.isConnector(nx, ny, nz) && isCovered(s, nx, ny, nz)) {
                        visitedAir.add(nlong);
                        queue.add(nlong);
                    } else {
                        visitedShell.add(nlong);
                    }
                } else if (isCovered(s, nx, ny, nz)) {
                    // 直上に固体の覆いがある空気セル → 屋内/洞窟内部として探索継続
                    visitedAir.add(nlong);
                    queue.add(nlong);
                } else if (horizontal) {
                    // 横方向に覆いの無い空気に面する非連結セルは「露出セル」。
                    // 屋外へ開いた軒先・庇の外殻であり、後段の境界再計算で領域から除外する。
                    // 連結部 (扉) は除外せず、開口を挟んだ部屋間の連結を維持する
                    if (!s.blockMap.isConnector(cx, cy, cz)) {
                        s.exposedAir.add(currentLong);
                    }
                }
                // 覆いの無いセルは屋外へ漏れるためキューに入れず打ち切る
            }
        }

        // 開口に面した露出セルを領域から除外してから水平境界を数え直す。これにより
        // 窓・開口の外側にある軒下空間への「漏れ」が切り離され、その外周が壁面として
        // 誤カウントされない。除外済みセルに面する境界は開放面として数える。
        visitedAir.removeAll(s.exposedAir);

        int wallFaces = 0;
        int openFaces = 0;
        for (LongIterator airIt = visitedAir.iterator(); airIt.hasNext(); ) {
            long airLong = airIt.nextLong();
            int ax = BlockPos.getX(airLong);
            int ay = BlockPos.getY(airLong);
            int az = BlockPos.getZ(airLong);
            for (Direction dir : DIRECTIONS) {
                if (dir.getStepY() != 0) {
                    continue;
                }
                int nx = ax + dir.getStepX();
                int nz = az + dir.getStepZ();
                if (s.blockMap.isSolid(nx, ay, nz)) {
                    wallFaces++;
                } else if (!visitedAir.contains(BlockPos.asLong(nx, ay, nz))) {
                    openFaces++;
                }
            }
        }

        // 屋内 = 水平境界が壁面 (固体) で支配的であること (開放面が壁面の
        // MAX_OPEN_FACE_PERCENT% を超えない)。頭上に覆いがあるだけの屋外空間
        // (オーバーハング・木陰・屋根付き広場・開放型の屋根付き構造) は
        // 開放面が多いため屋内と判定しない。
        boolean enclosed = !visitedAir.isEmpty() && wallFaces > 0
                && openFaces * 100 <= wallFaces * MAX_OPEN_FACE_PERCENT;
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);

        return new Result(enclosed, visitedAir, visitedShell, min, max, startPos);
    }

    /**
     * 起点位置が通過可能か検証し、埋まっている場合は頭上など適切な空きスペースを返します。
     */
    private static BlockPos findValidSeed(BlockMap blockMap, BlockPos seed) {
        if (!blockMap.isSolid(seed.getX(), seed.getY(), seed.getZ())) {
            return seed;
        }
        // 足元が埋まっている場合、頭上(+1), +2 を試行
        for (int dy = 1; dy <= 2; dy++) {
            if (!blockMap.isSolid(seed.getX(), seed.getY() + dy, seed.getZ())) {
                return seed.above(dy);
            }
        }
        return null;
    }

    /**
     * 指定位置の直上 {@link #CEILING_SCAN_HEIGHT} 以内に固体の覆い(天井)があるか判定する。
     * 空気・葉・液体は覆いとみなさない(透過)。見つからなかったセルは記録して再走査を避ける。
     */
    private static boolean isCovered(Scratch s, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (s.noCover.contains(key)) {
            return false;
        }
        final int limit = y + CEILING_SCAN_HEIGHT;
        for (int yy = y + 1; yy <= limit; yy++) {
            if (s.blockMap.isCover(x, yy, z)) {
                return true;
            }
        }
        s.noCover.add(key);
        return false;
    }

    /**
     * 部屋空間探索結果オブジェクト。
     */
    public static final class Result {

        public static final Result EMPTY = new Result(
                false,
                LongSets.EMPTY_SET,
                LongSets.EMPTY_SET,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO
        );

        private final boolean enclosed;
        private final LongSet airCells;
        private final LongSet shellCells;
        private final BlockPos minPos;
        private final BlockPos maxPos;
        private final BlockPos seed;

        public Result(boolean enclosed, LongSet airCells, LongSet shellCells,
                      BlockPos minPos, BlockPos maxPos, BlockPos seed) {
            this.enclosed = enclosed;
            this.airCells = LongSets.unmodifiable(Objects.requireNonNull(airCells));
            this.shellCells = LongSets.unmodifiable(Objects.requireNonNull(shellCells));
            this.minPos = minPos.immutable();
            this.maxPos = maxPos.immutable();
            this.seed = seed.immutable();
        }

        /** 屋内閉空間と判定されたか */
        public boolean isEnclosed() {
            return enclosed;
        }

        /** 部屋内部の空気セル集合 (packed long) */
        public LongSet getAirCells() {
            return airCells;
        }

        /** 部屋を囲む壁殻セル集合 (packed long) */
        public LongSet getShellCells() {
            return shellCells;
        }

        /** 部屋のバウンディングボックス最小座標 */
        public BlockPos getMinPos() {
            return minPos;
        }

        /** 部屋のバウンディングボックス最大座標 */
        public BlockPos getMaxPos() {
            return maxPos;
        }

        /** 探索の起点座標 */
        public BlockPos getSeed() {
            return seed;
        }

        /** 天井が存在する最大Y座標 (AABBのmaxY) */
        public int getCeilingY() {
            return enclosed ? maxPos.getY() : SpaceProbe.NO_CEILING;
        }
    }
}
