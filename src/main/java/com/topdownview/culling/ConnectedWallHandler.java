package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiPredicate;

/**
 * 部屋の角などの死角で発生するV字の壁残りを解消するため、
 * カリングされた手前の壁ブロックから隣接する壁ブロックへカリングを連鎖伝播させるハンドラ。
 *
 * <p><b>安定性の設計方針 (覆いカリングと同じ)</b>:
 * <ul>
 *   <li>プローブ間隔で再計算する。毎ティック再構築すると入力の1ティック揺れがすべて
 *       メンバーシップの揺れになる。</li>
 *   <li>シードは独立した幾何(円柱帯∧クリップ∧ピラミッド非保護∧非保護ブロック)だけで認定する。
 *       判定({@code isBlockCulled})の出力をシードにすると自己参照ループになる。</li>
 *   <li>距離保持でスキャン間のBFS/境界の揺れを吸収する。</li>
 *   <li>連鎖メンバーは遷移フェードの収集から除外する(即時切替)。毎プローブの一斉遷移を
 *       フェードへ流すと予算・位相が崩れる。</li>
 * </ul>
 */
public final class ConnectedWallHandler {

    /** BFS 連鎖で新規に追加できる壁の上限。シードは含めない。 */
    private static final int MAX_CHAIN_BLOCKS = 96;
    /** 公開集合全体の上限。柱展開(各メンバーの上方向ストリップ)を含む。 */
    private static final int MAX_TOTAL_BLOCKS = 1024;
    private static final int QUEUE_CAPACITY = 1024;

    /**
     * 保持できる「候補から外れたプローブ数」。1プローブ(3ブロック)分の境界揺れだけを吸収し、
     * 2プローブ連続で候補外なら解放する。距離ベースの保持は、入室時に手前の壁が範囲内に
     * 留まる限り永久に握り続けて「カリングされた壁が復元されない」状態になるため使わない。
     */
    private static final int RETAIN_MISSED_PROBES = 2;
    /** 保持時のY余白(ブロック)。 */
    private static final int RETENTION_Y_SPAN = 2;

    /** Y 平滑化パスの反復数。隣接列への Y レベル伝播が収束するのに十分な回数。 */
    private static final int Y_LEVEL_PASSES = 3;

    // 水平4方向 + 上方向 (下方向は床保護のため除外)
    private static final int[] DX = {1, -1, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1};
    private static final int[] DZ = {0, 0, 1, -1, 0};

    /** XZ 列キーのパック ((x << 32) | z)。Y 平滑化の「この列は連鎖済みか」判定に使う。 */
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 連鎖カリング集合。volatile 差し替えで公開し、チャンク構築ワーカーが
     * 再構築中の集合を二重読み/同時変更なしに参照できるようにする。
     */
    private volatile LongOpenHashSet connectedCulledPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    /** 差し替える二つの公開バッファ(再利用、アロケーションなし)。 */
    private final LongOpenHashSet publishBufferA = new LongOpenHashSet(MAX_TOTAL_BLOCKS);
    private final LongOpenHashSet publishBufferB = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    /** BFS+Y平滑化の候補スクラッチ。 */
    private final LongOpenHashSet candidateBuffer = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    /** Y 平滑化パス用の「連鎖済み XZ 列」スクラッチ集合。 */
    private final LongOpenHashSet columnScratch = new LongOpenHashSet(256);

    private final long[] queue = new long[QUEUE_CAPACITY];
    private final int[] depthQueue = new int[QUEUE_CAPACITY];

    private final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    /** 集合の世代番号。値が変わればカリング結果が変わり得る(再構築/走査ゲートのトリガ)。 */
    private volatile long generation;

    /** 解放された(連鎖から外れた)メンバーの受け渡し。開示経路でメッシュへ戻す。 */
    private final LongOpenHashSet droppedPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    /** 候補から連続で外れているプローブ数(前回メンバーごと)。しきい値で解放。 */
    private final Long2ByteOpenHashMap missedProbes = new Long2ByteOpenHashMap();

