package com.topdownview.culling;

import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * プレイヤーが歩行できる列を探索し、その列を覆う最初のブロック（屋根・天井・樹冠・
 * オーバーハング）とその上方をまとめてカリング対象として収集するハンドラー。
 *
 * <p>床上からではなく「最初の覆い」を基準に、覆いより上だけを消す。覆いより下の壁・
 * 間仕切り・床は残るため間取りが露出せず、覆いより上の多層の屋根や上階もいっしょに
 * 消えるので、上から見下ろしたときにプレイヤーと足元の通路が見える。
 *
 * <p>固体の覆い（建物の屋根・天井）はプレイヤーが屋内（閉鎖空間）にいるとき（または設定で
 * 屋外カリングが有効なとき）だけ消す。屋外で設定が無効なときは、近くの歩行可能列が軒下などに
 * 入っていても屋根を消さない（屋外から見た屋根に穴が開くのを防ぐ）。葉（自然の樹冠）は屋内外を問わず消す。
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


    /**
     * 覆い集合(カリング対象の全ブロック)。時差開始は廃止し、走査が確定した瞬間にメッシュから
     * 消える。フェードは集合の差分(消失/復元フラッシュ)で表示側が担うため、ここは判定だけを
     * 持つ。ワーカーから読むため volatile 参照を差し替える。
     */
    private volatile LongOpenHashSet coverCullPositions = new LongOpenHashSet();

    /** 覆い集合が入れ替わるたびに進む世代。再構築ボックスのトリガに使う。 */
    private long generation;

    private int lastScanX = Integer.MIN_VALUE;
    private int lastScanY = Integer.MIN_VALUE;
    private int lastScanZ = Integer.MIN_VALUE;

    /** 直近の走査で使った探索半径。毎tickの距離保持判定に使う。メインスレッド専用。 */
    private int lastRadius = -1;

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    /** 覆い集合の世代番号。値が変わればカリング結果が変わり得る。 */
    public long getGeneration() {
        return generation;
    }

    /** 復元対象(集合から外れた覆い)の受け渡し。メインスレッド専用。 */
    private final LongOpenHashSet droppedPositions = new LongOpenHashSet();

    /** 指定位置が覆いカリング対象か。 */
    public boolean isCoverCulled(long posLong) {
        return coverCullPositions.contains(posLong);
    }

    public boolean isCoverCulled(BlockPos pos) {
        return isCoverCulled(pos.asLong());
    }

    public void clearCache() {
        // この時点で消えている覆いは全て復元対象にする(モード切替・空間離脱の即時ポップを避ける)
        droppedPositions.addAll(coverCullPositions);
        coverCullPositions = new LongOpenHashSet();
        if (!droppedPositions.isEmpty()) {
            generation++;
        }
        lastScanX = Integer.MIN_VALUE;
        lastScanY = Integer.MIN_VALUE;
        lastScanZ = Integer.MIN_VALUE;
        lastRadius = -1;
    }

    /**
     * 覆いカリング対象の全ブロックを out に積む。遷移フェードのイベント源で、
     * 走査ごとに参照する。
     */
    public void addOverdueCullPositions(LongOpenHashSet out) {
        out.addAll(coverCullPositions);
    }

    /**
     * 歩行可能列を探索し、各列の最初の覆い以降をカリング対象として収集する。
     * 同じ位置では再計算しない（移動時のみ）。
     *
     * @param enclosed           プレイヤーが閉鎖空間（屋内）にいるか
     * @param eyeX               視点X（ビューシェッド判定の起点）
     * @param eyeY               視点Y
     * @param eyeZ               視点Z
     * @param cameraY            走査上限に使うカメラY
     * @param radius             探索する水平半径（ブロック）
     * @param viewshed           true なら視点から見える列だけを対象にする
     * @param cullSolidsOutdoors true なら屋外でも固体の覆い（屋根・天井）をカリングする
     */
    public void update(BlockGetter level, int feetX, int feetY, int feetZ, boolean enclosed,
            double eyeX, double eyeY, double eyeZ, int cameraY, int radius, boolean viewshed,
            boolean cullSolidsOutdoors) {
        if (level == null) {
            clearCache();
            return;
        }
        lastRadius = radius;
        if (lastScanX != Integer.MIN_VALUE) {
            int move = Math.abs(feetX - lastScanX)
                    + Math.abs(feetY - lastScanY)
                    + Math.abs(feetZ - lastScanZ);
            if (move < SCAN_MOVE_THRESHOLD) {
                return;
            }
        }
        lastScanX = feetX;
        lastScanY = feetY;
        lastScanZ = feetZ;

        LongOpenHashSet next = new LongOpenHashSet();

        boolean allowSolids = enclosed || cullSolidsOutdoors;

        int startY = findStandableY(level, feetX, feetY, feetZ);
        if (startY == NOT_STANDABLE) {
            applyCollected(next);
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

            addColumnCover(level, cx, cy, cz, allowSolids, eyeX, eyeY, eyeZ, cameraY, viewshed, next);

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

        applyCollected(next);
    }

    /**
     * 収集した覆い集合へ差し替える。集合から外れたブロックは即座に復元対象にする。
     */
    private void applyCollected(LongOpenHashSet next) {
        LongOpenHashSet previous = coverCullPositions;
        LongIterator dropped = previous.iterator();
        while (dropped.hasNext()) {
            long posLong = dropped.nextLong();
            if (!next.contains(posLong)) {
                droppedPositions.add(posLong);
            }
        }

        if (!next.equals(previous)) {
            generation++;
        }
        coverCullPositions = next;
    }

    /** 復元フェード用: 集合から外れた覆いを out に移して返す(メインスレッド専用)。 */
    public void takeDroppedPositions(LongOpenHashSet out) {
        if (droppedPositions.isEmpty()) {
            return;
        }
        out.addAll(droppedPositions);
        droppedPositions.clear();
    }

    /**
     * 走査の合間に、探索半径を越えて離れた覆いを即座に復元対象にする（毎tick判定）。
     */
    public void updateRetention(int playerBlockX, int playerBlockZ) {
        LongOpenHashSet current = coverCullPositions;
        if (current.isEmpty() || lastRadius < 0) {
            return;
        }
        int limit = lastRadius;
        // 期限切れが無ければ集合を作り直さない(毎tickの無駄なアロケーション回避)。
        boolean anyDropped = false;
        LongIterator probe = current.iterator();
        while (probe.hasNext()) {
            if (edgeDistance(probe.nextLong(), playerBlockX, playerBlockZ) > limit) {
                anyDropped = true;
                break;
            }
        }
        if (!anyDropped) {
            return;
        }
        LongOpenHashSet kept = new LongOpenHashSet(current.size());
        LongIterator iterator = current.iterator();
        while (iterator.hasNext()) {
            long posLong = iterator.nextLong();
            if (edgeDistance(posLong, playerBlockX, playerBlockZ) <= limit) {
                kept.add(posLong);
            } else {
                droppedPositions.add(posLong);
            }
        }
        coverCullPositions = kept;
        generation++;
    }

    /** プレイヤーのブロック座標からの水平チェビシェフ距離。 */
    private static int edgeDistance(long posLong, int refX, int refZ) {
        return Math.max(Math.abs(BlockPos.getX(posLong) - refX), Math.abs(BlockPos.getZ(posLong) - refZ));
    }

    /**
     * 立位列の最初の覆い(足元+2より上で最初の天井形状ブロック)から、カメラYまたは地表までを
     * 収集する。覆いより下(壁・間仕切り・床)は残すため間取りは露出しない。
     *
     * <p>最初の覆いが固体(建物の屋根・天井)のときは {@code enclosed} のときだけ収集する。
     * 屋外では近くの歩行可能列が軒下に入っていても屋根を消さない。葉(自然の樹冠)は常に収集する。
     * viewshed が有効な場合は、視点からその列の地面が見えるものだけを対象にする。
     */
    private void addColumnCover(BlockGetter level, int x, int feetY, int z, boolean allowSolids,
            double eyeX, double eyeY, double eyeZ, int cameraY, boolean viewshed, LongOpenHashSet out) {
        if (viewshed && !isVisibleFromEye(level, x, feetY, z, eyeX, eyeY, eyeZ)) {
            return;
        }
        int top = Math.min(cameraY, feetY + MAX_SCAN_HEIGHT);
        if (level instanceof LevelReader reader) {
            top = Math.min(top, reader.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
        }
        boolean covered = false;
        for (int y = feetY + 2; y <= top; y++) {
            mutablePos.set(x, y, z);
            BlockState state = level.getBlockState(mutablePos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            // フェンス・ランタンなどの細い縦構造は上を遮らない。覆い候補から除外して
            // その列の本当の天井・屋根を探す。
            if (!WallAnalyzer.isCeilingLike(level, mutablePos, state)) {
                continue;
            }
            if (!covered) {
                // 最初の覆いが固体で屋外(固体カリング無効)なら、その列の屋根は残す
                if (!allowSolids && !state.is(BlockTags.LEAVES)) {
                    return;
                }
                covered = true;
            }
            out.add(mutablePos.asLong());
        }
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
