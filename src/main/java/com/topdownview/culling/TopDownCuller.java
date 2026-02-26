package com.topdownview.culling;

import com.topdownview.client.ClientForgeEvents;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * トップダウンビュー用カリング実装 - ハイブリッド方式
 * - カメラ↔プレイヤー間: 円柱カリング（従来方式）
 * - プレイヤーより奥: プレイヤー周囲の円柱内の天井のみカリング
 * Minecraftメインスレッドのみで動作するためHashMapを使用
 */
public final class TopDownCuller implements Culler {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final int MAX_CACHE_SIZE = 8000;
    private static final double CACHE_CLEAR_DISTANCE_SQ = 100.0;

    private Vec3 currentPlayerPos = Vec3.ZERO;
    private Vec3 currentCameraPos = Vec3.ZERO;

    private final Map<BlockPos, Boolean> cullingCache = new HashMap<>(1000);

    private TopDownCuller() {
    }

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    @Override
    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    @Override
    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ClientForgeEvents.isTopDownView()) {
            if (!cullingCache.isEmpty()) {
                cullingCache.clear();
            }
            return false;
        }

        if (level == null) return false;

        Boolean cached = cullingCache.get(pos);
        if (cached != null) return cached;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cacheResult(pos, false);
            return false;
        }

        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.LadderBlock) {
            cacheResult(pos, false);
            return false;
        }

        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            cacheResult(pos, false);
            return false;
        }

        if (block instanceof net.minecraft.world.level.block.ChestBlock ||
                block instanceof net.minecraft.world.level.block.EnderChestBlock ||
                block instanceof net.minecraft.world.level.block.TrappedChestBlock ||
                block instanceof net.minecraft.world.level.block.DoorBlock) {
            if (pos.getY() <= pPos.y - 1.5 + 4.0) {
                cacheResult(pos, false);
                return false;
            }
        }

        boolean isCulled = shouldCullByCylinder(pos, level, pPos, cPos);
        cacheResult(pos, isCulled);
        return isCulled;
    }

    private void cacheResult(BlockPos pos, boolean result) {
        if (cullingCache.size() >= MAX_CACHE_SIZE) {
            cullingCache.clear();
        }
        cullingCache.put(pos.immutable(), result);
    }

    /**
     * ハイブリッドカリング判定
     * - カメラ↔プレイヤー間(t<=1.0): 円柱カリング（従来方式）
     * - プレイヤーより奥(t>1.0): プレイヤー周囲の円柱内の天井のみカリング
     */
    private boolean shouldCullByCylinder(BlockPos pos, BlockGetter level, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        double radius = com.topdownview.Config.cylinderRadius;
        int extension = com.topdownview.Config.cylinderExtension;

        Vec3 lineVec = playerPos.subtract(cameraPos);
        double lineLengthSq = lineVec.lengthSqr();

        if (lineLengthSq < 1.0E-8) {
            return false;
        }

        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(lineVec) / lineLengthSq;

        double relativeHeight = (pos.getY() + 0.5) - (playerPos.y - 1.5);

        if (t <= 1.0) {
            if (relativeHeight <= com.topdownview.Config.baseProtectionHeight) {
                return false;
            }

            Vec3 closestPoint = cameraPos.add(lineVec.scale(t));
            double dx = blockCenter.x - closestPoint.x;
            double dz = blockCenter.z - closestPoint.z;
            double horizontalDistSq = dx * dx + dz * dz;

            return horizontalDistSq <= radius * radius;
        }

        double dx = blockCenter.x - playerPos.x;
        double dz = blockCenter.z - playerPos.z;
        double horizontalDistSq = dx * dx + dz * dz;

        if (horizontalDistSq > extension * extension) {
            return false;
        }

        return relativeHeight > com.topdownview.Config.ceilingHeight;
    }

    @Override
    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 pPos = new Vec3(
            Math.floor(eyePos.x) + 0.5,
            eyePos.y,
            Math.floor(eyePos.z) + 0.5
        );
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            this.currentPlayerPos = pPos;
            this.currentCameraPos = Vec3.ZERO;
            return;
        }

        Vec3 cPos = new Vec3(
            Math.floor(rawCameraPos.x) + 0.5,
            Math.floor(rawCameraPos.y) + 0.5,
            Math.floor(rawCameraPos.z) + 0.5
        );

        if (pPos.distanceToSqr(this.currentPlayerPos) > CACHE_CLEAR_DISTANCE_SQ) {
            cullingCache.clear();
        }

        this.currentPlayerPos = pPos;
        this.currentCameraPos = cPos;
    }

    @Override
    public void reset() {
        cullingCache.clear();
        this.currentPlayerPos = Vec3.ZERO;
        this.currentCameraPos = Vec3.ZERO;
    }

    public int getCulledBlockCount() {
        int count = 0;
        for (Boolean value : cullingCache.values()) {
            if (value) count++;
        }
        return count;
    }

    public int getCacheSize() {
        return cullingCache.size();
    }
}
