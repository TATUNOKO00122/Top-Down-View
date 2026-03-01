package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * トップダウンビュー用カリング実装
 * 円柱カリングのみ（保護ロジックなし）
 */
public final class TopDownCuller {

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

    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
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

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(currentPlayerPos.y)) {
                cacheResult(pos, false);
                return false;
            }
        }

        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            cacheResult(pos, false);
            return false;
        }

        boolean isCulled = shouldCullByCylinder(pos, pPos, cPos);
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
     * 楕円柱カリング判定
     * カメラ→プレイヤーの軸に対して楕円柱カリングを適用
     * 断面は縦長の楕円（垂直方向が長い）
     */
    private boolean shouldCullByCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (blockCenter.y < playerPos.y) {
            return false;
        }

        Vec3 dir = playerPos.subtract(cameraPos);
        double dirLength = dir.length();
        if (dirLength < 1.0E-8) {
            return false;
        }

        Vec3 normDir = dir.normalize();
        Vec3 toBlockFromPlayer = blockCenter.subtract(playerPos);
        double forwardness = toBlockFromPlayer.dot(normDir);

        if (forwardness >= 0) {
            double dxPlayer = blockCenter.x - playerPos.x;
            double dzPlayer = blockCenter.z - playerPos.z;
            double distFromPlayerXZ = Math.sqrt(dxPlayer * dxPlayer + dzPlayer * dzPlayer);
            double protectionHeight = playerPos.y + Math.min(distFromPlayerXZ, 2.0);
            if (blockCenter.y < protectionHeight) {
                return false;
            }
        }

        double radiusH = Config.cylinderRadiusHorizontal;
        double radiusV = Config.cylinderRadiusVertical;

        Vec3 segVec = playerPos.subtract(cameraPos);
        double segLengthSq = segVec.lengthSqr();

        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(segVec) / segLengthSq;

        double segLength = Math.sqrt(segLengthSq);
        double extensionT = 3.0 / segLength;
        if (t < -extensionT) {
            return false;
        }

        t = Math.max(-extensionT, Math.min(t, 1.0));

        Vec3 closestPoint = cameraPos.add(segVec.scale(t));

        // 軸からの相対位置
        Vec3 relPos = blockCenter.subtract(closestPoint);

        // 軸方向成分を除去して、楕円断面内での位置を計算
        double alongAxis = relPos.dot(normDir);
        Vec3 perpPos = relPos.subtract(normDir.scale(alongAxis));

        // 水平成分（XZ平面）と垂直成分（Y）
        double distXZ = Math.sqrt(perpPos.x * perpPos.x + perpPos.z * perpPos.z);
        double distY = Math.abs(perpPos.y);

        // 楕円判定: (distXZ / rH)² + (distY / rV)² <= 1.0
        double normalizedDistSq = (distXZ * distXZ) / (radiusH * radiusH)
                                + (distY * distY) / (radiusV * radiusV);

        return normalizedDistSq <= 1.0;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 candidatePos = new Vec3(
            Math.floor(eyePos.x) + 0.5,
            Math.floor(eyePos.y) + 0.5,
            Math.floor(eyePos.z) + 0.5
        );
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            this.currentPlayerPos = candidatePos;
            this.currentCameraPos = Vec3.ZERO;
            return;
        }

        Vec3 cPos = new Vec3(
            Math.floor(rawCameraPos.x) + 0.5,
            Math.floor(rawCameraPos.y) + 0.5,
            Math.floor(rawCameraPos.z) + 0.5
        );

        if (candidatePos.distanceToSqr(this.currentPlayerPos) > CACHE_CLEAR_DISTANCE_SQ) {
            cullingCache.clear();
        }

        this.currentPlayerPos = candidatePos;
        this.currentCameraPos = cPos;
    }

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
