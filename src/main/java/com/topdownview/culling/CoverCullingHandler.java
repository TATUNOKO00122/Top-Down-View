package com.topdownview.culling;

import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * プレイヤーが歩行できる列を探索し、その列を上方から覆うブロック（屋根・樹冠・オーバーハング）を
 * カリング対象として収集するハンドラー。
 *
 * <p>カメラ軸ベースの円柱/楔と異なり、壁や構造物（列自体が固体＝歩行不可）は残し、
 * 「歩ける地面の上を覆っているもの」だけを消す。室内では屋根だけが消え壁は残り、
 * 森では進行方向の地面上の樹冠が消える。
 */
public final class CoverCullingHandler {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** BFS で探索する最大列数（安全弁）。 */
    private static final int MAX_CELLS = 1500;

    /** 1列あたり上方へ走査する最大高さ（カメラYとの小さい方）。 */
    private static final int MAX_SCAN_HEIGHT = 32;

    /** 立位不可を表す sentinel。 */
    private static final int NOT_STANDABLE = Integer.MIN_VALUE;

    /** 再スキャンを起こす最小移動量（マンハッタン）。カリングキャッシュのクリア間隔と揃える。 */
    private static final int SCAN_MOVE_THRESHOLD = 3;

    /** 建物スライスで床上どれだけ残すか（この高さより上を消す）。 */
    private static final int SLICE_FLOOR_KEEP = 1;

    /**
     * カリング対象の覆いブロック。チャンクビルドワーカーから読まれるため、
     * 再構築した集合を volatile 参照ごと差し替えて読み書きの競合を避ける。
     */
    private volatile LongOpenHashSet coverCullPositions = new LongOpenHashSet();

    /**
     * 建物スライス。空間領域(部屋 airCells)の水平フットプリントに限定し、
     * プレイヤーの床 + {@link #SLICE_FLOOR_KEEP} 以上をカメラYまでまとめて消す。
     * 基準を天井ではなく床にすることで低い入口でも全体を切り抜かない。
     */
    private volatile LongOpenHashSet sliceColumns = new LongOpenHashSet();
    private volatile boolean sliceActive = false;
    private volatile int sliceMinY = 0;
    private volatile int sliceTopY = 0;

    private int lastScanX = Integer.MIN_VALUE;
    private int lastScanY = Integer.MIN_VALUE;
    private int lastScanZ = Integer.MIN_VALUE;
    private boolean lastSliceActive = false;

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public void clearCache() {
        if (!coverCullPositions.isEmpty()) {
            coverCullPositions = new LongOpenHashSet();
        }
        if (!sliceColumns.isEmpty()) {
            sliceColumns = new LongOpenHashSet();
        }
        sliceActive = false;
        lastScanX = Integer.MIN_VALUE;
        lastScanY = Integer.MIN_VALUE;
        lastScanZ = Integer.MIN_VALUE;
        lastSliceActive = false;
    }

    public boolean isCoverCullBlock(long posLong) {
        LongOpenHashSet set = coverCullPositions;
        return !set.isEmpty() && set.contains(posLong);
    }

    /**
     * 空間領域のフットプリント内で、床上 {@link #SLICE_FLOOR_KEEP} 以上をカリング対象とする。
     */
    public boolean isSliceCulled(int x, int y, int z) {
        if (!sliceActive || y < sliceMinY || y > sliceTopY) {
            return false;
        }
        LongOpenHashSet cols = sliceColumns;
        return !cols.isEmpty() && cols.contains(BlockPos.asLong(x, 0, z));
    }

