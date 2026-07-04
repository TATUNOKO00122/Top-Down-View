package com.topdownview.culling.ladder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class LadderHelper {

    private LadderHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    // ハシゴの連続数キャッシュ (< 0 = 3未満, >= 0 = 連続数)
    private static final Map<Long, Integer> chainLengthCache = new HashMap<>();

    public static void clearCache() {
        chainLengthCache.clear();
    }

    public static boolean isLadderInLongChain(BlockPos pos, BlockGetter level) {
        return getChainLength(pos, level) >= 3;
    }

    private static int getChainLength(BlockPos pos, BlockGetter level) {
        long posKey = pos.asLong();
        Integer cached = chainLengthCache.get(posKey);
        if (cached != null) {
            return cached;
        }

        if (!level.getBlockState(pos).is(Blocks.LADDER)) {
            chainLengthCache.put(posKey, -1);
            return -1;
        }

        int upCount = 0;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        int y = pos.getY();
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();

        checkPos.set(pos.getX(), y, pos.getZ());
        while (checkPos.getY() < maxY) {
            checkPos.move(Direction.UP);
            if (level.getBlockState(checkPos).is(Blocks.LADDER)) {
                upCount++;
            } else {
                break;
            }
        }

        int downCount = 0;
        checkPos.set(pos.getX(), y, pos.getZ());
        while (checkPos.getY() > minY) {
            checkPos.move(Direction.DOWN);
            if (level.getBlockState(checkPos).is(Blocks.LADDER)) {
                downCount++;
            } else {
                break;
            }
        }

        int totalLength = upCount + downCount + 1;

        int result = totalLength >= 3 ? totalLength : -1;
        chainLengthCache.put(posKey, result);

        checkPos.set(pos.getX(), y, pos.getZ());
        for (int i = 0; i < upCount; i++) {
            checkPos.move(Direction.UP);
            chainLengthCache.put(checkPos.asLong(), result);
        }

        checkPos.set(pos.getX(), y, pos.getZ());
        for (int i = 0; i < downCount; i++) {
            checkPos.move(Direction.DOWN);
            chainLengthCache.put(checkPos.asLong(), result);
        }

        return result;
    }

    public static boolean isBlockBehindLadderChain(BlockPos pos, BlockGetter level) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            neighborPos.set(x + dir.getStepX(), y, z + dir.getStepZ());
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof LadderBlock)) {
                continue;
            }
            if (neighborState.getValue(LadderBlock.FACING) == dir) {
                if (isLadderInLongChain(neighborPos, level)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 指定されたハシゴ位置からチェーンの最下部のY座標を返す。
     * 指定位置がハシゴでない場合は Integer.MAX_VALUE を返す。
     */
    public static int getChainBottomY(BlockPos pos, BlockGetter level) {
        if (!level.getBlockState(pos).is(Blocks.LADDER)) {
            return Integer.MAX_VALUE;
        }
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        while (checkPos.getY() > minY) {
            checkPos.move(Direction.DOWN);
            if (!level.getBlockState(checkPos).is(Blocks.LADDER)) {
                return checkPos.getY() + 1;
            }
        }
        return minY;
    }

    /**
     * isBlockBehindLadderChain の派生版。ハシゴチェーンが
     * プレイヤーの足元Y 〜 足元Y+2 以内から始まる場合のみ true を返す。
     */
    public static boolean isBlockBehindLadderChain(BlockPos pos, BlockGetter level, int playerFeetY) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            neighborPos.set(x + dir.getStepX(), y, z + dir.getStepZ());
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof LadderBlock)) {
                continue;
            }
            if (neighborState.getValue(LadderBlock.FACING) == dir) {
                if (isLadderInLongChain(neighborPos, level)) {
                    int chainBottomY = getChainBottomY(neighborPos, level);
                    if (chainBottomY >= playerFeetY && chainBottomY <= playerFeetY + 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
