package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.state.ModState;
import com.topdownview.culling.ladder.LadderHelper;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * トップダウン視点用のブロックカリングを管理するシングルトンクラス。
 * 階段、ハシゴ、木、天井、フェードなどの各処理は専用のハンドラークラスに委譲されています。
 */
public final class TopDownCuller {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final double ENTITY_PROTECTION_RADIUS_SQ = 4.0;
    private static final int CACHE_CLEAR_MOVE_THRESHOLD = 3;

    private double playerX;
    private double playerY;
    private double playerZ;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private boolean contextValid = false;

    private int lastPlayerBlockX = Integer.MIN_VALUE;
    private int lastPlayerBlockY = Integer.MIN_VALUE;
    private int lastPlayerBlockZ = Integer.MIN_VALUE;
    private int lastCameraBlockX = Integer.MIN_VALUE;
    private int lastCameraBlockY = Integer.MIN_VALUE;
    private int lastCameraBlockZ = Integer.MIN_VALUE;

    private int lastFadePBlockX = Integer.MIN_VALUE;
    private int lastFadePBlockY = Integer.MIN_VALUE;
    private int lastFadePBlockZ = Integer.MIN_VALUE;
    private int lastFadeCBlockX = Integer.MIN_VALUE;
    private int lastFadeCBlockY = Integer.MIN_VALUE;
    private int lastFadeCBlockZ = Integer.MIN_VALUE;
    private boolean cacheClearedOnDisabled = false;

    private boolean currentSpaceEnclosed = false;
    private SpaceProbe.Result currentSpaceResult = null;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();
    private final MutableBlockPos entityGroundedPos = new MutableBlockPos();

    private final StairCullingHandler stairHandler = new StairCullingHandler();
    private final LadderCullingHandler ladderHandler = new LadderCullingHandler();
    private final TreeCullingHandler treeHandler = new TreeCullingHandler();
    private final CeilingCullingHandler ceilingHandler = new CeilingCullingHandler();
    private final FadeTransitionController fadeTransitionController = new FadeTransitionController();

    private double cachedFadeStart;
    private double cachedFadeNearAlpha;
    private double cachedFadeBlockHitThreshold;
    private int cachedCylinderRadiusHorizontal;
    private int cachedCylinderRadiusVertical;

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
        stairHandler.clearCache();
        ladderHandler.clearCache();
        treeHandler.clearCache();
        ceilingHandler.clearCache();
        fadeTransitionController.clearCache();
        
