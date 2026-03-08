package com.topdownview.culling.trapdoor;

import com.topdownview.culling.geometry.CylinderCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

public final class TrapdoorHelper {

    private TrapdoorHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean shouldCull(BlockPos pos, BlockGetter level, BlockState state, Vec3 playerPos, Vec3 cameraPos) {
        if (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM) {
            if (pos.getY() <= Math.floor(playerPos.y)) {
                return false;
            }
        } else {
            if (pos.getY() < Math.floor(playerPos.y)) {
                return false;
            }
        }

        if (pos.getY() <= Math.floor(playerPos.y) + 3) {
            if (hasLadderOrScaffoldingBelow(pos, level)) {
                return false;
            }
        }

        return CylinderCalculator.isInCylinderForTrapdoor(pos, playerPos, cameraPos);
    }

    public static boolean shouldCullForFade(BlockPos pos, BlockGetter level, BlockState state, Vec3 playerPos, Vec3 cameraPos) {
        return shouldCull(pos, level, state, playerPos, cameraPos);
    }

    public static boolean shouldMakeTranslucent(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        return CylinderCalculator.isInCylinderForTrapdoor(pos, playerPos, cameraPos);
    }

    public static boolean hasLadderOrScaffoldingBelow(BlockPos pos, BlockGetter level) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos checkPos = pos.below(dy);
            BlockState belowState = level.getBlockState(checkPos);
            if (belowState.is(Blocks.LADDER) || belowState.is(Blocks.SCAFFOLDING)) {
                return true;
            }
        }
        return false;
    }
}