    public ConnectedWallHandler() {
    }

    public void clearCache() {
        connectedCulledPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);
        candidateBuffer.clear();
        droppedPositions.clear();
        missedProbes.clear();
    }

    public boolean isConnectedCulled(long posLong) {
        return connectedCulledPositions.contains(posLong);
    }

    /** 現在の公開集合。呼び出し側は読み取り専用で扱うこと。 */
    public LongOpenHashSet getMembers() {
        return connectedCulledPositions;
    }

    /** デバッグHUD用: 現在連鎖カリングが有効なブロック数。 */
    public int getChainCount() {
        return connectedCulledPositions.size();
    }

    /** 集合の世代番号。値が変わればカリング結果が変わり得る。 */
    public long getGeneration() {
        return generation;
    }

    /** 解放された(連鎖から外れた)メンバーの受け渡し。メインスレッド専用。 */
    public void takeDroppedPositions(LongOpenHashSet out) {
        out.addAll(droppedPositions);
        droppedPositions.clear();
    }

    /**
     * プローブ受理時に連鎖カリング集合を再計算する。
     *
     * <p>シードは幾何のみで認定する。判定の出力をシードにすると自己参照ループになるため使わない。
     *
     * @param isProtected 壁ブロックが保護対象かを返す判定。シードから除外する。
     */
    public void update(Level level, double playerX, double playerY, double playerZ,
                       double cameraX, double cameraY, double cameraZ,
                       double viewDirX, double viewDirZ,
                       boolean wedgeActive, double wedgeCos,
                       int radiusH, int radiusV,
                       BiPredicate<BlockPos, BlockState> isProtected) {
        if (!Config.isConnectedWallCullingEnabled() || level == null) {
            clearCache();
            return;
        }

        int pBlockX = (int) Math.floor(playerX);
        int pFeetY = (int) Math.floor(playerY);
        int pBlockZ = (int) Math.floor(playerZ);
        int maxSteps = Config.getConnectedWallMaxDistance();
        int maxChainY = Math.max((int) Math.floor(cameraY), pFeetY + 2);
        // 連鎖の水平到達 = config 距離そのもの。保持範囲(下の updateRetention)と同値に
        // 揃えて隙間(churn)を作らない。
        int maxHorizDistFromPlayer = maxSteps;

        int head = 0;
        int tail = 0;

        LongOpenHashSet published = connectedCulledPositions;
        LongOpenHashSet candidate = candidateBuffer;
        candidate.clear();

        // 1. シード探索: 円柱帯∧クリップ∧ピラミッド非保護∧非保護の壁を幾何のみで認定する。
        int minX = (int) Math.floor(Math.min(playerX, cameraX)) - radiusH - 1;
        int maxX = (int) Math.floor(Math.max(playerX, cameraX)) + radiusH + 1;
        int minZ = (int) Math.floor(Math.min(playerZ, cameraZ)) - radiusH - 1;
        int maxZ = (int) Math.floor(Math.max(playerZ, cameraZ)) + radiusH + 1;
        int maxY = Math.min(maxChainY, (int) Math.floor(Math.max(playerY, cameraY)) + radiusV);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = pFeetY; y <= maxY; y++) {
                    tempPos.set(x, y, z);
                    BlockState state = level.getBlockState(tempPos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (!WallAnalyzer.isSolid(level, tempPos)) {
                        continue;
                    }
                    if (InteractableBlocks.isInteractable(state, level, tempPos)) {
                        continue;
                    }
                    double normDistSq = CylinderCalculator.getNormalizedDistanceSq(
                            x + 0.5, y + 0.5, z + 0.5);
                    if (normDistSq < 0.0 || normDistSq > 1.0) {
                        continue;
                    }
                    // クリップ(扇/半空間)の内側 = 円柱のカリング帯
                    if (wedgeActive) {
                        if (!OcclusionCalculator.isWithinViewWedge(
                                x + 0.5, z + 0.5, playerX, playerZ, viewDirX, viewDirZ, wedgeCos)) {
                            continue;
                        }
                    } else if (OcclusionCalculator.isBeyondPlayerHorizontally(
                            x + 0.5, z + 0.5, playerX, playerZ, viewDirX, viewDirZ)) {
                        continue;
                    }
                    // ピラミッド保護(足元近くの斜面)の内側はカリングされないのでシードにしない
                    if (PyramidProtectionCalc.calculateBoundaryDiff(tempPos,
                            playerX, playerY, playerZ, cameraX, cameraZ) >= 0.0) {
                        continue;
                    }
                    // 保護ブロック(支え構造など)はシードにしない
                    if (isProtected.test(tempPos, state)) {
                        continue;
                    }

                    if (tail < QUEUE_CAPACITY) {
                        queue[tail] = tempPos.asLong();
                        depthQueue[tail] = 0;
                        tail++;
                    }
                }
            }
        }

        // 2. BFS 連鎖展開: シードから隣接する壁ブロックを探索 (連鎖追加だけに上限を掛ける)
        int chainBase = candidate.size();
        while (head < tail && candidate.size() - chainBase < MAX_CHAIN_BLOCKS) {
            long currentPosLong = queue[head];
            int currentDepth = depthQueue[head];
            head++;

            if (currentDepth >= maxSteps) continue;

            tempPos.set(currentPosLong);

            for (int i = 0; i < 5; i++) {
                int nx = tempPos.getX() + DX[i];
                int ny = tempPos.getY() + DY[i];
                int nz = tempPos.getZ() + DZ[i];

                // 床・地面の保護 (プレイヤー足元未満は巻き込まない)
                if (ny < pFeetY) continue;
                // カメラより上はカリングしない
                if (ny > maxChainY) continue;

                // カメラ側半空間に限定する。プレイヤーの反対側(クリップが保護する領域)へは
                // 連鎖を渡さない。連鎖の役割は「カメラ側の角の裏を回り込む」こと。
                if (OcclusionCalculator.isBeyondPlayerHorizontally(
                        nx + 0.5, nz + 0.5, playerX, playerZ, viewDirX, viewDirZ)) {
                    continue;
                }

                // プレイヤーからの距離制限 (遠方の壁への延焼を防止)
                if (Math.abs(nx - pBlockX) > maxHorizDistFromPlayer
                        || Math.abs(nz - pBlockZ) > maxHorizDistFromPlayer) {
                    continue;
                }

                // 深さ(手数)は水平移動のみで消費する。上方向の登攀は Y 上限が支配する。
                int nextDepth = (i == 4) ? currentDepth : currentDepth + 1;

                neighborPos.set(nx, ny, nz);
                long neighborLong = neighborPos.asLong();
                if (candidate.contains(neighborLong)) continue;
                if (!WallAnalyzer.isSolid(level, neighborPos)) continue;
                BlockState state = level.getBlockState(neighborPos);
                if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                candidate.add(neighborLong);

                if (tail < QUEUE_CAPACITY) {
                    queue[tail] = neighborLong;
                    depthQueue[tail] = nextDepth;
                    tail++;
                }
            }
        }

        // 3. Y 平滑化: 横に隣接する連鎖列どうしで Y レベルを合わせ、上端の凸凹を解消する。
        for (int pass = 0; pass < Y_LEVEL_PASSES; pass++) {
            if (candidate.size() >= MAX_TOTAL_BLOCKS) {
                break;
            }
            boolean changed = false;
            columnScratch.clear();
            for (long posLong : candidate) {
                columnScratch.add(packXZ(BlockPos.getX(posLong), BlockPos.getZ(posLong)));
            }
            for (long posLong : candidate.toLongArray()) {
                int x = BlockPos.getX(posLong);
                int y = BlockPos.getY(posLong);
                int z = BlockPos.getZ(posLong);
                for (int i = 0; i < 4; i++) {
                    int nx = x + DX[i];
                    int nz = z + DZ[i];
                    if (!columnScratch.contains(packXZ(nx, nz))) continue;
                    if (y > maxChainY) continue;

                    neighborPos.set(nx, y, nz);
                    long neighborLong = neighborPos.asLong();
                    if (candidate.contains(neighborLong)) continue;
                    if (!WallAnalyzer.isSolid(level, neighborPos)) continue;
                    BlockState state = level.getBlockState(neighborPos);
                    if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                    candidate.add(neighborLong);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        // 4. 保持マージ(プローブ欠席制): 候補に無い前回メンバーは RETAIN_MISSED_PROBES まで
        //    維持し、連続で外れたら解放する。距離ベースだと入室時に手前の壁が範囲内に留まる
        //    限り永久に握られて復元されない。
        for (long prevLong : published) {
            if (candidate.contains(prevLong)) {
                missedProbes.remove(prevLong);
                continue;
            }
            int missed = missedProbes.get(prevLong) + 1;
            if (missed >= RETAIN_MISSED_PROBES) {
                missedProbes.remove(prevLong);
                continue;
            }
            int nx = BlockPos.getX(prevLong);
            int ny = BlockPos.getY(prevLong);
            int nz = BlockPos.getZ(prevLong);
            neighborPos.set(nx, ny, nz);
            // まだ壁で、インタラクション保護対象でもないメンバーのみ保持対象。
            if (ny < pFeetY || ny > maxChainY + RETENTION_Y_SPAN
                    || !WallAnalyzer.isSolid(level, neighborPos)) {
                missedProbes.remove(prevLong);
                continue;
            }
            BlockState state = level.getBlockState(neighborPos);
            if (InteractableBlocks.isInteractable(state, level, neighborPos)) {
                missedProbes.remove(prevLong);
                continue;
            }
            missedProbes.put(prevLong, (byte) missed);
            if (candidate.size() >= MAX_TOTAL_BLOCKS) {
                continue;
            }
            candidate.add(prevLong);
        }

        LongOpenHashSet next = (published == publishBufferA) ? publishBufferB : publishBufferA;
        next.clear();
        next.addAll(candidate);
        connectedCulledPositions = next;

        if (membershipChanged(published, next)) {
            for (long prevLong : published) {
                if (!next.contains(prevLong)) {
                    droppedPositions.add(prevLong);
                }
            }
            generation++;
        }
    }

    /**
     * 毎ティックの距離保持判定。プローブ間隔を待つと保持範囲を越えた壁が余白ぶん遠くまで残る。
     * BFS/平滑化は走らせず、距離だけを見る。範囲外は解放(開示経路でメッシュへ戻す)。
     */
    public void updateRetention(int playerBlockX, int playerBlockZ) {
        if (connectedCulledPositions.isEmpty()) {
            return;
        }
        int retainRange = Config.getConnectedWallMaxDistance();
        boolean changed = false;
        LongOpenHashSet published = connectedCulledPositions;
        LongOpenHashSet next = (published == publishBufferA) ? publishBufferB : publishBufferA;
        next.clear();
        for (long posLong : published) {
            if (Math.abs(BlockPos.getX(posLong) - playerBlockX) > retainRange
                    || Math.abs(BlockPos.getZ(posLong) - playerBlockZ) > retainRange) {
                droppedPositions.add(posLong);
                changed = true;
                continue;
            }
            next.add(posLong);
        }
        if (changed) {
            connectedCulledPositions = next;
            generation++;
        }
    }

    private static boolean membershipChanged(LongOpenHashSet previous, LongOpenHashSet next) {
        if (previous.size() != next.size()) {
            return true;
        }
        for (long posLong : previous) {
            if (!next.contains(posLong)) {
                return true;
            }
        }
        return false;
    }
}
