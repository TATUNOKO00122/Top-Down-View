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
        return shouldCull(pos, level, state, playerPos.x, playerPos.y, playerPos.z, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static boolean shouldCull(BlockPos pos, BlockGetter level, BlockState state,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        if (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM) {
            if (pos.getY() <= Math.floor(playerY)) {
                return false;
            }
        } else {
            if (pos.getY() < Math.floor(playerY)) {
                return false;
            }
        }

        if (pos.getY() <= Math.floor(playerY) + 3) {
            if (hasLadderOrScaffoldingBelow(pos, level)) {
                return false;
            }
        }

        return CylinderCalculator.isInCylinderForTrapdoor(pos, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
    }

    public static boolean shouldCullForFade(BlockPos pos, BlockGetter level, BlockState state, Vec3 playerPos, Vec3 cameraPos) {
        return shouldCull(pos, level, state, playerPos.x, playerPos.y, playerPos.z, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static boolean shouldCullForFade(BlockPos pos, BlockGetter level, BlockState state,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        return shouldCull(pos, level, state, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
    }

    public static boolean shouldMakeTranslucent(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        return shouldMakeTranslucent(pos, playerPos.x, playerPos.y, playerPos.z, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static boolean shouldMakeTranslucent(BlockPos pos,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        return CylinderCalculator.isInCylinderForTrapdoor(pos, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
    }

    public static boolean hasLadderOrScaffoldingBelow(BlockPos pos, BlockGetter level) {
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();
        for (int dy = 1; dy <= 3; dy++) {
            checkPos.set(px, py - dy, pz);
            BlockState belowState = level.getBlockState(checkPos);
            if (belowState.is(Blocks.LADDER) || belowState.is(Blocks.SCAFFOLDING)) {
                return true;
            }
        }
        return false;
    }
}
