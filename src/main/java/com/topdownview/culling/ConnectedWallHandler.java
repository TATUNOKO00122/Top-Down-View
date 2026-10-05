package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 部屋の角などの死角で発生するV字の壁残りを解消するため、
 * カリングされた手前の壁ブロックから隣接する壁ブロックへカリングを連鎖伝播させるハンドラ。
 */
public final class ConnectedWallHandler {

    private static final int MAX_CULLED_BLOCKS = 96;
    private static final int QUEUE_CAPACITY = 256;

    /** 保持側の距離余白。シード帯(±3)の出入れで連鎖メンバーが当落する揺れを吸収する。 */
    private static final int RETENTION_EXTRA_RANGE = 2;
    private static final int RETENTION_Y_MARGIN = 2;

    // 水平4方向 + 上方向 (下方向は床保護のため除外)
    private static final int[] DX = {1, -1, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1};
    private static final int[] DZ = {0, 0, 1, -1, 0};

    /**
     * 連鎖カリング集合。volatile 差し替えで公開し、チャンク構築ワーカーが
     * 再構築中の集合を二重読み/同時変更なしに参照できるようにする。
     */
    private volatile LongOpenHashSet connectedCulledPositions = new LongOpenHashSet(MAX_CULLED_BLOCKS);

    /** 毎ティック差し替える二つの公開バッファ(再利用、アロケーションなし)。
     *  published 集合が A のとき B に組み立てて差し替える。 */
    private final LongOpenHashSet publishBufferA = new LongOpenHashSet(MAX_CULLED_BLOCKS * 2);
    private final LongOpenHashSet publishBufferB = new LongOpenHashSet(MAX_CULLED_BLOCKS * 2);

    private final LongOpenHashSet visited = new LongOpenHashSet(MAX_CULLED_BLOCKS * 2);

    private final long[] queue = new long[QUEUE_CAPACITY];
    private final int[] depthQueue = new int[QUEUE_CAPACITY];

    private final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    public ConnectedWallHandler() {
    }

    public void clearCache() {
        connectedCulledPositions = new LongOpenHashSet(MAX_CULLED_BLOCKS);
        visited.clear();
    }

    public boolean isConnectedCulled(long posLong) {
        return connectedCulledPositions.contains(posLong);
    }

