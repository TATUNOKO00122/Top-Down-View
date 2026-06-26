package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.SpaceExplorer;
import com.topdownview.spatial.SpaceRegion;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.ModState;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.TrapDoorBlock;
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
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final double ENTITY_PROTECTION_RADIUS_SQ = 4.0;
    // プレイヤーがこのブロック距離以上移動した時のみカリング/フェードキャッシュをクリア。
    // 1ブロック毎のクリアは地下の階段昇降・洞穴の起伏で頻発しキャッシュミス連鎖を起こすため、
    // マンハッタン距離3まではキャッシュを再利用する（フェード描画で境界変化を補間）。
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

    // フェードブロック再構築判定用: 前回走査時のプレイヤー/カメラブロック座標。
    // 座標が変化した時のみ collectFadeBlocks を再実行し、プレイヤー静止時の毎tick全走査を回避。
    private int lastFadePBlockX = Integer.MIN_VALUE;
    private int lastFadePBlockY = Integer.MIN_VALUE;
    private int lastFadePBlockZ = Integer.MIN_VALUE;
    private int lastFadeCBlockX = Integer.MIN_VALUE;
    private int lastFadeCBlockY = Integer.MIN_VALUE;
    private int lastFadeCBlockZ = Integer.MIN_VALUE;
    private boolean cacheClearedOnDisabled = false;

    // 階段除外：プレイヤー足元〜足元+exclusionHeight の範囲内の階段ブロックはカリングから除外
    private final Set<BlockPos> excludedStairBlocks = new HashSet<>();
    private int lastStairScanBlockX = Integer.MIN_VALUE;
    private int lastStairScanBlockY = Integer.MIN_VALUE;
    private int lastStairScanBlockZ = Integer.MIN_VALUE;

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
        excludedStairBlocks.clear();
        resetLastBlockCoords();
    }

    /**
     * スキャン/フェードキャッシュ用の前回座標を初期値にリセット。
     * clearCache / reset / disable 時のキャッシュ無効化で共有。
     */
    private void resetLastBlockCoords() {
        lastStairScanBlockX = Integer.MIN_VALUE;
        lastStairScanBlockY = Integer.MIN_VALUE;
        lastStairScanBlockZ = Integer.MIN_VALUE;
        lastFadePBlockX = Integer.MIN_VALUE;
        lastFadePBlockY = Integer.MIN_VALUE;
        lastFadePBlockZ = Integer.MIN_VALUE;
        lastFadeCBlockX = Integer.MIN_VALUE;
        lastFadeCBlockY = Integer.MIN_VALUE;
        lastFadeCBlockZ = Integer.MIN_VALUE;
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
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
            if (!cacheClearedOnDisabled) {
                cullingCache.clear();
                fadeCache.clear();
                cacheClearedOnDisabled = true;
            }
            return false;
        }
        cacheClearedOnDisabled = false;

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
        if (state.isAir()) {
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

        // 階段除外：プレイヤー足元〜足元+exclusionHeight の範囲内の階段ブロック
        if (Config.isStaircaseExclusionEnabled() && isExcludedStairBlock(pos, playerFeetY)) {
            // 視線遮蔽時透明化が有効な場合、階段ブロックは常にカリング（通常描画キャンセル）し、
            // TranslucentBlockRenderer 経路で alpha 付き描画する。
            // alpha 値は視線遮蔽の有無で getFadeBlocks() が決定する。
            boolean occludeEnabled = Config.isStaircaseOccludeEnabled();
            cullingCache.put(pos, occludeEnabled);
            return occludeEnabled;
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

        if (state.isAir()) {
            return 1.0f;
        }

        if (isProtectedBlock(pos, state, pY, level)) {
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

        float finalAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);

        // 葉ブロックで、グラフィックス設定がFAST（透過しない設定）の場合、フェード（半透明描画）させず瞬時にカリングする
        if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                net.minecraft.client.Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
            return 0.0f;
        }

        return finalAlpha;
    }

    private boolean isProtectedBlock(BlockPos pos, BlockState state, double pY, BlockGetter level) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            Vec3 pPos = new Vec3(playerX, playerY, playerZ);
            Vec3 cPos = new Vec3(cameraX, cameraY, cameraZ);
            // TrapdoorHelperを使用して、カリング対象外（保護対象）であればtrueを返す
            return !TrapdoorHelper.shouldCull(pos, level, state, pPos, cPos);
        }

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

        if (!com.topdownview.state.CameraState.isPositionValid(ModState.CAMERA.getCameraPosition())) {
            playerX = Math.floor(eyeX) + 0.5;
            playerY = Math.floor(eyeY) + 0.5;
            playerZ = Math.floor(eyeZ) + 0.5;
            contextValid = false;
            return;
        }

        double rawCameraX = ModState.CAMERA.getCameraX();
        double rawCameraY = ModState.CAMERA.getCameraY();
        double rawCameraZ = ModState.CAMERA.getCameraZ();

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

        // マンハッタン距離が閾値以上の時のみキャッシュクリア（地下の微小移動での頻発を抑制）
        if (lastPlayerBlockX != Integer.MIN_VALUE) {
            int moveDist = Math.abs(currentBlockX - lastPlayerBlockX)
                    + Math.abs(currentBlockY - lastPlayerBlockY)
                    + Math.abs(currentBlockZ - lastPlayerBlockZ);
            if (moveDist >= CACHE_CLEAR_MOVE_THRESHOLD) {
                cullingCache.clear();
                fadeCache.clear();
                lastPlayerBlockX = currentBlockX;
                lastPlayerBlockY = currentBlockY;
                lastPlayerBlockZ = currentBlockZ;
            }
        } else {
            lastPlayerBlockX = currentBlockX;
            lastPlayerBlockY = currentBlockY;
            lastPlayerBlockZ = currentBlockZ;
        }

        // 階段除外リストを更新（プレイヤーがブロック境界を超えたら再検出）
        if (Config.isStaircaseExclusionEnabled()) {
            updateStairExclusion(mc, currentBlockX, currentBlockY, currentBlockZ);
        }

        updateEntityCulling(mc);
    }

    /**
     * 階段除外リストを更新。
     * プレイヤーが別ブロックに移動した時のみ再検出する（重い処理を毎tick走らせない）。
     * 検出された階段の全段から、プレイヤー足元〜足元+exclusionHeight の範囲内のものを抽出。
     */
    private void updateStairExclusion(Minecraft mc, int blockX, int blockY, int blockZ) {
        if (!Config.isStaircaseExclusionEnabled()) {
            if (!excludedStairBlocks.isEmpty()) {
                excludedStairBlocks.clear();
            }
            return;
        }

        // 前回スキャン位置からのマンハッタン距離を判定し、4ブロック未満であれば再利用（走査頻度の削減）
        if (lastStairScanBlockX != Integer.MIN_VALUE &&
            lastStairScanBlockY != Integer.MIN_VALUE &&
            lastStairScanBlockZ != Integer.MIN_VALUE) {
            int dx = Math.abs(blockX - lastStairScanBlockX);
            int dy = Math.abs(blockY - lastStairScanBlockY);
            int dz = Math.abs(blockZ - lastStairScanBlockZ);
            if (dx + dy + dz < 4) {
                return;
            }
        }
        lastStairScanBlockX = blockX;
        lastStairScanBlockY = blockY;
        lastStairScanBlockZ = blockZ;

        excludedStairBlocks.clear();

        if (mc.level == null || mc.player == null) {
            return;
        }

        BlockPos seed = mc.player.blockPosition();
        SpaceRegion region = SpaceExplorer.explore(mc.level, seed,
                com.topdownview.state.SpaceDebugState.MAX_EXPLORE_BLOCKS,
                com.topdownview.state.SpaceDebugState.MAX_WALL_THICKNESS,
                com.topdownview.state.SpaceDebugState.MAX_HOLE_SIZE);
        if (!region.isValid()) {
            return;
        }

        List<Staircase> staircases = StairAnalyzer.detect(mc.level, region,
                com.topdownview.state.SpaceDebugState.MIN_STAIRCASE_STEPS);
        if (staircases.isEmpty()) {
            return;
        }

        // 足元Y（足元ブロック = eyeY-1 の床 = eyeY-2）。playerY は eyeY のブロック中心。
        // update() で playerY = floor(eyeY)+0.5。足元床ブロック = floor(eyeY)-1。
        int playerFeetY = blockY - 1;
        int exclusionHeight = Config.getStaircaseExclusionHeight();
        int minY = playerFeetY;
        int maxY = playerFeetY + exclusionHeight;

        for (Staircase stair : staircases) {
            for (BlockPos step : stair.getSteps()) {
                if (step.getY() >= minY && step.getY() <= maxY) {
                    excludedStairBlocks.add(step.immutable());
                }
            }
        }
    }

    /**
     * 指定ブロックが階段除外リストに含まれるか。
     * Y範囲チェックは updateStairExclusion で行うため、ここではSetの包含のみ。
     */
    private boolean isExcludedStairBlock(BlockPos pos, int playerFeetY) {
        if (excludedStairBlocks.isEmpty()) {
            return false;
        }
        return excludedStairBlocks.contains(pos);
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

        try {
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
        } catch (java.util.ConcurrentModificationException e) {
            // エンティティリストが別スレッドで変更された - 次フレームで再試行
            LOGGER.debug("[TopDownView] Entity list modified concurrently during culling update, will retry next frame", e);
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
        if (entity instanceof Mob) {
            return Config.isMobCullingEnabled();
        }
        return entity instanceof ItemFrame
            || entity instanceof GlowItemFrame
            || entity instanceof ArmorStand
            || entity instanceof Painting;
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        excludedStairBlocks.clear();
        resetLastBlockCoords();
        contextValid = false;
        playerX = 0.0;
        playerY = 0.0;
        playerZ = 0.0;
        cameraX = 0.0;
        cameraY = 0.0;
        cameraZ = 0.0;
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
        boolean fadeEnabled = Config.isFadeEnabled();
        boolean stairOccludeEnabled = Config.isStaircaseExclusionEnabled() && Config.isStaircaseOccludeEnabled();

        if (!ModState.STATUS.isEnabled() || ModState.STATUS.isMiningMode()
                || (!fadeEnabled && !stairOccludeEnabled)) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        if (level == null || !contextValid) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        // プレイヤー/カメラのブロック座標が変化した時のみフェードブロックを再構築。
        // プレイヤー静止時は毎tickの3重ループ全走査を回避（地下での主要な負荷源）。
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            int pBX = (int) Math.floor(pX);
            int pBY = (int) Math.floor(pY);
            int pBZ = (int) Math.floor(pZ);
            int cBX = (int) Math.floor(cX);
            int cBY = (int) Math.floor(cY);
            int cBZ = (int) Math.floor(cZ);
            if (pBX == lastFadePBlockX && pBY == lastFadePBlockY && pBZ == lastFadePBlockZ
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

        // 階段視線遮蔽ブロック収集を先に行う（優先度高、フェード有無に関わらず動作）
        if (stairOccludeEnabled) {
            collectStairOcclusionBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        // フェードブロック収集（フェード有効時のみ）
        if (fadeEnabled && !fadeCache.isFadeBlocksFull()) {
            collectFadeBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        return fadeCache.getFadeBlocksCache();
    }

    private void collectFadeBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        int radiusH = Config.getCylinderRadiusHorizontal();
        int radiusV = Config.getCylinderRadiusVertical();
        int margin = 2;

        int minX = (int) Math.floor(Math.min(pX, cX)) - radiusH - margin;
        int maxX = (int) Math.floor(Math.max(pX, cX)) + radiusH + margin;
        int minY = (int) Math.floor(Math.min(pY, cY)) - 1;
        int maxY = (int) Math.floor(Math.max(pY, cY)) + radiusV + margin;
        int minZ = (int) Math.floor(Math.min(pZ, cZ)) - radiusH - margin;
        int maxZ = (int) Math.floor(Math.max(pZ, cZ)) + radiusH + margin;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    float alpha = calculateFadeAlpha(mutablePos, level, state, pX, pY, pZ, cX, cY, cZ);
                    if (alpha > 0.0f && alpha < 1.0f) {
                        fadeCache.putFadeBlock(new BlockPos(x, y, z), alpha);

                        if (fadeCache.isFadeBlocksFull()) {
                            return;
                        }
                    } else if (alpha >= 1.0f && isNearCullingBoundary(mutablePos, pX, pY, pZ, cX, cY, cZ)) {
                        // カリング境界のすぐ外側: メッシュ再構築遅延による点滅防止用安全マージン
                        fadeCache.putFadeBlock(new BlockPos(x, y, z), 1.0f);

                        if (fadeCache.isFadeBlocksFull()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * 階段視線遮蔽ブロック収集。
     * excludedStairBlocks の各ブロックについて視線遮蔽判定を行い、
     * 遮蔽時は staircaseOccludeAlpha、非遮蔽時は 1.0（ほぼ不透明）で fadeCache に追加。
     * alpha=1.0 で translucent 経路描画することで、チャンクメッシュ再構築不要化。
     */
    private void collectStairOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        if (excludedStairBlocks.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getStaircaseOccludeAlpha();
        Vec3 camera = new Vec3(cX, cY, cZ);
        Vec3 player = new Vec3(pX, pY, pZ);

        for (BlockPos pos : excludedStairBlocks) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            float alpha = isStairOccludingView(pos, camera, player) ? occludeAlpha : 1.0f;
            fadeCache.putFadeBlock(pos.immutable(), alpha);
        }
    }

    /**
     * 視線遮蔽判定: カメラ→プレイヤーの視線レイがブロックの単位立方体AABBを通過するか。
     * スラブ法によるレイ-AABB交差判定。カメラ〜プレイヤー間の範囲のみ判定。
     */
    private boolean isStairOccludingView(BlockPos pos, Vec3 camera, Vec3 player) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();

        double dirX = player.x - camera.x;
        double dirY = player.y - camera.y;
        double dirZ = player.z - camera.z;
        double rayLengthSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (rayLengthSq < 1.0E-12) {
            // カメラとプレイヤーが同一位置: 視線なし
            return false;
        }

        // t[0]=tmin, t[1]=tmax。カメラ〜プレイヤー間の範囲のみ判定
        double[] t = {0.0, 1.0};

        if (!slabIntersect(camera.x, dirX, minX, minX + 1.0, t)) return false;
        if (!slabIntersect(camera.y, dirY, minY, minY + 1.0, t)) return false;
        if (!slabIntersect(camera.z, dirZ, minZ, minZ + 1.0, t)) return false;

        return true;
    }

    /**
     * スラブ法による1軸のレイ-AABB交差判定。
     * t[0]=tmin, t[1]=tmax を直接更新する（呼び出し側で配列を再利用）。
     * レイが軸に平行な場合は原点がスラブ内にあるかのみ判定。
     */
    private static boolean slabIntersect(double origin, double dir, double min, double max, double[] t) {
        if (Math.abs(dir) < 1.0E-9) {
            // レイが軸に平行: 原点がスラブ内にあるか
            return origin >= min && origin <= max;
        }
        double invDir = 1.0 / dir;
        double t1 = (min - origin) * invDir;
        double t2 = (max - origin) * invDir;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        t[0] = Math.max(t[0], t1);
        t[1] = Math.min(t[1], t2);
        return t[0] <= t[1];
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

    /**
     * 指定されたブロック位置がカリング境界のすぐ外側（シリンダー正規化距離の二乗が 1.0 から 1.5 の間）にあるかを判定します。
     * メッシュの再構築遅延によるブロックの一瞬の消失（点滅）を防ぐための安全マージンとして使用します。
     */
    private boolean isNearCullingBoundary(BlockPos pos, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        // シリンダー外（normalizedDistSq > 1.0）だが、境界の近く（1.5以内）のブロック
        return normalizedDistSq > 1.0 && normalizedDistSq <= 1.5;
    }
}