    /** 天然の樹冠セットまたは固体の覆いスライスのいずれかに該当するか。 */
    public boolean isCoverCulled(BlockPos pos) {
        return isCoverCullBlock(pos.asLong()) || isSliceCulled(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * 空間領域(部屋)からスライスのフットプリントを更新し、併せて天然の樹冠(葉)を
     * 歩行可能列から再収集する。同じ位置では再計算しない（移動時のみ）。
     *
     * @param airCells 空間領域の空気セル(packed long)。空なら屋外。
     * @param eyeX     視点X（ビューシェッド判定の起点）
     * @param eyeY     視点Y
     * @param eyeZ     視点Z
     * @param cameraY  スライス上限に使うカメラY
     * @param radius   探索する水平半径（ブロック）
     * @param viewshed true なら視点から見える列だけを対象にする
     */
    public void update(BlockGetter level, int feetX, int feetY, int feetZ, LongSet airCells,
            double eyeX, double eyeY, double eyeZ, int cameraY, int radius, boolean viewshed) {
        if (level == null) {
            clearCache();
            return;
        }
        boolean sliceNow = airCells != null && !airCells.isEmpty();
        if (lastScanX != Integer.MIN_VALUE && sliceNow == lastSliceActive) {
            int move = Math.abs(feetX - lastScanX)
                    + Math.abs(feetY - lastScanY)
                    + Math.abs(feetZ - lastScanZ);
            if (move < SCAN_MOVE_THRESHOLD) {
                return;
            }
        }
        lastSliceActive = sliceNow;
        lastScanX = feetX;
        lastScanY = feetY;
        lastScanZ = feetZ;

        if (sliceNow) {
            sliceMinY = feetY + SLICE_FLOOR_KEEP;
            sliceTopY = Math.min(cameraY, sliceMinY + MAX_SCAN_HEIGHT);
            sliceColumns = buildFootprint(airCells);
            sliceActive = true;
        } else {
            sliceActive = false;
            if (!sliceColumns.isEmpty()) {
                sliceColumns = new LongOpenHashSet();
            }
        }

        LongOpenHashSet next = new LongOpenHashSet();

        int startY = findStandableY(level, feetX, feetY, feetZ);
        if (startY == NOT_STANDABLE) {
            coverCullPositions = next;
            return;
        }

        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();
        long start = BlockPos.asLong(feetX, startY, feetZ);
        visited.add(start);
        queue.add(start);

        int head = 0;
        while (head < queue.size() && visited.size() < MAX_CELLS) {
            long cur = queue.getLong(head++);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);

            addColumnCover(level, cx, cy, cz, eyeX, eyeY, eyeZ, cameraY, viewshed, next);

            for (Direction dir : HORIZONTAL) {
                int nx = cx + dir.getStepX();
                int nz = cz + dir.getStepZ();
                if (Math.abs(nx - feetX) > radius || Math.abs(nz - feetZ) > radius) {
                    continue;
                }
                int ny = findStandableY(level, nx, cy, nz);
                if (ny == NOT_STANDABLE) {
                    continue;
                }
                long nlong = BlockPos.asLong(nx, ny, nz);
                if (visited.add(nlong)) {
                    queue.add(nlong);
                }
            }
        }

        coverCullPositions = next;
    }

    /**
     * 立位列の 2 ブロック上空からカメラYまで、最初の覆いが葉(自然の樹冠)のときだけ収集する。
     * 固体の覆い(建物屋根・洞窟天井)はスライス({@link #isSliceCulled})側で処理する。
     * 地表高(Heightmap)より上には何も無いため、走査上限を地表に丸めて屋外列を早期に打ち切る。
     * viewshed が有効な場合は、視点からその列の地面が見えるものだけを対象にする。
     */
    private void addColumnCover(BlockGetter level, int x, int feetY, int z,
            double eyeX, double eyeY, double eyeZ, int cameraY, boolean viewshed, LongOpenHashSet out) {
        if (viewshed && !isVisibleFromEye(level, x, feetY, z, eyeX, eyeY, eyeZ)) {
            return;
        }
        int top = Math.min(cameraY, feetY + MAX_SCAN_HEIGHT);
        if (level instanceof LevelReader reader) {
            top = Math.min(top, reader.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
        }
        if (!firstCoverIsNatural(level, x, feetY, z, top)) {
            return;
        }
        for (int y = feetY + 2; y <= top; y++) {
            mutablePos.set(x, y, z);
            BlockState state = level.getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            out.add(mutablePos.asLong());
        }
    }

    /**
     * 空間領域(airCells)の水平フットプリント列を作る。壁を含めるため各air列の4近傍も加える。
     * 列は (x, 0, z) でパックする。
     */
    private LongOpenHashSet buildFootprint(LongSet airCells) {
        LongOpenHashSet columns = new LongOpenHashSet(Math.max(16, airCells.size()));
        LongIterator it = airCells.iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            int x = BlockPos.getX(cell);
            int z = BlockPos.getZ(cell);
            columns.add(BlockPos.asLong(x, 0, z));
            columns.add(BlockPos.asLong(x + 1, 0, z));
            columns.add(BlockPos.asLong(x - 1, 0, z));
            columns.add(BlockPos.asLong(x, 0, z + 1));
            columns.add(BlockPos.asLong(x, 0, z - 1));
        }
        return columns;
    }

    /**
     * 立位列の最初の覆いが葉(自然の樹冠)かどうかを返す。覆いが無ければ false。
     */
    private boolean firstCoverIsNatural(BlockGetter level, int x, int feetY, int z, int top) {
        for (int y = feetY + 2; y <= top; y++) {
            mutablePos.set(x, y, z);
            BlockState state = level.getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            return state.is(BlockTags.LEAVES);
        }
        return false;
    }

    /**
     * 視点から対象列の地面(胸の高さ)までの視線をサンプリングし、固体で遮られていないかを判定する。
     * 屋根・樹冠は視線より上にあるため遮蔽物にはならない。
     */
    private boolean isVisibleFromEye(BlockGetter level, int x, int feetY, int z,
            double eyeX, double eyeY, double eyeZ) {
        double targetX = x + 0.5;
        double targetY = feetY + 0.9;
        double targetZ = z + 0.5;
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0E-4) {
            return true;
        }
        int steps = (int) Math.ceil(dist / 0.5);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            mutablePos.set(
                    (int) Math.floor(eyeX + dx * t),
                    (int) Math.floor(eyeY + dy * t),
                    (int) Math.floor(eyeZ + dz * t));
            if (WallAnalyzer.isSolid(level, mutablePos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 指定列で立位可能なYを周辺±1から探す。階段・坂道の段差に対応。
     */
    private int findStandableY(BlockGetter level, int x, int aroundY, int z) {
        int y = findStandableAt(level, x, aroundY, z);
        if (y != NOT_STANDABLE) {
            return y;
        }
        y = findStandableAt(level, x, aroundY + 1, z);
        if (y != NOT_STANDABLE) {
            return y;
        }
        return findStandableAt(level, x, aroundY - 1, z);
    }

    private int findStandableAt(BlockGetter level, int x, int feetY, int z) {
        mutablePos.set(x, feetY - 1, z);
        if (!WallAnalyzer.isSolid(level, mutablePos)) {
            return NOT_STANDABLE;
        }
        // 足元と頭上の2ブロックが実際に通行可能(衝突なし)であること
        mutablePos.set(x, feetY, z);
        if (!isPassable(level, mutablePos)) {
            return NOT_STANDABLE;
        }
        mutablePos.set(x, feetY + 1, z);
        if (!isPassable(level, mutablePos)) {
            return NOT_STANDABLE;
        }
        return feetY;
    }

    /** プレイヤーが通行できる空間か(衝突形状が空)。葉など衝突を持つブロックは通行不可。 */
    private boolean isPassable(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }
}
