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
 * トップダウンビュー用カリング実装 - Dungeons Perspective方式
 * Minecraftメインスレッドのみで動作するためHashMapを使用
 */
public final class TopDownCuller implements Culler {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static double get_culling_camera_distance() {
        return com.topdownview.Config.cullingRange;
    }

    private static final double CULLING_ANGLE_COS = 0.9848;
    private static final double BACK_SIDE_CULL_LIMIT = 3.0;
    private static final int UPDATE_FREQUENCY = 1;
    private static final int MAX_CACHE_SIZE = 8000;
    private static final double CACHE_CLEAR_DISTANCE_SQ = 100.0; // 10ブロック移動でキャッシュクリア

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

        boolean isCulled = shouldCullByVector(pos, level, pPos, cPos);
        cacheResult(pos, isCulled);
        return isCulled;
    }

    private void cacheResult(BlockPos pos, boolean result) {
        if (cullingCache.size() >= MAX_CACHE_SIZE) {
            cullingCache.clear();
        }
        cullingCache.put(pos.immutable(), result);
    }

    private boolean shouldCullByVector(BlockPos pos, BlockGetter level, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 cameraDirection = playerPos.subtract(cameraPos).normalize();
        double cullingDist = get_culling_camera_distance();
        Vec3 virtualCameraPos = playerPos.subtract(cameraDirection.scale(cullingDist));

        Vec3 viewAxis = cameraDirection;
        double distToPlayer = cullingDist;

        Vec3 vCameraToBlock = blockCenter.subtract(virtualCameraPos);
        double distToBlock = vCameraToBlock.length();
        Vec3 blockDir = vCameraToBlock.normalize();

        double cosTheta = viewAxis.dot(blockDir);

        if (cosTheta < CULLING_ANGLE_COS) {
            return false;
        }

        Vec3 playerToBlock = blockCenter.subtract(playerPos);
        double relativeHeight = (pos.getY() + 0.5) - (playerPos.y - 1.5);

        Vec3 horizontalViewDir = new Vec3(viewAxis.x, 0, viewAxis.z).normalize();
        double horizontalOffset = playerToBlock.x * horizontalViewDir.x + playerToBlock.z * horizontalViewDir.z;

        boolean isNearSide = horizontalOffset < -0.2;

        if (isNearSide) {
            return relativeHeight > com.topdownview.Config.cullingHeightThreshold;
        } else {
            double projDist = distToBlock * cosTheta;
            if (projDist > distToPlayer + BACK_SIDE_CULL_LIMIT) {
                return false;
            }

            if (relativeHeight > com.topdownview.Config.cullingHeightThreshold) {
                return level.getBlockState(pos.below()).isAir();
            } else {
                return false;
            }
        }
    }

    @Override
    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 pPos = mc.player.getEyePosition(1.0f);
        Vec3 cPos = ModState.CAMERA.getCameraPosition();

        if (cPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            cPos = Vec3.ZERO;
        }

        // プレイヤーが大きく移動した場合のみキャッシュクリア
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
