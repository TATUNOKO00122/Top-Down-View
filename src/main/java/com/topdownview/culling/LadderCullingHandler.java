package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.ladder.LadderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ハシゴおよびその支えとなる壁ブロックの保護・半透明化判定を担うハンドラー。
 */
public final class LadderCullingHandler {

    private static final class ProtectedLadderChain {
        final int x;
        final int z;
        final int bottomY;
        final int topY;
        final int wallX;
        final int wallZ;

        ProtectedLadderChain(int x, int z, int bottomY, int topY, Direction facing) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
            this.wallX = x - facing.getStepX();
            this.wallZ = z - facing.getStepZ();
        }
    }

    private final List<ProtectedLadderChain> protectedLadderChains = new ArrayList<>();
    private final Set<BlockPos> protectedLadderPositions = new HashSet<>();

    public void clearCache() {
        protectedLadderChains.clear();
        protectedLadderPositions.clear();
    }

    public boolean isProtectedPosition(BlockPos pos) {
        return !protectedLadderPositions.isEmpty() && protectedLadderPositions.contains(pos);
    }

    public void scan(Level level, int blockX, int blockZ, int playerFeetY) {
        protectedLadderChains.clear();
        protectedLadderPositions.clear();

        if (!Config.isLadderOccludeEnabled() || level == null) {
            return;
        }

        int radius = com.topdownview.state.SpaceDebugState.STAIR_SCAN_RADIUS;
        int minY = playerFeetY;
        int Math_min = Math.min(playerFeetY + 1, level.getMaxBuildHeight() - 1);
        int Math_max = Math.max(minY, level.getMinBuildHeight());

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        Set<Long> scannedColumns = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = blockX + dx;
                int z = blockZ + dz;
                long columnKey = BlockPos.asLong(x, 0, z);
                if (scannedColumns.contains(columnKey)) {
                    continue;
                }
                for (int checkY = Math_max; checkY <= Math_min; checkY++) {
                    checkPos.set(x, checkY, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (!state.is(Blocks.LADDER)) continue;

                    int chainLength = LadderHelper.getChainLengthPublic(checkPos, level);
                    if (chainLength < 3) continue;
                    int chainBottomY = LadderHelper.getChainBottomY(checkPos, level);
                    if (chainBottomY < playerFeetY || chainBottomY > playerFeetY + 1) continue;

                    scannedColumns.add(columnKey);

                    int chainTopY = chainBottomY + chainLength - 1;
                    Direction facing = state.getValue(LadderBlock.FACING);
                    ProtectedLadderChain chain = new ProtectedLadderChain(x, z, chainBottomY, chainTopY, facing);
                    protectedLadderChains.add(chain);

                    for (int y = chainBottomY; y <= chainTopY; y++) {
                        protectedLadderPositions.add(new BlockPos(x, y, z));
                        protectedLadderPositions.add(new BlockPos(chain.wallX, y, chain.wallZ));
                    }
                    break;
                }
            }
        }
    }

    public void collectOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, FadeCacheManager fadeCache) {
        if (protectedLadderChains.isEmpty() || !Config.isLadderOccludeEnabled()) {
            return;
        }

        float occludeAlpha = (float) Config.getLadderOccludeAlpha();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (ProtectedLadderChain chain : protectedLadderChains) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            boolean anyOccluding = OcclusionFadeCollector.anyOccludingColumn(
                    chain.x, chain.z, chain.bottomY, chain.topY, mutablePos, cX, cY, cZ, pX, pY, pZ)
                    || OcclusionFadeCollector.anyOccludingColumn(
                    chain.wallX, chain.wallZ, chain.bottomY, chain.topY, mutablePos, cX, cY, cZ, pX, pY, pZ);
            float alpha = anyOccluding ? occludeAlpha : 1.0f;
            OcclusionFadeCollector.putColumn(level, fadeCache, chain.x, chain.z, chain.bottomY, chain.topY,
                    mutablePos, null, false, alpha);
            OcclusionFadeCollector.putColumn(level, fadeCache, chain.wallX, chain.wallZ, chain.bottomY, chain.topY,
                    mutablePos, null, true, alpha);
        }
    }
}
