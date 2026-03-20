package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.OreBlocks;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * マイニングモード用のカリング計算を行うユーティリティクラス。
 * プレイヤー周辺のスライス表示と鉱石保護を行う。
 */
final class MiningModeCuller {

    private static final int SLICE_OFFSET = -3;
    private static final int SLICE_HEIGHT = 5;
    private static final double ORE_EXCLUDE_RADIUS = 2.0;
    private static final int CAMERA_SIDE_REDUCTION = 5;
    private static final int MAX_BACKWARD_LAYERS = 5;
    private static final double CAMERA_INVALID_THRESHOLD_SQ = 1.0E-8;

    private MiningModeCuller() {}

    static boolean isBlockCulled(BlockPos pos, BlockGetter level,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        if (cameraX * cameraX + cameraY * cameraY + cameraZ * cameraZ < CAMERA_INVALID_THRESHOLD_SQ) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof StairBlock) {
            return false;
        }

        if (OreBlocks.isOre(state)) {
            double dx = (pos.getX() + 0.5) - playerX;
            double dz = (pos.getZ() + 0.5) - playerZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            if (distXZ <= ORE_EXCLUDE_RADIUS) {
                int playerFeetY = (int) Math.floor(playerY) - 1;
                if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 3) {
                    return false;
                }
            }
        }

        double radius = Config.getMiningCylinderRadius();
        double forwardShift = Config.getMiningCylinderForwardShift();
        float yaw = ModState.CAMERA.getYaw();

        if (!CylinderCalculator.isInMiningCylinder(pos,
                playerX, playerY, playerZ,
                cameraX, cameraY, cameraZ,
                radius, yaw, forwardShift)) {
            return false;
        }

        int playerFeetY = (int) Math.floor(playerY) - 1;
        int protectedMinY = playerFeetY + SLICE_OFFSET;
        int protectedMaxY = protectedMinY + SLICE_HEIGHT - 1;

        if (isCameraSide(pos, playerX, playerZ)) {
            protectedMaxY -= CAMERA_SIDE_REDUCTION;
        } else {
            double distanceFactor = getBackwardDistanceFactor(pos, playerX, playerZ, radius);
            protectedMaxY += (int) (distanceFactor * MAX_BACKWARD_LAYERS);
        }

        return pos.getY() < protectedMinY || pos.getY() > protectedMaxY;
    }

    private static boolean isCameraSide(BlockPos pos, double playerX, double playerZ) {
        float pitch = ModState.CAMERA.getPitch();

        if (pitch >= MathConstants.PITCH_NEAR_VERTICAL) {
            return false;
        }

        float yaw = ModState.CAMERA.getYaw();
        double radYaw = Math.toRadians(yaw);

        double dxToCamera = Math.sin(radYaw);
        double dzToCamera = -Math.cos(radYaw);

        double dxBlock = (pos.getX() + 0.5) - playerX;
        double dzBlock = (pos.getZ() + 0.5) - playerZ;

        double dot = dxBlock * dxToCamera + dzBlock * dzToCamera;

        return dot > MathConstants.DOT_PRODUCT_THRESHOLD;
    }

    private static double getBackwardDistanceFactor(BlockPos pos, double playerX, double playerZ, double radius) {
        float pitch = ModState.CAMERA.getPitch();

        if (pitch >= 89.9f) {
            return 0.0;
        }

        float yaw = ModState.CAMERA.getYaw();
        double radYaw = Math.toRadians(yaw);

        double dxBackward = -Math.sin(radYaw);
        double dzBackward = Math.cos(radYaw);

        double dxBlock = (pos.getX() + 0.5) - playerX;
        double dzBlock = (pos.getZ() + 0.5) - playerZ;

        double dot = dxBlock * dxBackward + dzBlock * dzBackward;

        if (dot <= MathConstants.DOT_PRODUCT_THRESHOLD) {
            return 0.0;
        }

        return Math.min(dot / radius, 1.0);
    }
}