        currentSpaceEnclosed = false;
        currentSpaceResult = null;
        LadderHelper.clearCache();
        NaturalTreeDetector.clearCache();
        resetLastBlockCoords();
    }

    private void resetLastBlockCoords() {
        fadeTransitionController.clearCache();
        stairHandler.clearCache(); // wait, we don't need resetLastScanPos, clearCache handles variables
        
        lastFadePBlockX = Integer.MIN_VALUE;
        lastFadePBlockY = Integer.MIN_VALUE;
        lastFadePBlockZ = Integer.MIN_VALUE;
        lastFadeCBlockX = Integer.MIN_VALUE;
        lastFadeCBlockY = Integer.MIN_VALUE;
        lastFadeCBlockZ = Integer.MIN_VALUE;
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
        lastCameraBlockX = Integer.MIN_VALUE;
        lastCameraBlockY = Integer.MIN_VALUE;
        lastCameraBlockZ = Integer.MIN_VALUE;
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            if (!cacheClearedOnDisabled) {
                cullingCache.clear();
                fadeCache.clear();
                LadderHelper.clearCache();
                NaturalTreeDetector.clearCache();
                cacheClearedOnDisabled = true;
            }
            return false;
        }
        cacheClearedOnDisabled = false;
        if (level == null) return false;

        long posLong = pos.asLong();
        Boolean cached = cullingCache.get(posLong);
        if (cached != null) return cached;

        if (!contextValid) {
            cullingCache.put(posLong, false);
            return false;
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        if (ModState.STATUS.isMiningMode()) {
            boolean cull = MiningModeCuller.isBlockCulled(pos, level, pX, pY, pZ, cX, cY, cZ);
            cullingCache.put(posLong, cull);
            return cull;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            cullingCache.put(posLong, false);
            return false;
        }

        int playerBlockX = (int) Math.floor(pX);
        int playerBlockZ = (int) Math.floor(pZ);
        int playerFeetY = (int) Math.floor(pY) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            int maxProtectY = Config.isPlayerNearTranslucencyEnabled() ? playerFeetY : playerFeetY + 1;
            if (pos.getY() >= playerFeetY && pos.getY() <= maxProtectY) {
                cullingCache.put(posLong, false);
                return false;
            }
        }

        if (Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(pos, pX, pY, pZ)) {
            if (!isProtectedBlock(pos, state, pY, level)) {
                cullingCache.put(posLong, true);
                return true;
            }
        }

        if (Config.isLadderOccludeEnabled() && ladderHandler.isProtectedPosition(pos)) {
            cullingCache.put(posLong, true);
            return true;
        }

        if (Config.isTreeOccludeEnabled() && treeHandler.isOccludedLog(posLong, pos)) {
            cullingCache.put(posLong, true);
            return true;
        }

        if (isProtectedBlock(pos, state, pY, level)) {
            cullingCache.put(posLong, false);
            return false;
        }

        if (ceilingHandler.isCeilingBlock(posLong)) {
            cullingCache.put(posLong, true);
            return true;
        }

        if (Config.isStaircaseExclusionEnabled() && stairHandler.isExcludedStairBlock(pos)) {
            boolean occludeEnabled = Config.isStaircaseOccludeEnabled();
            cullingCache.put(posLong, occludeEnabled);
            return occludeEnabled;
        }

        float alpha = calculateFadeAlpha(pos, level, state, pX, pY, pZ, cX, cY, cZ);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(posLong, isCulled);
        return isCulled;
    }

    private boolean isPlayerNearBlock(BlockPos pos, double pX, double pY, double pZ) {
        int pBX = (int) Math.floor(pX);
        int pBY = (int) Math.floor(pY);
        int pBZ = (int) Math.floor(pZ);
        int rangeH = Config.getPlayerNearTranslucencyRangeHorizontal();
        int rangeV = Config.getPlayerNearTranslucencyRangeVertical();
        return pos.getX() >= pBX - rangeH && pos.getX() <= pBX + rangeH
            && pos.getZ() >= pBZ - rangeH && pos.getZ() <= pBZ + rangeH
            && pos.getY() >= pBY && pos.getY() < pBY + rangeV;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                pos, pX, pY, pZ, cX, cZ);

        float cylinderAlpha;
        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
            cylinderAlpha = 1.0f;
        } else if (normalizedDistSq <= cachedFadeStart) {
            cylinderAlpha = (float) cachedFadeNearAlpha;
        } else {
            double t = (normalizedDistSq - cachedFadeStart) / (1.0 - cachedFadeStart);
            cylinderAlpha = (float) (cachedFadeNearAlpha + t * (1.0 - cachedFadeNearAlpha));
        }

        float finalAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);
        if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
            return 0.0f;
        }
        return finalAlpha;
    }

    private boolean isProtectedBlock(BlockPos pos, BlockState state, double pY, BlockGetter level) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !TrapdoorHelper.shouldCull(pos, level, state, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        }

        int playerFeetY = (int) Math.floor(pY) - 1;
        boolean ladderOcclude = Config.isLadderOccludeEnabled();

        if (!ladderOcclude && state.getBlock() instanceof LadderBlock) {
            if (LadderHelper.isLadderInLongChain(pos, level)) {
                int chainBottomY = LadderHelper.getChainBottomY(pos, level);
                if (chainBottomY >= playerFeetY && chainBottomY <= playerFeetY + 1) {
                    return true;
                }
            }
        }

        if (!ladderOcclude && LadderHelper.isBlockBehindLadderChain(pos, level, playerFeetY)) {
            return true;
        }

        double blockHeight = 0.0;
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            blockHeight = shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        boolean isThinnerThanSlab = blockHeight > 0.0 && blockHeight < 0.5;

        // MODなどに対応するため、特定のクラスではなく「衝突判定を持たない（通り抜け可能な）」ブロックを
        // 全般的に草や花などの装飾ブロックとみなして保護の対象とする
        boolean isPlantOrDecoration = state.is(net.minecraft.tags.BlockTags.FLOWERS) ||
                state.is(net.minecraft.tags.BlockTags.TALL_FLOWERS) ||
                state.is(net.minecraft.tags.BlockTags.REPLACEABLE) ||
                state.getCollisionShape(level, pos).isEmpty();

        double protectThresholdY = (isThinnerThanSlab || isPlantOrDecoration) ? pY + 1.0 : pY;

        if (pos.getY() + 0.5 < protectThresholdY) return true;

        if (treeHandler.isProtectedLog(pos.asLong())) return true;

        if (InteractableBlocks.isInteractable(state, level, pos)) {
            int checkY = pos.getY();
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                if (state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                    checkY--;
                }
            }
            int protectY = currentSpaceEnclosed ? playerFeetY + 3 : playerFeetY + 1;
            if (checkY <= protectY) {
                return true;
            }
        }
        return false;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            contextValid = false;
            return;
        }

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getEyeY();
        double eyeZ = mc.player.getZ();

        if (!com.topdownview.state.CameraState.isPositionValid(ModState.CAMERA.getCameraPosition())) {
            playerX = Math.floor(eyeX) + 0.5;
            playerY = Math.floor(eyeY) + 0.5;
            playerZ = Math.floor(eyeZ) + 0.5;
            contextValid = false;
            return;
        }

        playerX = Math.floor(eyeX) + 0.5;
        playerY = Math.floor(eyeY) + 0.5;
        playerZ = Math.floor(eyeZ) + 0.5;
        cameraX = Math.floor(ModState.CAMERA.getCameraX()) + 0.5;
        cameraY = Math.floor(ModState.CAMERA.getCameraY()) + 0.5;
        cameraZ = Math.floor(ModState.CAMERA.getCameraZ()) + 0.5;
        contextValid = true;

        CylinderCalculator.updateCache(ModState.CAMERA.getYaw(), Config.getCylinderForwardShift());

        cachedFadeStart = Config.getFadeStart();
        cachedFadeNearAlpha = Config.getFadeNearAlpha();
        cachedFadeBlockHitThreshold = Config.getFadeBlockHitThreshold();
        cachedCylinderRadiusHorizontal = Config.getCylinderRadiusHorizontal();
        cachedCylinderRadiusVertical = Config.getCylinderRadiusVertical();

        int currentBlockX = (int) Math.floor(eyeX);
        int currentBlockY = (int) Math.floor(eyeY);
        int currentBlockZ = (int) Math.floor(eyeZ);
        int currentCamBlockX = (int) Math.floor(ModState.CAMERA.getCameraX());
        int currentCamBlockY = (int) Math.floor(ModState.CAMERA.getCameraY());
        int currentCamBlockZ = (int) Math.floor(ModState.CAMERA.getCameraZ());

        boolean playerMoved = false;
        if (lastPlayerBlockX != Integer.MIN_VALUE) {
            int moveDist = Math.abs(currentBlockX - lastPlayerBlockX)
                    + Math.abs(currentBlockY - lastPlayerBlockY)
                    + Math.abs(currentBlockZ - lastPlayerBlockZ);
            if (moveDist >= CACHE_CLEAR_MOVE_THRESHOLD) playerMoved = true;
        } else {
            lastPlayerBlockX = currentBlockX;
            lastPlayerBlockY = currentBlockY;
            lastPlayerBlockZ = currentBlockZ;
        }

        boolean cameraMoved = false;
        if (lastCameraBlockX != Integer.MIN_VALUE) {
            if (currentCamBlockX != lastCameraBlockX || currentCamBlockY != lastCameraBlockY || currentCamBlockZ != lastCameraBlockZ) {
                cameraMoved = true;
            }
        } else {
            lastCameraBlockX = currentCamBlockX;
            lastCameraBlockY = currentCamBlockY;
            lastCameraBlockZ = currentCamBlockZ;
        }

        if (playerMoved || cameraMoved) {
            cullingCache.clear();
            fadeCache.clear();
            if (playerMoved) {
                lastPlayerBlockX = currentBlockX;
                lastPlayerBlockY = currentBlockY;
                lastPlayerBlockZ = currentBlockZ;
            }
            if (cameraMoved) {
                lastCameraBlockX = currentCamBlockX;
                lastCameraBlockY = currentCamBlockY;
                lastCameraBlockZ = currentCamBlockZ;
            }
        }

        updateSpaceRecognition(mc, currentBlockX, currentBlockY, currentBlockZ);
        treeHandler.updateOcclusion(playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        updateEntityCulling(mc);
    }

    private void updateSpaceRecognition(Minecraft mc, int blockX, int blockY, int blockZ) {
        if (mc.level == null || mc.player == null) {
            currentSpaceEnclosed = false;
            return;
        }

        if (Config.isProtectNaturalTreeLogs()) {
            NaturalTreeDetector.scan(mc.level, blockX, blockY, blockZ, cachedCylinderRadiusHorizontal + 2);
            treeHandler.updateLogs();
        } else {
            NaturalTreeDetector.clearCache();
            treeHandler.clearCache();
        }

        BlockPos seed = mc.player.blockPosition();
        currentSpaceResult = SpaceProbe.probe(mc.level, seed);
        currentSpaceEnclosed = currentSpaceResult.isEnclosed();

        RoomFloodFill.Result roomResult = currentSpaceResult.getRoomResult();
        ceilingHandler.update(currentSpaceEnclosed, roomResult);
        ladderHandler.scan(mc.level, blockX, blockZ, blockY - 1);
        stairHandler.update(mc, blockY, currentSpaceEnclosed, roomResult);
    }

    private void updateEntityCulling(Minecraft mc) {
        if (!ModState.STATUS.isEnabled() || mc.level == null || mc.player == null || !contextValid) return;
        int playerFeetBlockY = (int) Math.floor(mc.player.getY());
        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Player && entity == mc.player) continue;
                if (entity instanceof Cullable cullable) {
                    if (!isCullableEntityType(entity)) {
                        cullable.topdownview_setCulled(false);
                        continue;
                    }
                    boolean shouldCull = entity instanceof Mob ? shouldCullMob(entity, mc, playerFeetBlockY) 
                        : shouldCullDecorativeEntity(entity, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
                    cullable.topdownview_setCulled(shouldCull);
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            LOGGER.debug("[TopDownView] Entity list modified concurrently during culling update, will retry next frame", e);
        }
    }

    private boolean shouldCullMob(Entity entity, Minecraft mc, int playerFeetBlockY) {
        int entityBlockY = entity.getBlockY();
        if (entityBlockY <= playerFeetBlockY + 1 || mc.level == null) return false;
        int ex = entity.getBlockX();
        int ez = entity.getBlockZ();
        boolean grounded = false;
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            entityGroundedPos.set(ex, entityBlockY - yOffset, ez);
            if (!mc.level.getBlockState(entityGroundedPos).isAir()) {
                grounded = true;
                if (isBlockCulled(entityGroundedPos, mc.level)) return true;
                break;
            }
        }
        if (!grounded) return false;
        for (int y = playerFeetBlockY + 1; y < entityBlockY; y++) {
            entityGroundedPos.set(ex, y, ez);
            if (isBlockCulled(entityGroundedPos, mc.level)) return true;
        }
        return false;
    }

    private boolean shouldCullDecorativeEntity(Entity entity, double pX, double pY, double pZ, double cX, double cY, double cZ) {
        Vec3 pos = entity.position();
        double dx = pos.x - pX;
        double dy = pos.y - pY;
        double dz = pos.z - pZ;
        if (dx * dx + dy * dy + dz * dz <= ENTITY_PROTECTION_RADIUS_SQ) return false;
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(pos.x, pos.y, pos.z, pX, pY, pZ, cX, cY, cZ);
        if (normalizedDistSq < 0) return false;
        return normalizedDistSq <= 1.0;
    }

    private boolean isCullableEntityType(Entity entity) {
        if (entity instanceof Mob) return Config.isMobCullingEnabled();
        return entity instanceof ItemFrame || entity instanceof GlowItemFrame || entity instanceof ArmorStand || entity instanceof Painting;
    }

    public void reset() {
        clearCache();
        contextValid = false;
        playerX = playerY = playerZ = cameraX = cameraY = cameraZ = 0.0;
    }

    public int getCulledBlockCount() { return cullingCache.getCulledCount(); }
    public int getCacheSize() { return cullingCache.size(); }

    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ModState.STATUS.isMiningMode()) return 1.0f;
        long posLong = pos.asLong();
        Float cached = fadeCache.getFadeAlpha(posLong);
        if (cached != null) return cached;

        if (Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(pos, playerX, playerY, playerZ)) {
            if (level != null && !isProtectedBlock(pos, level.getBlockState(pos), playerY, level)) {
                float alpha = (float) Config.getPlayerNearTranslucencyAlpha();
                fadeCache.putFadeAlpha(posLong, alpha);
                return alpha;
            }
        }

        if (!Config.isFadeEnabled()) return 1.0f;
        float alpha = calculateFadeAlpha(pos, level, level.getBlockState(pos), playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        fadeCache.putFadeAlpha(posLong, alpha);
        return alpha;
    }

    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ModState.STATUS.isMiningMode() || level == null) return false;
        if (!Config.isFadeEnabled() && !Config.isPlayerNearTranslucencyEnabled() && !Config.isStaircaseOccludeEnabled() 
            && !Config.isLadderOccludeEnabled() && !Config.isTreeOccludeEnabled()) return false;
        if (ceilingHandler.isCeilingBlock(pos.asLong())) return false;
        float alpha = getFadeAlpha(pos, level);
        return alpha < 1.0f && alpha > cachedFadeBlockHitThreshold;
    }

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocks(BlockGetter level) {
        boolean fadeEnabled = Config.isFadeEnabled();
        boolean stairOcclude = Config.isStaircaseExclusionEnabled() && Config.isStaircaseOccludeEnabled();
        boolean ladderOcclude = Config.isLadderOccludeEnabled();
        boolean treeOcclude = Config.isTreeOccludeEnabled();
        boolean playerNearTrans = Config.isPlayerNearTranslucencyEnabled();

        if (!ModState.STATUS.isEnabled() || ModState.STATUS.isMiningMode() || 
            (!fadeEnabled && !stairOcclude && !ladderOcclude && !treeOcclude && !playerNearTrans) || level == null || !contextValid) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            int pBX = (int) Math.floor(playerX);
            int pBY = (int) Math.floor(playerY);
            int pBZ = (int) Math.floor(playerZ);
            int cBX = (int) Math.floor(cameraX);
            int cBY = (int) Math.floor(cameraY);
            int cBZ = (int) Math.floor(cameraZ);
            if (!fadeTransitionController.hasActiveHandoffs() && pBX == lastFadePBlockX && pBY == lastFadePBlockY && pBZ == lastFadePBlockZ
                    && cBX == lastFadeCBlockX && cBY == lastFadeCBlockY && cBZ == lastFadeCBlockZ) {
                return fadeCache.getFadeBlocksCache();
            }
            lastFadePBlockX = pBX;
            lastFadePBlockY = pBY;
            lastFadePBlockZ = pBZ;
            lastFadeCBlockX = cBX;
            lastFadeCBlockY = cBY;
            lastFadeCBlockZ = cBZ;
        }

        fadeCache.clearFadeBlocks();

        if (stairOcclude) stairHandler.collectOcclusionBlocks(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ, fadeCache);
        if (ladderOcclude) ladderHandler.collectOcclusionBlocks(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ, fadeCache);
        if (treeOcclude) treeHandler.collectOcclusionBlocks(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ, fadeCache);

        if ((fadeEnabled || playerNearTrans) && !fadeCache.isFadeBlocksFull()) {
            collectFadeBlocks(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        }

        return fadeCache.getFadeBlocksCache();
    }

    private void collectFadeBlocks(BlockGetter level, double pX, double pY, double pZ, double cX, double cY, double cZ) {
        fadeTransitionController.onStartCollection();

        int radiusH = cachedCylinderRadiusHorizontal;
        int radiusV = cachedCylinderRadiusVertical;
        int margin = 2;

        int minX = (int) Math.floor(Math.min(pX, cX)) - radiusH - margin;
        int maxX = (int) Math.floor(Math.max(pX, cX)) + radiusH + margin;
        int minY = (int) Math.floor(Math.min(pY, cY)) - 1;
        int maxY = (int) Math.floor(Math.max(pY, cY)) + radiusV + margin;
        int minZ = (int) Math.floor(Math.min(pZ, cZ)) - radiusH - margin;
        int maxZ = (int) Math.floor(Math.max(pZ, cZ)) + radiusH + margin;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);
                    boolean fadeEnabled = Config.isFadeEnabled();
                    boolean isNearTarget = Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(mutablePos, pX, pY, pZ);

                    double normalizedDistSq = 0.0;
                    float tempAlpha = 1.0f;
                    if (fadeEnabled) {
                        normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(x + 0.5, y + 0.5, z + 0.5, pX, pY, pZ, cX, cY, cZ);
                        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(mutablePos, pX, pY, pZ, cX, cZ);

                        float cylinderAlpha;
                        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) cylinderAlpha = 1.0f;
                        else if (normalizedDistSq <= cachedFadeStart) cylinderAlpha = (float) cachedFadeNearAlpha;
                        else {
                            double t = (normalizedDistSq - cachedFadeStart) / (1.0 - cachedFadeStart);
                            cylinderAlpha = (float) (cachedFadeNearAlpha + t * (1.0 - cachedFadeNearAlpha));
                        }
                        tempAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);
                    }

                    long posLong = mutablePos.asLong();
                    boolean isTarget = false;
                    float finalAlpha = tempAlpha;

                    if (isNearTarget) {
                        isTarget = true;
                        finalAlpha = (float) Config.getPlayerNearTranslucencyAlpha();
                    } else if (fadeEnabled) {
                        if (tempAlpha > 0.0f && tempAlpha < 1.0f) {
                            isTarget = true;
                            fadeTransitionController.onBlockFaded(posLong);
                        } else if (tempAlpha >= 1.0f) {
                            if (fadeTransitionController.isHandoffActive(posLong)) {
                                isTarget = true;
                                finalAlpha = 1.0f;
                                fadeTransitionController.activateHandoff(posLong);
                            }
                        }
                    }

                    if (!isTarget) continue;

                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    if (isProtectedBlock(mutablePos, state, pY, level)) continue;

                    if (ceilingHandler.isCeilingBlock(posLong)) continue;
                    if (Config.isLadderOccludeEnabled() && ladderHandler.isProtectedPosition(mutablePos)) continue;
                    if (Config.isStaircaseOccludeEnabled() && stairHandler.isExcludedStairBlock(mutablePos)) continue;
                    if (Config.isTreeOccludeEnabled() && treeHandler.isOccludedLog(posLong, mutablePos)) continue;

                    if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                            Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
                        continue;
                    }

                    fadeCache.putFadeBlock(posLong, finalAlpha);
                    if (fadeCache.isFadeBlocksFull()) break;
                }
                if (fadeCache.isFadeBlocksFull()) break;
            }
            if (fadeCache.isFadeBlocksFull()) break;
        }

        fadeTransitionController.onEndCollection();
    }

    public boolean isEntityCulled(Entity entity) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) return false;
        if (entity instanceof Cullable) return ((Cullable) entity).topdownview_isCulled();
        return false;
    }
}