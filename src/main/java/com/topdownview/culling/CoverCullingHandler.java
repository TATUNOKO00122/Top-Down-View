package com.topdownview.culling;

import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
 * <p>固体の覆い（建物の屋根・天井）はプレイヤーが屋内（閉鎖空間）にいるときだけ消す。
 * 屋外で近くの歩行可能列が軒下などに入っていても屋根を消さない（屋外から見た屋根に
 * 穴が開くのを防ぐ）。葉（自然の樹冠）は屋内外を問わず消す。
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
     * 覆い集合が入れ替わってから、全ブロックが消えるまでの猶予。プレイヤーからの距離に比例させて
     * 開始時刻をずらすことで、塊ではなく近い順に1ブロックずつ消える。
     */
    private static final long RELEASE_WINDOW_MS = 700L;

    /**
     * 覆いブロックとそのカリング開始時刻(ms)。集合全体を一斉に消すと樹冠などが塊で
     * 消えるため、ブロックごとに開始時刻をずらす。ワーカーから読むため volatile 参照を差し替える。
     */
    private volatile Long2LongMap coverCullDeadlines = new Long2LongOpenHashMap();

    /** 未カリングのブロックが残る最終時刻(ms)。これを過ぎたら再構築を強制する必要はない。 */
    private volatile long releaseEndMillis;

    private int lastScanX = Integer.MIN_VALUE;
    private int lastScanY = Integer.MIN_VALUE;
    private int lastScanZ = Integer.MIN_VALUE;

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public void clearCache() {
        coverCullDeadlines = new Long2LongOpenHashMap();
        releaseEndMillis = 0L;
        lastScanX = Integer.MIN_VALUE;
        lastScanY = Integer.MIN_VALUE;
        lastScanZ = Integer.MIN_VALUE;
    }

    /** 指定位置が覆いカリング対象か(開始時刻の到達に関係なく)。 */
    public boolean isCoverBlock(long posLong) {
        Long2LongMap deadlines = coverCullDeadlines;
        return !deadlines.isEmpty() && deadlines.containsKey(posLong);
    }

    public boolean isCoverBlock(BlockPos pos) {
        return isCoverBlock(pos.asLong());
    }

    /** 指定位置が覆いカリング対象で、かつ開始時刻に達しているか。 */
    public boolean isCoverCullBlock(long posLong) {
        Long2LongMap deadlines = coverCullDeadlines;
        long deadline = deadlines.getOrDefault(posLong, Long.MIN_VALUE);
        return deadline != Long.MIN_VALUE && System.currentTimeMillis() >= deadline;
    }

    public boolean isCoverCulled(BlockPos pos) {
        return isCoverCullBlock(pos.asLong());
    }

    /** まだカリング開始待ちのブロックが残っているか(チャンク再構築の強制が必要か)。 */
    public boolean isReleasing() {
        return System.currentTimeMillis() < releaseEndMillis;
    }

    /**
     * 歩行可能列を探索し、各列の最初の覆い以降をカリング対象として収集する。
     * 同じ位置では再計算しない（移動時のみ）。
     *
     * @param enclosed プレイヤーが閉鎖空間（屋内）にいるか。false のとき固体の覆いは消さない
     * @param eyeX     視点X（ビューシェッド判定の起点）
     * @param eyeY     視点Y
     * @param eyeZ     視点Z
     * @param cameraY  走査上限に使うカメラY
     * @param radius   探索する水平半径（ブロック）
     * @param viewshed true なら視点から見える列だけを対象にする
     */
    public void update(BlockGetter level, int feetX, int feetY, int feetZ, boolean enclosed,
            double eyeX, double eyeY, double eyeZ, int cameraY, int radius, boolean viewshed) {
        if (level == null) {
            clearCache();
            return;
        }
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

        int startY = findStandableY(level, feetX, feetY, feetZ);
        if (startY == NOT_STANDABLE) {
            applyCollected(next, feetX, feetY, feetZ, radius);
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

            addColumnCover(level, cx, cy, cz, enclosed, eyeX, eyeY, eyeZ, cameraY, viewshed, next);

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

        applyCollected(next, feetX, feetY, feetZ, radius);
    }

    /**
     * 収集した覆い集合へ、カリング開始時刻を割り当てて差し替える。プレイヤーに近いブロックほど
     * 早く消え、同距離帯は位置ハッシュでばらけさせて輪状の塊にならないようにする。
     * 既存ブロックの開始時刻は引き継ぐ(再割り当てすると、既に消えた覆いが復活してしまう)。
     */
    private void applyCollected(LongOpenHashSet next, int refX, int refY, int refZ, int radius) {
        long now = System.currentTimeMillis();
        Long2LongMap previous = coverCullDeadlines;
        Long2LongOpenHashMap deadlines = new Long2LongOpenHashMap(next.size());
        LongIterator iterator = next.iterator();
        long end = 0L;
        while (iterator.hasNext()) {
            long posLong = iterator.nextLong();
            long deadline = previous.getOrDefault(posLong,
                    now + staggerDelayMillis(posLong, refX, refY, refZ, radius));
            deadlines.put(posLong, deadline);
            if (deadline > end) {
                end = deadline;
            }
        }
        coverCullDeadlines = deadlines;
        releaseEndMillis = end;
    }

    /** プレイヤーからの距離に比例した遅延(近いほど早い)＋同一距離帯を散らす小さなジッタ。 */
    private static long staggerDelayMillis(long posLong, int refX, int refY, int refZ, int radius) {
        double dx = BlockPos.getX(posLong) + 0.5 - refX;
        double dy = BlockPos.getY(posLong) + 0.5 - refY;
        double dz = BlockPos.getZ(posLong) + 0.5 - refZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double normalized = Math.min(distance / (radius + 1.0), 1.0);
        long base = (long) (normalized * RELEASE_WINDOW_MS);
        long span = Math.max(1L, RELEASE_WINDOW_MS / 6L);
        long hash = posLong;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return base + (hash & Long.MAX_VALUE) % span;
    }

    /**
     * 立位列の最初の覆い(足元+2より上で最初の非空気ブロック)から、カメラYまたは地表までを
     * 収集する。覆いより下(壁・間仕切り・床)は残すため間取りは露出しない。
     *
     * <p>最初の覆いが固体(建物の屋根・天井)のときは {@code enclosed} のときだけ収集する。
     * 屋外では近くの歩行可能列が軒下に入っていても屋根を消さない。葉(自然の樹冠)は常に収集する。
     * viewshed が有効な場合は、視点からその列の地面が見えるものだけを対象にする。
     */
    private void addColumnCover(BlockGetter level, int x, int feetY, int z, boolean enclosed,
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
            if (!covered) {
                // 最初の覆いが固体で屋外なら、その列の屋根は残す
                if (!enclosed && !state.is(BlockTags.LEAVES)) {
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