    /**
     * プレイヤー周辺の手前壁から連鎖カリング対象のブロック集合を更新する。
     *
     * <p>前回の集合は毎ティック全クリアせず<b>保持マージ</b>する: 現在の候補(BFS結果)に加え、
     * 範囲内・Y帯内・まだ壁である前回メンバーは保持する。全クリア方式ではシード帯の出入れ
     * (プレイヤーの1ブロック移動)で連鎖メンバーの当落が当ティックごとに反転し、判定が
     * カリング→復元→カリングの往復で遷移フェードが毎回始まり直す(消失/復元の点滅)。
     * 保持マージは members の離脱を「範囲外の確定した時だけ」にする。
     */
    public void update(Level level, double playerX, double playerY, double playerZ,
                       double cameraX, double cameraY, double cameraZ,
                       double viewDirX, double viewDirZ, double viewWedgeCos,
                       boolean wedgeClipActive) {
        if (!Config.isConnectedWallCullingEnabled() || level == null) {
            clearCache();
            return;
        }

        int pBlockX = (int) Math.floor(playerX);
        int pFeetY = (int) Math.floor(playerY);
        int pBlockZ = (int) Math.floor(playerZ);
        int maxSteps = Config.getConnectedWallMaxDistance();

        int head = 0;
        int tail = 0;

        // 組み立て先を今ティックの公開バッファへ。前回の公開集合と別インスタンスにする。
        LongOpenHashSet published = connectedCulledPositions;
        LongOpenHashSet next = (published == publishBufferA) ? publishBufferB : publishBufferA;

        visited.clear();
        // 前回メンバーが visited を消費してしまうのを避けるため candidate に直接追加する。
        LongOpenHashSet candidate = next;
        candidate.clear();

        // 1. シード探索: プレイヤー周囲で扇状カリング等により実際にカリングされる手前の壁を収集
        int seedRadius = 3;
        for (int dx = -seedRadius; dx <= seedRadius; dx++) {
            for (int dz = -seedRadius; dz <= seedRadius; dz++) {
                if (dx == 0 && dz == 0) continue;

                int x = pBlockX + dx;
                int z = pBlockZ + dz;

                // 扇状クリップ外のブロックはシードにしない
                if (wedgeClipActive && !OcclusionCalculator.isWithinViewWedge(
                        x + 0.5, z + 0.5, playerX, playerZ, viewDirX, viewDirZ, viewWedgeCos)) {
                    continue;
                }

                for (int y = pFeetY; y <= pFeetY + 2; y++) {
                    tempPos.set(x, y, z);
                    if (!WallAnalyzer.isSolid(level, tempPos)) continue;

                    double normDistSq = CylinderCalculator.getNormalizedDistanceSq(
                            x + 0.5, y + 0.5, z + 0.5);
                    if (normDistSq < 0 || normDistSq > 1.0) continue;

                    long posLong = tempPos.asLong();
                    if (candidate.add(posLong)) {
                        if (tail < QUEUE_CAPACITY) {
                            queue[tail] = posLong;
                            depthQueue[tail] = 0;
                            tail++;
                        }
                    }
                }
            }
        }

        // 2. BFS 連鎖展開: シードから隣接する壁ブロックを探索
        int maxHorizDistFromPlayer = maxSteps + 2;
        while (head < tail && candidate.size() < MAX_CULLED_BLOCKS) {
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
                // 頭上高所の過剰巻き込み防止
                if (ny > pFeetY + 4) continue;

                // プレイヤーからの距離制限 (遠方の壁への延焼を防止)
                if (Math.abs(nx - pBlockX) > maxHorizDistFromPlayer
                        || Math.abs(nz - pBlockZ) > maxHorizDistFromPlayer) {
                    continue;
                }

                neighborPos.set(nx, ny, nz);
                long neighborLong = neighborPos.asLong();
                if (candidate.contains(neighborLong)) continue;

                if (!WallAnalyzer.isSolid(level, neighborPos)) continue;

                BlockState state = level.getBlockState(neighborPos);
                if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                candidate.add(neighborLong);

                if (tail < QUEUE_CAPACITY) {
                    queue[tail] = neighborLong;
                    depthQueue[tail] = currentDepth + 1;
                    tail++;
                }
            }
        }

        // 3. 保持マージ: 前回メンバーでも範囲・Y帯・surviv条件が有効なものは維持する。
        for (long prevLong : connectedCulledPositions) {
            if (candidate.contains(prevLong)) {
                continue;
            }
            int nx = BlockPos.getX(prevLong);
            int ny = BlockPos.getY(prevLong);
            int nz = BlockPos.getZ(prevLong);

            if (ny < pFeetY - RETENTION_Y_MARGIN || ny > pFeetY + 4 + RETENTION_Y_MARGIN) {
                continue;
            }
            if (Math.abs(nx - pBlockX) > maxHorizDistFromPlayer + RETENTION_EXTRA_RANGE
                    || Math.abs(nz - pBlockZ) > maxHorizDistFromPlayer + RETENTION_EXTRA_RANGE) {
                continue;
            }

            neighborPos.set(nx, ny, nz);
            // まだ壁として有効で、インタラクション保護対象でもないメンバーは保持する。
            if (!WallAnalyzer.isSolid(level, neighborPos)) {
                continue;
            }
            BlockState state = level.getBlockState(neighborPos);
            if (InteractableBlocks.isInteractable(state, level, neighborPos)) {
                continue;
            }

            candidate.add(prevLong);
        }

        connectedCulledPositions = candidate;
    }
}
