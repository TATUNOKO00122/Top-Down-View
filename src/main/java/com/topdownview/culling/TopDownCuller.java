package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TopDownCuller {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final double CACHE_CLEAR_DISTANCE_SQ = 100.0;
    private static final int MAX_TRANSLUCENT_CACHE_SIZE = 500;
    private static final double INTERACTION_RANGE_HORIZONTAL = 3.0;
    private static final int INTERACTION_RANGE_VERTICAL = 2;

    private Vec3 currentPlayerPos = Vec3.ZERO;
    private Vec3 currentCameraPos = Vec3.ZERO;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();
    private final Set<BlockPos> translucentTrapdoorsCache = new HashSet<>(100);

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
        translucentTrapdoorsCache.clear();
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

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cullingCache.put(pos, false);
            return false;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            boolean shouldCull = TrapdoorHelper.shouldCull(pos, level, state, currentPlayerPos, currentCameraPos);
            cullingCache.put(pos, shouldCull);
            return shouldCull;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(currentPlayerPos.y)) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        int playerBlockX = (int) Math.floor(currentPlayerPos.x);
        int playerBlockZ = (int) Math.floor(currentPlayerPos.z);
        int playerFeetY = (int) Math.floor(currentPlayerPos.y) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
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

        if (isProtectedBlock(pos, state, level)) {
            return 1.0f;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(pos, this.currentPlayerPos, this.currentCameraPos);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(pos, this.currentPlayerPos);

        double fadeStart = Config.fadeStart;
        double fadeNearAlpha = Config.fadeNearAlpha;

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

    private boolean isProtectedBlock(BlockPos pos, BlockState state, BlockGetter level) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return true;
        }

        if (blockCenter.y < pPos.y) {
            return true;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return true;
            }
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            if (!TrapdoorHelper.shouldCullForFade(pos, level, state, pPos, cPos)) {
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

        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            this.currentPlayerPos = candidatePos;
            this.currentCameraPos = Vec3.ZERO;
            return;
        }

        Vec3 cPos = new Vec3(
                Math.floor(rawCameraPos.x) + 0.5,
                Math.floor(rawCameraPos.y) + 0.5,
                Math.floor(rawCameraPos.z) + 0.5);

        if (candidatePos.distanceToSqr(this.currentPlayerPos) > CACHE_CLEAR_DISTANCE_SQ) {
            cullingCache.clear();
        }

        this.currentPlayerPos = candidatePos;
        this.currentCameraPos = cPos;
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        this.currentPlayerPos = Vec3.ZERO;
        this.currentCameraPos = Vec3.ZERO;
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

        if (!Config.fadeEnabled) {
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
        if (!ModState.STATUS.isEnabled() || !Config.fadeEnabled) {
            return false;
        }

        if (level == null) {
            return false;
        }

        float alpha = getFadeAlpha(pos, level);
        if (alpha >= 1.0f) {
            return false;
        }

        int playerBlockX = (int) Math.floor(currentPlayerPos.x);
        int playerBlockY = (int) Math.floor(currentPlayerPos.y);
        int playerBlockZ = (int) Math.floor(currentPlayerPos.z);

        if (pos.getX() == playerBlockX && pos.getY() == playerBlockY - 1 && pos.getZ() == playerBlockZ) {
            return false;
        }

        double dx = (pos.getX() + 0.5) - (playerBlockX + 0.5);
        double dz = (pos.getZ() + 0.5) - (playerBlockZ + 0.5);

        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double dot = dx * forwardX + dz * forwardZ;
        if (dot < 0) {
            return false;
        }

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ > INTERACTION_RANGE_HORIZONTAL) {
            return false;
        }

        int relY = pos.getY() - playerBlockY;
        if (relY < 0 || relY > INTERACTION_RANGE_VERTICAL) {
            return false;
        }

        return true;
    }

    public Map<BlockPos, Float> getFadeBlocks(BlockGetter level) {
        fadeCache.clearFadeBlocks();

        if (!ModState.STATUS.isEnabled()) {
            return fadeCache.getFadeBlocksCache();
        }

        if (!Config.fadeEnabled) {
            return fadeCache.getFadeBlocksCache();
        }

        if (level == null || currentCameraPos == Vec3.ZERO) {
            return fadeCache.getFadeBlocksCache();
        }

        int rangeH = Config.cylinderRadiusHorizontal + 2;
        int rangeV = Config.cylinderRadiusVertical + 3;

        int scanRangeXZ = Math.max(rangeH, 4);

        int minX = (int) Math.floor(currentPlayerPos.x) - scanRangeXZ;
        int maxX = (int) Math.floor(currentPlayerPos.x) + scanRangeXZ;
        int minY = (int) Math.floor(currentPlayerPos.y) - 1;
        int maxY = (int) Math.floor(currentPlayerPos.y) + rangeV;
        int minZ = (int) Math.floor(currentPlayerPos.z) - scanRangeXZ;
        int maxZ = (int) Math.floor(currentPlayerPos.z) + scanRangeXZ;

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

    public Set<BlockPos> getTranslucentTrapdoors(BlockGetter level) {
        translucentTrapdoorsCache.clear();

        if (!ModState.STATUS.isEnabled()) {
            return translucentTrapdoorsCache;
        }

        if (!Config.trapdoorTranslucencyEnabled) {
            return translucentTrapdoorsCache;
        }

        if (level == null || currentCameraPos == Vec3.ZERO) {
            return translucentTrapdoorsCache;
        }

        int rangeH = Config.cylinderRadiusHorizontal + 2;
        int rangeV = Config.cylinderRadiusVertical + 2;

        int minX = (int) Math.floor(currentPlayerPos.x) - rangeH;
        int maxX = (int) Math.floor(currentPlayerPos.x) + rangeH;
        int minY = (int) Math.floor(currentPlayerPos.y);
        int maxY = (int) Math.floor(currentPlayerPos.y) + rangeV;
        int minZ = (int) Math.floor(currentPlayerPos.z) - rangeH;
        int maxZ = (int) Math.floor(currentPlayerPos.z) + rangeH;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.getBlock() instanceof TrapDoorBlock) {
                        if (TrapdoorHelper.hasLadderOrScaffoldingBelow(mutablePos, level)) {
                            continue;
                        }

                        if (TrapdoorHelper.shouldMakeTranslucent(mutablePos, currentPlayerPos, currentCameraPos)) {
                            translucentTrapdoorsCache.add(mutablePos.immutable());

                            if (translucentTrapdoorsCache.size() >= MAX_TRANSLUCENT_CACHE_SIZE) {
                                return translucentTrapdoorsCache;
                            }
                        }
                    }
                }
            }
        }

        return translucentTrapdoorsCache;
    }
}
