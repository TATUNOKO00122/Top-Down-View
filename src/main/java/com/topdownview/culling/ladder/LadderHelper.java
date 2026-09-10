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

    // チェーン最下部Yのキャッシュ（getChainBottomYの下方走査を1回に抑える）
    private static final Map<Long, Integer> chainBottomCache = new HashMap<>();

    public static void clearCache() {
        chainLengthCache.clear();
        chainBottomCache.clear();
    }

    public static boolean isLadderInLongChain(BlockPos pos, BlockGetter level) {
        return getChainLength(pos, level) >= 3;
    }

    /**
     * 指定位置のハシゴチェーン長を返す。ハシゴでない場合は -1。
     * TopDownCuller のハシゴ視線遮蔽半透明化で使用。
     */
    public static int getChainLengthPublic(BlockPos pos, BlockGetter level) {
        return getChainLength(pos, level);
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
        long posKey = pos.asLong();
        Integer cached = chainBottomCache.get(posKey);
        if (cached != null) {
            return cached;
        }

        int result;
        if (!level.getBlockState(pos).is(Blocks.LADDER)) {
            result = Integer.MAX_VALUE;
        } else {
            int minY = level.getMinBuildHeight();
            int bottom = minY;
            BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            while (checkPos.getY() > minY) {
                checkPos.move(Direction.DOWN);
                if (!level.getBlockState(checkPos).is(Blocks.LADDER)) {
                    bottom = checkPos.getY() + 1;
                    break;
                }
            }
            result = bottom;
        }

        chainBottomCache.put(posKey, result);
        return result;
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
