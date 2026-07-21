package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/**
 * 3D BFS (幅優先探索) による部屋形状・空間自動認識クラス。
 *
 * <p>プレイヤー足元を起点に空間をフラッドフィルスキャンし、
 * 通過可能な空気セル領域と、それを囲む1ブロック厚の壁殻 (Shell) セル領域を特定します。
 *
 * <p>各セルにおいて上方に本物の屋根があるかを自動チェック (葉ブロック等は透過) し、
 * 屋根がない屋外方向への無駄な漏れ出しを自然に防止します。
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

    /** 各セルで天井を探す上方走査距離 */
    public static final int CEILING_SCAN_HEIGHT = 10;

    /** 探索を行う6方向 */
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * 指定された起点から空間フラッドフィルを実行する。
     *
     * @param level ワールド
     * @param seed  起点となるブロック座標（通常プレイヤー足元）
     * @return 空間探索結果
     */
    public static Result compute(BlockGetter level, BlockPos seed) {
        if (level == null || seed == null) {
            return Result.EMPTY;
        }

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        // 起点が通過可能かチェック。壁に埋まっている場合は頭上などを探す
        BlockPos startPos = findValidSeed(level, seed, mpos);
        if (startPos == null) {
            return Result.EMPTY;
        }

        // 起点自体が屋外に開放されているかチェック
        if (openToSky(level, mpos, startPos.getX(), startPos.getY(), startPos.getZ())) {
            return Result.EMPTY;
        }

        LongOpenHashSet visitedAir = new LongOpenHashSet();
        LongOpenHashSet visitedShell = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();

        long startLong = startPos.asLong();
        visitedAir.add(startLong);
        queue.add(startLong);

        int minX = startPos.getX();
        int minY = startPos.getY();
        int minZ = startPos.getZ();
        int maxX = startPos.getX();
        int maxY = startPos.getY();
        int maxZ = startPos.getZ();

        int seedX = startPos.getX();
        int seedY = startPos.getY();
        int seedZ = startPos.getZ();

        int head = 0;
        int skyStops = 0;

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

                mpos.set(nx, ny, nz);
                long nlong = mpos.asLong();

                if (visitedAir.contains(nlong) || visitedShell.contains(nlong)) {
                    continue;
                }

                // 固体ブロック（壁・床・天井）か通過可能空間かを判定
                if (WallAnalyzer.isSolid(level, mpos)) {
                    // 固体ブロックは壁殻 (Shell) として記録（キューへは入れない）
                    visitedShell.add(nlong);
                } else {
                    // 通過可能空間の場合、真上に本物の屋根があるか確認
                    if (openToSky(level, mpos, nx, ny, nz)) {
                        // 空が開けているセルは skyStops をカウントし、キューには入れない（探索打ち切り）
                        skyStops++;
                    } else {
                        // 屋根のある部屋内部の空気セル → 探索継続
                        visitedAir.add(nlong);
                        queue.add(nlong);
                    }
                }
            }
        }

        // 屋外判定: 空への開口数が多く、壁シェルに対する比率が高い場合は屋外と判定
        if (skyStops > 8 && skyStops * 3 > visitedShell.size()) {
            return Result.EMPTY;
        }

        boolean enclosed = !visitedAir.isEmpty();
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);

        return new Result(enclosed, visitedAir, visitedShell, min, max, startPos);
    }

    /**
     * 起点位置が通過可能か検証し、埋まっている場合は頭上など適切な空きスペースを返します。
     */
    private static BlockPos findValidSeed(BlockGetter level, BlockPos seed, BlockPos.MutableBlockPos mpos) {
        mpos.set(seed);
        if (!WallAnalyzer.isSolid(level, mpos)) {
            return seed;
        }
        // 足元が埋まっている場合、頭上(+1), +2 を試行
        for (int dy = 1; dy <= 2; dy++) {
            mpos.set(seed.getX(), seed.getY() + dy, seed.getZ());
            if (!WallAnalyzer.isSolid(level, mpos)) {
                return seed.above(dy);
            }
        }
        return null;
    }

    /**
     * 指定位置から上方に「本物の屋根」があるか判定する。
     * 葉ブロック (LeavesBlock) は屋根とみなさず透過する。
     */
    private static boolean openToSky(BlockGetter level, BlockPos.MutableBlockPos mpos, int x, int y, int z) {
        if (level instanceof Level world) {
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y >= topY) {
                return true;
            }
            int limit = Math.min(topY, y + CEILING_SCAN_HEIGHT);
            for (int yy = y + 1; yy < limit; yy++) {
                mpos.set(x, yy, z);
                BlockState st = level.getBlockState(mpos);
                if (st.isAir() || st.is(BlockTags.LEAVES)) {
                    continue;
                }
                VoxelShape shape = st.getCollisionShape(level, mpos, CollisionContext.empty());
                if (!shape.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        // Level でない場合のフォールバック走査
        int limit = y + CEILING_SCAN_HEIGHT;
        for (int yy = y + 1; yy <= limit; yy++) {
            mpos.set(x, yy, z);
            BlockState st = level.getBlockState(mpos);
            if (st.isAir() || st.is(BlockTags.LEAVES)) {
                continue;
            }
            VoxelShape shape = st.getCollisionShape(level, mpos, CollisionContext.empty());
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
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

