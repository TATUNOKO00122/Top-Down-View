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
    private final MutableBlockPos entityGroundedPos = new MutableBlockPos();

    // フレーム毎にキャッシュされる Config 値
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

        long posLong = pos.asLong();
        Boolean cached = cullingCache.get(posLong);
        if (cached != null) {
            return cached;
        }

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

        // プレイヤー自身のいる場所（足元〜頭上まで保護）
        int playerBlockX = (int) Math.floor(pX);
        int playerBlockZ = (int) Math.floor(pZ);
        int playerFeetY = (int) Math.floor(pY) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cullingCache.put(posLong, false);
                return false;
            }
        }

        // その他保護対象ブロック判定（Trapdoor, 足元より下, インタラクト可能ブロック等）
        if (isProtectedBlock(pos, state, pY, level)) {
            cullingCache.put(posLong, false);
            return false;
        }

        // 階段除外：プレイヤー足元〜足元+exclusionHeight の範囲内の階段ブロック
        if (Config.isStaircaseExclusionEnabled() && isExcludedStairBlock(pos, playerFeetY)) {
            boolean occludeEnabled = Config.isStaircaseOccludeEnabled();
            cullingCache.put(posLong, occludeEnabled);
            return occludeEnabled;
        }

        float alpha = calculateFadeAlpha(pos, level, state, pX, pY, pZ, cX, cY, cZ);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(posLong, isCulled);
        return isCulled;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                pos, pX, pY, pZ, cX, cZ);

        double fadeStart = this.cachedFadeStart;
        double fadeNearAlpha = this.cachedFadeNearAlpha;

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
            // TrapdoorHelperを使用して、カリング対象外（保護対象）であればtrueを返す
            // プリミティブ値版 shouldCull() を呼び出して Vec3 生成を回避
            return !TrapdoorHelper.shouldCull(pos, level, state, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        }

        if (pos.getY() + 0.5 < pY) {
            return true;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            int checkY = pos.getY();
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                if (state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                    checkY--;
                }
            }
            if (checkY <= Math.floor(pY)) {
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

        // シリンダー計算用の事前パラメータ（sin/cos/シフト）を更新
        CylinderCalculator.updateCache(ModState.CAMERA.getYaw(), Config.getCylinderForwardShift());

        // Config値をフレームキャッシュ
        cachedFadeStart = Config.getFadeStart();
        cachedFadeNearAlpha = Config.getFadeNearAlpha();
        cachedFadeBlockHitThreshold = Config.getFadeBlockHitThreshold();
        cachedCylinderRadiusHorizontal = Config.getCylinderRadiusHorizontal();
        cachedCylinderRadiusVertical = Config.getCylinderRadiusVertical();

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
            // 天井の階段などを誤検出・除外しないよう、階段の最下段がプレイヤーの足元+1以下から始まるもののみに限定
            if (stair.getBottomPos().getY() <= playerFeetY + 1) {
                for (BlockPos step : stair.getSteps()) {
                    if (step.getY() >= minY && step.getY() <= maxY) {
                        excludedStairBlocks.add(step.immutable());
                    }
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

        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            entityGroundedPos.set(entity.getBlockX(), entityBlockY - yOffset, entity.getBlockZ());
            if (!mc.level.getBlockState(entityGroundedPos).isAir()) {
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

        long posLong = pos.asLong();
        Float cached = fadeCache.getFadeAlpha(posLong);
        if (cached != null) {
            return cached;
        }

        BlockState state = level.getBlockState(pos);
        float alpha = calculateFadeAlpha(pos, level, state,
                playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        fadeCache.putFadeAlpha(posLong, alpha);

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
        return alpha > this.cachedFadeBlockHitThreshold;
    }

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocks(BlockGetter level) {
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
        int radiusH = this.cachedCylinderRadiusHorizontal;
        int radiusV = this.cachedCylinderRadiusVertical;
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

                    // 1. 重い getBlockState を呼ぶ前に、数学的なアルファ値を先に計算して判定する
                    double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                            x + 0.5, y + 0.5, z + 0.5,
                            pX, pY, pZ, cX, cY, cZ);
                    double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                            mutablePos, pX, pY, pZ, cX, cZ);

                    float cylinderAlpha;
                    if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
                        cylinderAlpha = 1.0f;
                    } else if (normalizedDistSq <= this.cachedFadeStart) {
                        cylinderAlpha = (float) this.cachedFadeNearAlpha;
                    } else {
                        double t = (normalizedDistSq - this.cachedFadeStart) / (1.0 - this.cachedFadeStart);
                        cylinderAlpha = (float) (this.cachedFadeNearAlpha + t * (1.0 - this.cachedFadeNearAlpha));
                    }

                    float tempAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);

                    // フェード対象ブロック、または境界マージン内のブロックか判定
                    boolean isTarget = false;
                    float finalAlpha = tempAlpha;
                    if (tempAlpha > 0.0f && tempAlpha < 1.0f) {
                        isTarget = true;
                    } else if (tempAlpha >= 1.0f) {
                        // カリング境界のすぐ外側: メッシュ再構築遅延による点滅防止用安全マージン
                        if (normalizedDistSq > 1.0 && normalizedDistSq <= 1.5) {
                            isTarget = true;
                            finalAlpha = 1.0f;
                        }
                    }

                    if (!isTarget) {
                        continue;
                    }

                    // 2. フェード対象の場合のみ、重い getBlockState を呼び出す
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    // 3. 保護対象ブロック（プレイヤー足元、Trapdoor、インタラクト可能など）はフェードさせない
                    if (isProtectedBlock(mutablePos, state, pY, level)) {
                        continue;
                    }

                    // 4. 葉ブロックのFASTグラフィックス設定の処理
                    if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                            net.minecraft.client.Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
                        continue;
                    }

                    // 5. キャッシュへ追加
                    fadeCache.putFadeBlock(mutablePos.asLong(), finalAlpha);

                    if (fadeCache.isFadeBlocksFull()) {
                        return;
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

        for (BlockPos pos : excludedStairBlocks) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            float alpha = isStairOccludingView(pos, cX, cY, cZ, pX, pY, pZ) ? occludeAlpha : 1.0f;
            fadeCache.putFadeBlock(pos.asLong(), alpha);
        }
    }

    /**
     * 視線遮蔽判定: カメラ→プレイヤーの視線レイがブロックの単位立方体AABBを通過するか。
     * スラブ法によるレイ-AABB交差判定。カメラ〜プレイヤー間の範囲のみ判定。
     */
    private boolean isStairOccludingView(BlockPos pos, double cX, double cY, double cZ, double pX, double pY, double pZ) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();

        double dirX = pX - cX;
        double dirY = pY - cY;
        double dirZ = pZ - cZ;
        double rayLengthSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (rayLengthSq < 1.0E-12) {
            // カメラとプレイヤーが同一位置: 視線なし
            return false;
        }

        // t[0]=tmin, t[1]=tmax。カメラ〜プレイヤー間の範囲のみ判定
        double[] t = {0.0, 1.0};

        if (!slabIntersect(cX, dirX, minX, minX + 1.0, t)) return false;
        if (!slabIntersect(cY, dirY, minY, minY + 1.0, t)) return false;
        if (!slabIntersect(cZ, dirZ, minZ, minZ + 1.0, t)) return false;

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

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocksCache() {
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