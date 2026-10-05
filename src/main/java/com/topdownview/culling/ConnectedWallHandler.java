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

    // 水平4方向 + 上方向 (下方向は床保護のため除外)
    private static final int[] DX = {1, -1, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1};
    private static final int[] DZ = {0, 0, 1, -1, 0};

    private final LongOpenHashSet connectedCulledPositions = new LongOpenHashSet(MAX_CULLED_BLOCKS);
    private final LongOpenHashSet visited = new LongOpenHashSet(MAX_CULLED_BLOCKS * 2);

    private final long[] queue = new long[QUEUE_CAPACITY];
    private final int[] depthQueue = new int[QUEUE_CAPACITY];

    private final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    public ConnectedWallHandler() {
    }

    public void clearCache() {
        connectedCulledPositions.clear();
        visited.clear();
    }

    public boolean isConnectedCulled(long posLong) {
        return connectedCulledPositions.contains(posLong);
    }

    /**
     * プレイヤー周辺の手前壁から連鎖カリング対象のブロック集合を更新する。
     */
    public void update(Level level, double playerX, double playerY, double playerZ,
                       double cameraX, double cameraY, double cameraZ,
                       double viewDirX, double viewDirZ, double viewWedgeCos,
                       boolean wedgeClipActive) {
        if (!Config.isConnectedWallCullingEnabled() || level == null) {
            clearCache();
            return;
        }

        clearCache();

        int pBlockX = (int) Math.floor(playerX);
        int pFeetY = (int) Math.floor(playerY);
        int pBlockZ = (int) Math.floor(playerZ);
        int maxSteps = Config.getConnectedWallMaxDistance();

        int head = 0;
        int tail = 0;

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
                    if (visited.add(posLong)) {
                        connectedCulledPositions.add(posLong);
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
        while (head < tail && connectedCulledPositions.size() < MAX_CULLED_BLOCKS) {
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
                if (!visited.add(neighborLong)) continue;

                if (!WallAnalyzer.isSolid(level, neighborPos)) continue;

                BlockState state = level.getBlockState(neighborPos);
                if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                connectedCulledPositions.add(neighborLong);

                if (tail < QUEUE_CAPACITY) {
                    queue[tail] = neighborLong;
                    depthQueue[tail] = currentDepth + 1;
                    tail++;
                }
            }
        }
    }
}
