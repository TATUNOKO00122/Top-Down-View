package com.topdownview.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * ネザーゲートやエンドポータル等のゲート構造を枠を含めてカリングから保護するハンドラー。
 * ポータル面から外周 1 ブロックの非空気ブロックを枠として動的に収集し、
 * MOD 産を含め枠の建材に関わらずゲート全体を保護する。
 */
public final class PortalCullingHandler {

    /** チャンク構築ワーカーから読まれるため、集合は volatile 参照ごと差し替える。 */
    private volatile LongOpenHashSet protectedPositions = new LongOpenHashSet();

    public void clearCache() {
        protectedPositions = new LongOpenHashSet();
    }

    public boolean isProtectedPortal(long posLong) {
        return protectedPositions.contains(posLong);
    }

    public void update(Level level, int playerX, int playerY, int playerZ, int radiusH) {
        if (level == null) {
            clearCache();
            return;
        }

        LongOpenHashSet newProtected = new LongOpenHashSet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos adjPos = new BlockPos.MutableBlockPos();

        int minX = playerX - radiusH;
        int maxX = playerX + radiusH;
        int minZ = playerZ - radiusH;
        int maxZ = playerZ + radiusH;
        int minY = Math.max(level.getMinBuildHeight(), playerY - 4);
        int maxY = Math.min(level.getMaxBuildHeight(), playerY + 8);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (isPortalBlock(state)) {
                        newProtected.add(pos.asLong());
                        collectFrameBlocks(level, pos, state, adjPos, newProtected);
                    } else if (state.getBlock() instanceof EndPortalFrameBlock) {
                        newProtected.add(pos.asLong());
                    }
                }
            }
        }

        protectedPositions = newProtected;
    }

    private static boolean isPortalBlock(BlockState state) {
        return state.getBlock() instanceof NetherPortalBlock
                || state.getBlock() instanceof EndPortalBlock
                || state.getBlock() instanceof EndGatewayBlock;
    }

    private static void collectFrameBlocks(Level level, BlockPos pos, BlockState state,
                                           BlockPos.MutableBlockPos adjPos, LongOpenHashSet out) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            // ポータルが伸びる軸と Y 軸の平面上（周囲 8 方向）を走査
            for (int stepAxis = -1; stepAxis <= 1; stepAxis++) {
                for (int stepY = -1; stepY <= 1; stepY++) {
                    if (stepAxis == 0 && stepY == 0) continue;
                    int ax = (axis == Direction.Axis.X) ? stepAxis : 0;
                    int az = (axis == Direction.Axis.Z) ? stepAxis : 0;
                    adjPos.set(pos.getX() + ax, pos.getY() + stepY, pos.getZ() + az);
                    addIfSolidFrame(level, adjPos, out);
                }
            }
        } else {
            // 軸プロパティを持たないポータルは周囲を走査
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        adjPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        addIfSolidFrame(level, adjPos, out);
                    }
                }
            }
        }
    }

    private static void addIfSolidFrame(Level level, BlockPos pos, LongOpenHashSet out) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && state.getFluidState().isEmpty()) {
            out.add(pos.asLong());
        }
    }
}
