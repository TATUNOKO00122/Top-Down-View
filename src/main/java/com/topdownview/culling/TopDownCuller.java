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
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

/**
 * トップダウン視点用のブロックカリングを管理するシングルトンクラス。
 * 
 * <p>ハイブリッドカリング戦略:
 * <ul>
 *   <li>シリンダーゾーン: カメラとプレイヤー間の可視領域</li>
 *   <li>逆ピラミッド保護: プレイヤー周辺の足元ブロック保護</li>
 *   <li>フェード境界: カリング境界での滑らかな透過遷移</li>
 * </ul>
 */
public final class TopDownCuller {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final double ENTITY_PROTECTION_RADIUS_SQ = 4.0;

    private volatile double playerX;
    private volatile double playerY;
    private volatile double playerZ;
    private volatile double cameraX;
    private volatile double cameraY;
    private volatile double cameraZ;
    private volatile boolean contextValid = false;

    private volatile int lastPlayerBlockX = Integer.MIN_VALUE;
    private volatile int lastPlayerBlockY = Integer.MIN_VALUE;
    private volatile int lastPlayerBlockZ = Integer.MIN_VALUE;

    private volatile long lastFadeBlocksUpdateTick = -1;

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

        if (!contextValid) {
            cullingCache.put(pos, false);
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
            cullingCache.put(pos, cull);
            return cull;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cullingCache.put(pos, false);
            return false;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pY)) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        int playerBlockX = (int) Math.floor(pX);
        int playerBlockZ = (int) Math.floor(pZ);
        int playerFeetY = (int) Math.floor(pY) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        float alpha = calculateFadeAlpha(pos, level, state, pX, pY, pZ, cX, cY, cZ);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(pos, isCulled);
        return isCulled;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cY, double cZ) {
        if (level == null) {
            return 1.0f;
        }

        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 1.0f;
        }

        if (isProtectedBlock(pos, state, pY)) {
            return 1.0f;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                pos, pX, pY, pZ, cX, cZ);

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

    private boolean isProtectedBlock(BlockPos pos, BlockState state, double pY) {
        if (pos.getY() + 0.5 < pY) {
            return true;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pY)) {
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

        double rawCameraX = ModState.CAMERA.getCameraX();
        double rawCameraY = ModState.CAMERA.getCameraY();
        double rawCameraZ = ModState.CAMERA.getCameraZ();

        if (rawCameraX == 0.0 && rawCameraY == 0.0 && rawCameraZ == 0.0) {
            playerX = Math.floor(eyeX) + 0.5;
            playerY = Math.floor(eyeY) + 0.5;
            playerZ = Math.floor(eyeZ) + 0.5;
            contextValid = false;
            return;
        }

        playerX = Math.floor(eyeX) + 0.5;
        playerY = Math.floor(eyeY) + 0.5;
        playerZ = Math.floor(eyeZ) + 0.5;
        cameraX = Math.floor(rawCameraX) + 0.5;
        cameraY = Math.floor(rawCameraY) + 0.5;
        cameraZ = Math.floor(rawCameraZ) + 0.5;
        contextValid = true;

        int currentBlockX = (int) Math.floor(eyeX);
        int currentBlockY = (int) Math.floor(eyeY);
        int currentBlockZ = (int) Math.floor(eyeZ);

        if (currentBlockX != lastPlayerBlockX || currentBlockY != lastPlayerBlockY || currentBlockZ != lastPlayerBlockZ) {
            cullingCache.clear();
            fadeCache.clear();
            lastPlayerBlockX = currentBlockX;
            lastPlayerBlockY = currentBlockY;
            lastPlayerBlockZ = currentBlockZ;
        }

        updateEntityCulling(mc);
    }

    private void updateEntityCulling(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (mc.level == null || mc.player == null) {
            return;
        }

        if (!contextValid) {
            return;
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        int playerFeetBlockY = (int) Math.floor(mc.player.getY());

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity == mc.player) {
                continue;
            }

            if (entity instanceof Cullable) {
                Cullable cullable = (Cullable) entity;

                if (!isCullableEntityType(entity)) {
                    cullable.topdownview_setCulled(false);
                    continue;
                }

                boolean isMob = entity instanceof Mob;
                boolean shouldCull;

                if (isMob) {
                    shouldCull = shouldCullMob(entity, mc, playerFeetBlockY);
                } else {
                    shouldCull = shouldCullDecorativeEntity(entity, pX, pY, pZ, cX, cY, cZ);
                }

                cullable.topdownview_setCulled(shouldCull);
            }
        }
    }

    private boolean shouldCullMob(Entity entity, Minecraft mc, int playerFeetBlockY) {
        int entityBlockY = entity.getBlockY();

        if (entityBlockY <= playerFeetBlockY + 1) {
            return false;
        }

        if (!isEntityGrounded(entity, mc)) {
            return false;
        }

        return true;
    }

    private boolean shouldCullDecorativeEntity(Entity entity, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        Vec3 entityPos = entity.position();
        double ex = entityPos.x;
        double ey = entityPos.y;
        double ez = entityPos.z;

        double dx = ex - pX;
        double dy = ey - pY;
        double dz = ez - pZ;
        double distToPlayerSq = dx * dx + dy * dy + dz * dz;

        if (distToPlayerSq <= ENTITY_PROTECTION_RADIUS_SQ) {
            return false;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                ex, ey, ez,
                pX, pY, pZ,
                cX, cY, cZ);

        if (normalizedDistSq < 0) {
            return false;
        }

        return normalizedDistSq <= 1.0;
    }

    private boolean isEntityGrounded(Entity entity, Minecraft mc) {
        if (mc.level == null) {
            return false;
        }

        int entityBlockY = entity.getBlockY();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            mutablePos.set(entity.getBlockX(), entityBlockY - yOffset, entity.getBlockZ());
            if (!mc.level.getBlockState(mutablePos).isAir()) {
                return true;
            }
        }

        return false;
    }

    private boolean isCullableEntityType(Entity entity) {
        return entity instanceof Mob
            || entity instanceof ItemFrame
            || entity instanceof GlowItemFrame
            || entity instanceof ArmorStand
            || entity instanceof Painting;
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        contextValid = false;
        playerX = 0.0;
        playerY = 0.0;
        playerZ = 0.0;
        cameraX = 0.0;
        cameraY = 0.0;
        cameraZ = 0.0;
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
        float alpha = calculateFadeAlpha(pos, level, state,
                playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
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
        if (!ModState.STATUS.isEnabled()) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        if (ModState.STATUS.isMiningMode()) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        if (!Config.isFadeEnabled()) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        if (level == null || !contextValid) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long currentTick = mc.level.getGameTime();
            if (currentTick == lastFadeBlocksUpdateTick) {
                return fadeCache.getFadeBlocksCache();
            }
            lastFadeBlocksUpdateTick = currentTick;
        }

        fadeCache.clearFadeBlocks();

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        int rangeH = Config.getCylinderRadiusHorizontal() + 2;
        int rangeV = Config.getCylinderRadiusVertical() + 3;

        int scanRangeXZ = Math.max(rangeH, 4);

        int minX = (int) Math.floor(pX) - scanRangeXZ;
        int maxX = (int) Math.floor(pX) + scanRangeXZ;
        int minY = (int) Math.floor(pY) - 1;
        int maxY = (int) Math.floor(pY) + rangeV;
        int minZ = (int) Math.floor(pZ) - scanRangeXZ;
        int maxZ = (int) Math.floor(pZ) + scanRangeXZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    float alpha = calculateFadeAlpha(mutablePos, level, state,
                            pX, pY, pZ, cX, cY, cZ);
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

    public boolean isEntityCulled(Entity entity) {
        if (!ModState.STATUS.isEnabled()) {
            return false;
        }
        if (entity instanceof Cullable) {
            return ((Cullable) entity).topdownview_isCulled();
        }
        return false;
    }
}