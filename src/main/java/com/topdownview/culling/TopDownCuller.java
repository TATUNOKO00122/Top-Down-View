package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

public final class TopDownCuller {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final record CullingContext(Vec3 playerPos, Vec3 cameraPos) {
        static final CullingContext EMPTY = new CullingContext(Vec3.ZERO, Vec3.ZERO);
    }

    private static final int UPDATE_FREQUENCY = 1;
    private static final int CACHE_CLEAR_DISTANCE_SQ = 9;

    private int lastPlayerBlockX = Integer.MIN_VALUE;
    private int lastPlayerBlockY = Integer.MIN_VALUE;
    private int lastPlayerBlockZ = Integer.MIN_VALUE;

    private volatile CullingContext context = CullingContext.EMPTY;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();

    private TopDownCuller() {
    }

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    public void clearCache() {
        cullingCache.clear();
        fadeCache.clear();
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            cullingCache.clear();
            return false;
        }

        if (level == null) {
            return false;
        }

        Boolean cached = cullingCache.get(pos);
        if (cached != null) {
            return cached;
        }

        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (ModState.STATUS.isMiningMode()) {
            boolean cull = MiningModeCuller.isBlockCulled(pos, level, pPos, cPos);
            cullingCache.put(pos, cull);
            return cull;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cullingCache.put(pos, false);
            return false;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        int playerBlockX = (int) Math.floor(pPos.x);
        int playerBlockZ = (int) Math.floor(pPos.z);
        int playerFeetY = (int) Math.floor(pPos.y) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        if (cPos.equals(Vec3.ZERO)) {
            cullingCache.put(pos, false);
            return false;
        }

        float alpha = calculateFadeAlpha(pos, level, state);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(pos, isCulled);
        return isCulled;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state) {
        if (level == null) {
            return 1.0f;
        }

        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 1.0f;
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (isProtectedBlock(pos, state, level, pPos, cPos)) {
            return 1.0f;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(pos, pPos, cPos);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(pos, pPos, cPos);

        double fadeStart = Config.getFadeStart();
        double fadeNearAlpha = Config.getFadeNearAlpha();

        float cylinderAlpha;

        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
            cylinderAlpha = 1.0f;
        } else if (normalizedDistSq <= fadeStart) {
            cylinderAlpha = (float) fadeNearAlpha;
        } else {
            double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
            cylinderAlpha = (float) (fadeNearAlpha + t * (1.0 - fadeNearAlpha));
        }

        return (float) Math.max(cylinderAlpha, pyramidFactor);
    }

    private boolean isProtectedBlock(BlockPos pos, BlockState state, BlockGetter level, Vec3 pPos, Vec3 cPos) {
        if (cPos.equals(Vec3.ZERO)) {
            return true;
        }

        if (pos.getY() + 0.5 < pPos.y) {
            return true;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return true;
            }
        }

        return false;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 candidatePos = new Vec3(
                Math.floor(eyePos.x) + 0.5,
                Math.floor(eyePos.y) + 0.5,
                Math.floor(eyePos.z) + 0.5);
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        // カメラ位置が未初期化の場合、プレイヤー位置のみ更新して早期リターン
        // 次のフレームでカメラ位置が設定された後にカリングが有効になる
        if (rawCameraPos.equals(com.topdownview.state.CameraState.DEFAULT_POSITION)) {
            // プレイヤー位置のみ更新（カメラ位置はZERO）
            this.context = new CullingContext(candidatePos, Vec3.ZERO);
            return;
        }

        // カメラ位置をブロック座標にスナップ（判定の安定化のため）
        Vec3 cPos = new Vec3(
                Math.floor(rawCameraPos.x) + 0.5,
                Math.floor(rawCameraPos.y) + 0.5,
                Math.floor(rawCameraPos.z) + 0.5);

        int playerBlockX = (int) Math.floor(eyePos.x);
        int playerBlockY = (int) Math.floor(eyePos.y);
        int playerBlockZ = (int) Math.floor(eyePos.z);

        int dx = playerBlockX - lastPlayerBlockX;
        int dy = playerBlockY - lastPlayerBlockY;
        int dz = playerBlockZ - lastPlayerBlockZ;
        int distSq = dx * dx + dy * dy + dz * dz;

        if (distSq >= CACHE_CLEAR_DISTANCE_SQ || distSq < 0) {
            cullingCache.clear();
            fadeCache.clear();
            lastPlayerBlockX = playerBlockX;
            lastPlayerBlockY = playerBlockY;
            lastPlayerBlockZ = playerBlockZ;
        }

        // 不変オブジェクトでアトミック更新（synchronized不要）
        this.context = new CullingContext(candidatePos, cPos);
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        this.context = CullingContext.EMPTY;
        // 前回位置もリセット
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
    }

    public int getCulledBlockCount() {
        return cullingCache.getCulledCount();
    }

    public int getCacheSize() {
        return cullingCache.size();
    }

    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            return 1.0f;
        }

        if (ModState.STATUS.isMiningMode()) {
            return 1.0f;
        }

        if (!Config.isFadeEnabled()) {
            return 1.0f;
        }

        Float cached = fadeCache.getFadeAlpha(pos);
        if (cached != null) {
            return cached;
        }

        BlockState state = level.getBlockState(pos);
        float alpha = calculateFadeAlpha(pos, level, state);
        fadeCache.putFadeAlpha(pos, alpha);

        return alpha;
    }

    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !Config.isFadeEnabled()) {
            return false;
        }

        if (ModState.STATUS.isMiningMode()) {
            return false;
        }

        if (level == null) {
            return false;
        }

        float alpha = getFadeAlpha(pos, level);
        return alpha > Config.getFadeBlockHitThreshold();
    }

    public Map<BlockPos, Float> getFadeBlocks(BlockGetter level) {
        fadeCache.clearFadeBlocks();

        if (!ModState.STATUS.isEnabled()) {
            return fadeCache.getFadeBlocksCache();
        }

        // マイニングモード時はフェードブロックを返さない
        if (ModState.STATUS.isMiningMode()) {
            return fadeCache.getFadeBlocksCache();
        }

        if (!Config.isFadeEnabled()) {
            return fadeCache.getFadeBlocksCache();
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (level == null || cPos.equals(Vec3.ZERO)) {
            return fadeCache.getFadeBlocksCache();
        }

        int rangeH = Config.getCylinderRadiusHorizontal() + 2;
        int rangeV = Config.getCylinderRadiusVertical() + 3;

        int scanRangeXZ = Math.max(rangeH, 4);

        int minX = (int) Math.floor(pPos.x) - scanRangeXZ;
        int maxX = (int) Math.floor(pPos.x) + scanRangeXZ;
        int minY = (int) Math.floor(pPos.y) - 1;
        int maxY = (int) Math.floor(pPos.y) + rangeV;
        int minZ = (int) Math.floor(pPos.z) - scanRangeXZ;
        int maxZ = (int) Math.floor(pPos.z) + scanRangeXZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    float alpha = calculateFadeAlpha(mutablePos, level, state);
                    if (alpha < 1.0f && alpha > 0.0f) {
                        fadeCache.putFadeBlock(mutablePos.immutable(), alpha);

                        if (fadeCache.isFadeBlocksFull()) {
                            return fadeCache.getFadeBlocksCache();
                        }
                    }
                }
            }
        }

        return fadeCache.getFadeBlocksCache();
    }

    public Map<BlockPos, Float> getFadeBlocksCache() {
        return fadeCache.getFadeBlocksCache();
    }
}
