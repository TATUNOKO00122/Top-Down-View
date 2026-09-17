package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.config.CullingConfig;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.compat.VerticalUnitHelper;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.cache.SurfaceHeightCache;
import com.topdownview.culling.geometry.BlockChangeBox;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.state.ModState;
import com.topdownview.culling.ladder.LadderHelper;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.topdownview.util.PerfMonitor;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    private RoomFloodFill.Scratch spaceScratch = new RoomFloodFill.Scratch();
    private BlockPos lastSpaceSeed = null;
    private ResourceKey<Level> lastSpaceDimension = null;

    // ==================== 非同期空間解析 ====================
    /**
     * flood/segment は密な空間で 100ms 超になるため、Render thread の同期実行をやめ
     * 1スレッドの daemon ワーカーで計算し、完成品を volatile スロット経由で受け取る。
     * 排他は「投げる側=メインスレッド、受け取る側=メインスレッド」に限定し、
     * ワーカーは単一スロットへの1回の volatile 書き込みしかしない。
     */
    private ExecutorService probeExecutor;
    private volatile TopDownCuller.ProbeOutcome probeOutcome;
    private volatile boolean probeInFlight;
    private volatile int probeEpoch;
    /** probe が失敗した直後に再試行しない期間(ワーカー例外のループ防止)。 */
    private long probeRetryAfterNanos;
    private static final long PROBE_RETRY_NANOS = 500_000_000L;
    /** ワーカー例外ログのスパム防止。 */
    private long probeErrorLogAfterNanos;

    /**
     * ワーカーからメインスレッドへ渡す probe 完了データ。不変なので参照の volatile 読みだけで安全に受け渡せる。
     */
    private record ProbeOutcome(int epoch, Level level, BlockPos seed,
                                SpaceProbe.Result result, RoomFloodFill.Scratch scratch, long elapsedNanos) {
    }

    /** 空間判定を再実行するプレイヤーシードの移動量（マンハッタン）。 */
    private static final int SPACE_REPROBE_MOVE_THRESHOLD = 3;

    /** 立ち位置の基準 Y を求めるときに足元から下へ探す最大ブロック数（ジャンプ・段差を吸収）。 */
    private static final int STANDING_SCAN_DOWN = 4;

    /** 直前に屋内と判定した座標。この近くの非屋内判定は段差等による一瞬のブレとして無視する。 */
    private BlockPos lastEnclosedSeed = null;
    private static final int ENCLOSED_STICKY_MOVE = 3;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();
    private final SurfaceHeightCache surfaceHeightCache = new SurfaceHeightCache();
    private final MutableBlockPos entityGroundedPos = new MutableBlockPos();

    private final StairCullingHandler stairHandler = new StairCullingHandler();
    private final LadderCullingHandler ladderHandler = new LadderCullingHandler();
    private final TreeCullingHandler treeHandler = new TreeCullingHandler();
    private final CeilingSliceCuller ceilingSliceCuller = new CeilingSliceCuller();
    private final CoverCullingHandler coverHandler = new CoverCullingHandler();
    private final FadeTransitionController fadeTransitionController = new FadeTransitionController();

    private double cachedFadeStart;
    private double cachedFadeNearAlpha;
    private double cachedFadeBlockHitThreshold;
    private int cachedCylinderRadiusHorizontal;
    private int cachedCylinderRadiusVertical;
    private boolean cachedViewWedgeProtection;
    /** 壁パネル/天井スライスの差分を union した再構築範囲。 */
    private final BlockChangeBox pendingElementChange = new BlockChangeBox();
    private boolean cachedCoverCullingActive;
    private boolean cachedDisableIndoorFade;
    private int cachedCullingMode;
    private boolean cachedIndoorElementActive;
    private boolean cachedIndoorCeilingEnabled;
    private double cachedViewWedgeCos;
    private double viewDirX = 0.0;
    private double viewDirZ = 1.0;

    private boolean undergroundCullingActive = false;
    private double undergroundCullingStartDistSq = 0.0;
    private int cachedUndergroundCullingKeepDepth = 0;
    private ResourceKey<Level> lastSurfaceCacheDimension = null;

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
        surfaceHeightCache.clear();
        stairHandler.clearCache();
        ladderHandler.clearCache();
        treeHandler.clearCache();
        ceilingSliceCuller.clearCache();
        coverHandler.clearCache();
        fadeTransitionController.clearCache();
        
        currentSpaceEnclosed = false;
        cachedDisableIndoorFade = false;
        cachedIndoorElementActive = false;
        cachedCoverCullingActive = false;
        currentSpaceResult = null;
        spaceScratch.clear();
        lastSpaceSeed = null;
        lastSpaceDimension = null;
        lastEnclosedSeed = null;
        probeEpoch++;
        probeOutcome = null;
        probeInFlight = false;
        probeRetryAfterNanos = 0L;
        pendingElementChange.reset();
        // 次元/ワールドをまたいだ差分を持ち越さない。
        ceilingSliceCuller.clearPendingChange();
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

        PerfMonitor.IS_BLOCK_CULLED.increment();

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
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ
                && pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
            cullingCache.put(posLong, false);
            return false;
        }

        // 屋内天井スライスは保護の影響を受けずに消す。これ以外のブロックは下の通常カリングへ流す。
        if (cachedIndoorElementActive && ceilingSliceCuller.isCeilingSliceBlock(posLong)) {
            cullingCache.put(posLong, true);
            return true;
        }

        if (undergroundCullingActive && isVerticallyCulled(pos)) {
            cullingCache.put(posLong, true);
            return true;
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

        if (cachedCoverCullingActive && coverHandler.isCoverCulled(pos)) {
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

    /**
     * 注視点（プレイヤー）から一定距離以上離れた列で、地表から深い位置のブロックをカリングする。
     * トップダウン視点では地表下は見えないため、遠方の地下ジオメトリを削減する。
     */
    private boolean isVerticallyCulled(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - playerX;
        double dz = (pos.getZ() + 0.5) - playerZ;
        if (dx * dx + dz * dz < undergroundCullingStartDistSq) {
            return false;
        }
        int surfaceY = surfaceHeightCache.getSurfaceY(pos.getX(), pos.getZ());
        if (surfaceY == SurfaceHeightCache.UNKNOWN) {
            return false;
        }
        // カメラが地表以下（屋内・地下・ネザー天井下など）の列は、地表が視界を遮らないため対象外
        if (cameraY <= surfaceY) {
            return false;
        }
        return pos.getY() < surfaceY - cachedUndergroundCullingKeepDepth;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);

        // 円柱内でも、プレイヤーより奥や真横のブロックは保護する。
        // カメラ側(手前)の視界コーン内だけをカリングし、手前の壁を通り抜けて見えるようにする。
        if (cachedViewWedgeProtection && normalizedDistSq >= 0.0 && normalizedDistSq <= 1.0
                && !OcclusionCalculator.isWithinViewWedge(
                        pos.getX() + 0.5, pos.getZ() + 0.5,
                        pX, pZ, viewDirX, viewDirZ, cachedViewWedgeCos)) {
            return 1.0f;
        }

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
        // FASTグラフィックの葉は不透明テクスチャで描かれるため、半透明にすると「別ブロック」のように
        // 見える。見た目の変化を避けるため、半透明化せず完全にカリングして視界から消す。
        if (finalAlpha < 1.0f && isFastGraphicsLeaves(state)) {
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

        // ドア・ウェイストーン・FastPaintingsの絵画など上下に連結した構造は、最下段のYを基準に
        // 1単位として扱い、一部の段だけがカリングされて見た目が欠けるのを防ぐ。
        int blockY = VerticalUnitHelper.getUnitAnchorY(state, pos, level);

        double blockHeight = 0.0;
        VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            blockHeight = shape.max(Direction.Axis.Y);
        }
        boolean isThinnerThanSlab = blockHeight > 0.0 && blockHeight < 0.5;

        // MODなどに対応するため、特定のクラスではなく「衝突判定を持たない（通り抜け可能な）」ブロックを
        // 全般的に草や花などの装飾ブロックとみなして保護の対象とする
        boolean isPlantOrDecoration = state.is(BlockTags.FLOWERS) ||
                state.is(BlockTags.TALL_FLOWERS) ||
                state.is(BlockTags.REPLACEABLE) ||
                state.getCollisionShape(level, pos).isEmpty();

        double protectThresholdY = (isThinnerThanSlab || isPlantOrDecoration) ? pY + 1.0 : pY;

        // 眼の高さ以下のY保護。下側が残ると壁がプレイヤーを隠すため。
        if (blockY + 0.5 < protectThresholdY) return true;

        if (treeHandler.isProtectedLog(pos.asLong())) return true;

        if (InteractableBlocks.isInteractable(state, level, pos)) {
            int protectY = currentSpaceEnclosed ? playerFeetY + 3 : playerFeetY + 1;
            if (blockY <= protectY) {
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
        cachedCullingMode = Config.getCullingMode();
        cachedIndoorCeilingEnabled = Config.isIndoorCeilingCullingEnabled();
        cachedViewWedgeProtection = cachedCullingMode == CullingConfig.CULLING_MODE_COVER_CORRIDOR;
        cachedViewWedgeCos = Math.cos(Math.toRadians(Config.getViewWedgeHalfAngle()));
        double wedgeDirX = playerX - cameraX;
        double wedgeDirZ = playerZ - cameraZ;
        double wedgeDirLen = Math.sqrt(wedgeDirX * wedgeDirX + wedgeDirZ * wedgeDirZ);
        if (wedgeDirLen < 1.0E-4) {
            // カメラが真上付近: yaw から前方向を求める(CylinderCalculator と同じ規約)
            double yawRad = Math.toRadians(ModState.CAMERA.getYaw());
            viewDirX = -Math.sin(yawRad);
            viewDirZ = Math.cos(yawRad);
        } else {
            viewDirX = wedgeDirX / wedgeDirLen;
            viewDirZ = wedgeDirZ / wedgeDirLen;
        }

        undergroundCullingActive = Config.isUndergroundCullingEnabled();
        double undergroundCullingStartBlocks = Config.getUndergroundCullingStartDistance() * 16.0;
        undergroundCullingStartDistSq = undergroundCullingStartBlocks * undergroundCullingStartBlocks;
        cachedUndergroundCullingKeepDepth = Config.getUndergroundCullingKeepDepth();

        if (mc.level != null) {
            ResourceKey<Level> dimension = mc.level.dimension();
            if (!dimension.equals(lastSurfaceCacheDimension)) {
                lastSurfaceCacheDimension = dimension;
                surfaceHeightCache.clear();
                coverHandler.clearCache();
            }
        }

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

        long genBefore = getCullingGeneration();
        updateSpaceRecognition(mc, currentBlockX, currentBlockY, currentBlockZ);
        // 要素集合(壁パネル/天井スライス)が変わったら、ワーカーの判定キャッシュを破棄して
        // 再構築後のメッシュが古い判定を拾わないようにする。
        if (getCullingGeneration() != genBefore) {
            cullingCache.clear();
        }
        // 屋内判定が変わったらキャッシュを破棄して、フェード/近接半透明化の切替を即座に反映する
        boolean disableIndoorFade = Config.isDisableFadeIndoors() && currentSpaceEnclosed;
        if (disableIndoorFade != cachedDisableIndoorFade) {
            cullingCache.clear();
            fadeCache.clear();
        }
        cachedDisableIndoorFade = disableIndoorFade;
        if (cachedCoverCullingActive && coverHandler.isReleasing()) {
            // 覆いのカリング開始時刻が時間で進むため、ワーカーの判定結果を毎tick作り直す。
            cullingCache.clear();
        }
        treeHandler.updateOcclusion(playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        long tEntity = System.nanoTime();
        updateEntityCulling(mc);
        PerfMonitor.ENTITY_CULL.add(System.nanoTime() - tEntity);
    }

    private void updateSpaceRecognition(Minecraft mc, int blockX, int blockY, int blockZ) {
        // 完成した probe をまず受理する (前回結果と入れ替わったタイミングで再構築が走る)。
        acceptProbeResult(mc);

        if (mc.level == null || mc.player == null) {
            currentSpaceEnclosed = false;
            return;
        }

        // 空間判定・樹木検出はプレイヤーが一定量移動したときだけ再実行する。
        // 屋内構造や樹木は数ブロックの移動では変わらないため、毎tickの全探索を避けられる。
        final BlockPos seed = mc.player.blockPosition();
        final boolean dimensionChanged = !mc.level.dimension().equals(lastSpaceDimension);
        boolean needReprobe;
        if (currentSpaceResult == null || lastSpaceSeed == null || dimensionChanged) {
            needReprobe = true;
        } else {
            needReprobe = seed.distManhattan(lastSpaceSeed) >= SPACE_REPROBE_MOVE_THRESHOLD;
        }
        if (!needReprobe) {
            return;
        }
        // 進行中の probe は受理後にゲート再判定で再依頼する。重ねて依頼しない。
        if (probeInFlight) {
            return;
        }
        // 直前に失敗した probe の連投防止。
        if (System.nanoTime() < probeRetryAfterNanos) {
            return;
        }

        lastSpaceSeed = seed.immutable();
        lastSpaceDimension = mc.level.dimension();
        if (dimensionChanged) {
            lastEnclosedSeed = null;
        }

        if (Config.isProtectNaturalTreeLogs()) {
            NaturalTreeDetector.scan(mc.level, blockX, blockY, blockZ, cachedCylinderRadiusHorizontal + 2);
            treeHandler.updateLogs();
        } else {
            NaturalTreeDetector.clearCache();
            treeHandler.clearCache();
        }

        if (submitProbe(mc, seed, probeEpoch)) {
            return;
        }

        // ワーカーが使えない場合のみ同期実行にフォールバックする。
        long tProbe = System.nanoTime();
        SpaceProbe.Result probed;
        try {
            probed = SpaceProbe.probe(mc.level, seed, spaceScratch);
        } catch (Throwable t) {
            logProbeFailure(t);
            return;
        }
        PerfMonitor.PROBE.add(System.nanoTime() - tProbe);
        applySpaceResult(mc, mc.level, seed, probed);
    }

    /**
     * probe をワーカースレッドへ依頼する。
     *
     * @return ワーカーに依頼できた場合 true。フォールバックで同期実行すべき false。
     */
    private boolean submitProbe(Minecraft mc, BlockPos seed, int epoch) {
        try {
            if (probeExecutor == null) {
                probeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                    Thread probeThread = new Thread(runnable, "TopDownView-SpaceProbe");
                    probeThread.setDaemon(true);
                    probeThread.setPriority(Thread.NORM_PRIORITY - 1);
                    return probeThread;
                });
            }
            final Level level = mc.level;
            final BlockPos fixedSeed = seed.immutable();
            probeInFlight = true;
            probeExecutor.execute(() -> {
                // Scratch は probe ごとに新規確保。受理時にメインスレッドへ所有権ごと渡すため、
                // 前回の BlockMap を引きずらない (BlockMap は probe 起点中心の領域で reset される)。
                RoomFloodFill.Scratch probeScratch = new RoomFloodFill.Scratch();
                runProbe(level, fixedSeed, probeScratch, epoch);
            });
            return true;
        } catch (Throwable t) {
            probeInFlight = false;
            LOGGER.warn("[TopDownView] space probe executor unavailable ({}), using sync fallback", t.toString());
            return false;
        }
    }

    /**
     * ワーカースレッド本体。例外でスロットが空のままになることを避けるため必ず結果を発行する。
     */
    private void runProbe(Level level, BlockPos seed, RoomFloodFill.Scratch probeScratch, int epoch) {
        SpaceProbe.Result result;
        long elapsedNanos;
        try {
            long tProbe = System.nanoTime();
            result = SpaceProbe.probe(level, seed, probeScratch, false);
            elapsedNanos = System.nanoTime() - tProbe;
        } catch (Throwable t) {
            result = null;
            elapsedNanos = 0L;
            logProbeFailure(t);
        }
        probeOutcome = new ProbeOutcome(epoch, level, seed, result, probeScratch, elapsedNanos);
    }

    /**
     * メインスレッドから完成 probe を受理する。世代とレベル一致だけを検証し、
     * 当初の受理ロジック (スティッキー/ハンドラ更新) は {@link #applySpaceResult} に委譲する。
     */
    private void acceptProbeResult(Minecraft mc) {
        ProbeOutcome outcome = probeOutcome;
        if (outcome == null) {
            return;
        }
        probeOutcome = null;
        probeInFlight = false;

        if (outcome.epoch() != probeEpoch) {
            return;
        }
        if (mc.level == null || mc.level != outcome.level()
                || !mc.level.dimension().equals(outcome.level().dimension())) {
            return;
        }
        if (outcome.result() == null) {
            // 失敗結果は一定時間捨てて再試行する。
            probeRetryAfterNanos = System.nanoTime() + PROBE_RETRY_NANOS;
            return;
        }

        PerfMonitor.PROBE.add(outcome.elapsedNanos());
        spaceScratch = outcome.scratch();
        applySpaceResult(mc, outcome.level(), outcome.seed(), outcome.result());
    }

    /**
     * probe 結果を確定させる (スティッピー屋内判定/天井スライス/はしご/階段/覆い)。
     * メインスレッドからのみ呼ぶこと ({@code mc} とハンドラ内部状態を更新する)。
     */
    private void applySpaceResult(Minecraft mc, Level level, BlockPos seed, SpaceProbe.Result probed) {
        // 段差・階段・開口部ではフラッドフィル結果が一瞬「屋外」になりカリングがチカチカする。
        // 直前まで屋内だった座標の近くならそのブレとして無視し、屋内状態を維持する。
        if (!probed.isEnclosed() && lastEnclosedSeed != null
                && seed.distManhattan(lastEnclosedSeed) <= ENCLOSED_STICKY_MOVE) {
            return;
        }

        currentSpaceResult = probed;
        currentSpaceEnclosed = probed.isEnclosed();
        lastEnclosedSeed = currentSpaceEnclosed ? seed.immutable() : null;

        // 屋内の天井スライスは通常カリングに追加する形で動かす。屋内外どちらでも通常カリング
        // (覆い/円柱/保護など) は適用し、天井スライスだけ保護の対象外。
        boolean elementActive = currentSpaceEnclosed && cachedIndoorCeilingEnabled;
        cachedIndoorElementActive = elementActive;
        cachedCoverCullingActive = cachedCullingMode != CullingConfig.CULLING_MODE_CYLINDER;

        RoomFloodFill.Result roomResult = currentSpaceResult.getRoomResult();
        RoomSegmentation.Room playerRoom = currentSpaceResult.getSegmentation().getPlayerRoom();

        long tCeiling = System.nanoTime();
        if (elementActive) {
            // 母集団はプレイヤーがいる部屋のセルに限り、立ち位置より下は集計側で除外する。
            // これで橋の下や地下道のような下の階が天井候補に混ざらない。
            LongSet floorCells = playerRoom != null ? playerRoom.getAirCells() : roomResult.getAirCells();
            ceilingSliceCuller.update(level, roomResult.getMinPos(), roomResult.getMaxPos(),
                    floorCells, resolveStandingY(seed));
        } else {
            ceilingSliceCuller.clearCache();
        }
        PerfMonitor.CEILING.add(System.nanoTime() - tCeiling);

        // 受理時点のプレイヤー位置で走査する (依頼時の座標は probe 遅延で既に古い可能性がある)。
        final int currentBlockX = (int) Math.floor(mc.player.getX());
        final int currentBlockY = (int) Math.floor(mc.player.getEyeY());
        final int currentBlockZ = (int) Math.floor(mc.player.getZ());

        long tLadder = System.nanoTime();
        ladderHandler.scan(level, currentBlockX, currentBlockZ, currentBlockY - 1);
        PerfMonitor.LADDER.add(System.nanoTime() - tLadder);

        long tStair = System.nanoTime();
        stairHandler.update(mc, currentBlockY, currentSpaceEnclosed, roomResult, spaceScratch.getBlockMap());
        PerfMonitor.STAIR.add(System.nanoTime() - tStair);

        if (ModState.STATUS.isEnabled() && cachedCoverCullingActive) {
            int feetY = (int) Math.floor(mc.player.getY());
            long tCover = System.nanoTime();
            coverHandler.update(level, currentBlockX, feetY, currentBlockZ, currentSpaceEnclosed,
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    (int) Math.floor(cameraY), Config.getCoverCullingRadius(),
                    Config.isCoverCullingViewshedEnabled());
            PerfMonitor.COVER.add(System.nanoTime() - tCover);
        } else {
            coverHandler.clearCache();
        }
    }

    /** probe ワーカー例外のログ (スパム防止で最初の1件のみ)。 */
    private void logProbeFailure(Throwable cause) {
        long now = System.nanoTime();
        if (now < probeErrorLogAfterNanos) {
            return;
        }
        probeErrorLogAfterNanos = now + 10_000_000_000L;
        LOGGER.error("[TopDownView] space probe failed: {}", cause.toString());
    }

    /**
     * プレイヤーの立ち位置の基準 Y（支えている地面の1つ上）を返す。
     *
     * <p>瞬間的な足元 Y はジャンプで上下するため、天井スライスの母集団の下限がぶれる。
     * 足元から下へ最初の固体ブロックを探すことで、ジャンプ中でも着地時の高さに固定する。
     */
    private int resolveStandingY(BlockPos seed) {
        var blockMap = spaceScratch.getBlockMap();
        int x = seed.getX();
        int z = seed.getZ();
        int from = seed.getY();
        for (int y = from; y >= from - STANDING_SCAN_DOWN; y--) {
            if (blockMap.isSolid(x, y, z)) {
                return y + 1;
            }
        }
        return from;
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
        // HangingEntity を対象にすることで MOD 製の壁掛け装飾（キャンバス等）もカリングする。
        // リードの結び目は装飾ではないため除外。
        if (entity instanceof LeashFenceKnotEntity) return false;
        return entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    public void reset() {
        clearCache();
        contextValid = false;
        lastSurfaceCacheDimension = null;
        playerX = playerY = playerZ = cameraX = cameraY = cameraZ = 0.0;
    }

    public int getCulledBlockCount() { return cullingCache.getCulledCount(); }
    public int getCacheSize() { return cullingCache.size(); }

    /** 覆いカリングが時間差で進行中か(進行中はチャンク再構築を強制する必要がある)。 */
    public boolean hasActiveCoverRelease() {
        return cachedCoverCullingActive && coverHandler.isReleasing();
    }

    /** 屋内の天井スライスカリングが有効か。 */
    public boolean isIndoorElementActive() {
        return cachedIndoorElementActive;
    }

    /** デバッグ用: 直近の天井スライス Y。無効なら {@link Integer#MIN_VALUE}。 */
    public int getCeilingSliceY() {
        return ceilingSliceCuller.getLastCeilingY();
    }

    /** デバッグ用: 直近の天井スライス集計列数。 */
    public int getCeilingSliceColumns() {
        return ceilingSliceCuller.getLastColumnCount();
    }

    /** デバッグ用: 処理量超過により天井スライスがクールダウン中か。 */
    public boolean isCeilingSliceCoolingDown() {
        return ceilingSliceCuller.isCoolingDown();
    }

    /**
     * カリング集合の世代番号。値が変わるとカリング結果が変わった可能性がある。
     * 屋内天井スライスは視点の回転や階の移動で変わるため、チャンク再構築のトリガに使う。
     */
    public long getCullingGeneration() {
        return ceilingSliceCuller.getGeneration();
    }

    /** 天井スライスの差分範囲を返す。空なら差分追跡できている集合の変化は無い。 */
    public BlockChangeBox getPendingElementChange() {
        pendingElementChange.reset();
        pendingElementChange.includeBox(ceilingSliceCuller.getPendingChange());
        return pendingElementChange;
    }

    /** 再構築を実際にスケジュールした後に呼ぶ。次回の差分を新しく蓄積し直す。 */
    public void clearPendingElementChange() {
        ceilingSliceCuller.clearPendingChange();
    }

    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ModState.STATUS.isMiningMode()) return 1.0f;
        long posLong = pos.asLong();
        // 天井スライスのブロックは透明(0)。それ以外は通常のフェード/半透明をそのまま適用する。
        if (cachedCoverCullingActive && coverHandler.isCoverCulled(pos)) return 0.0f;
        if (cachedIndoorElementActive && ceilingSliceCuller.isCeilingSliceBlock(posLong)) return 0.0f;
        Float cached = fadeCache.getFadeAlpha(posLong);
        if (cached != null) return cached;

        BlockState state = level.getBlockState(pos);
        float fadeAlpha = calculateFadeAlpha(pos, level, state, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);

        // 近接半透明化はカリング済み(フェード対象)のブロックだけに適用する。カリングされて
        // いないブロックは不透明のまま残すため、プレイヤー周囲を箱状に消さない。
        if (fadeAlpha < 1.0f && Config.isPlayerNearTranslucencyEnabled()
                && isPlayerNearBlock(pos, playerX, playerY, playerZ)
                && !isProtectedBlock(pos, state, playerY, level)
                && !isFastGraphicsLeaves(state)) {
            float nearAlpha = (float) Config.getPlayerNearTranslucencyAlpha();
            fadeCache.putFadeAlpha(posLong, nearAlpha);
            return nearAlpha;
        }

        // 屋内では境界フェードの半透明ゴーストを止める(カリング自体は calculateFadeAlpha 側で維持)
        if (!Config.isFadeEnabled() || cachedDisableIndoorFade) return 1.0f;
        fadeCache.putFadeAlpha(posLong, fadeAlpha);
        return fadeAlpha;
    }

    /**
     * FASTグラフィックの葉は不透明テクスチャで描かれるため、半透明ゴーストにできない。
     * {@code calculateFadeAlpha} と同じ判定を近接半透明化側でも使う。
     */
    private boolean isFastGraphicsLeaves(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES)
                && Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST;
    }

    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ModState.STATUS.isMiningMode() || level == null) return false;
        if (!Config.isFadeEnabled() && !Config.isPlayerNearTranslucencyEnabled() && !Config.isStaircaseOccludeEnabled() 
            && !Config.isLadderOccludeEnabled() && !Config.isTreeOccludeEnabled()) return false;
        if (cachedIndoorElementActive && ceilingSliceCuller.isCeilingSliceBlock(pos.asLong())) return false;
        if (cachedCoverCullingActive && coverHandler.isCoverCulled(pos)) return false;
        float alpha = getFadeAlpha(pos, level);
        return alpha < 1.0f && alpha > cachedFadeBlockHitThreshold;
    }

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocks(BlockGetter level) {
        long tCollect = System.nanoTime();
        it.unimi.dsi.fastutil.longs.Long2FloatMap result = collectFadeBlocksImpl(level);
        PerfMonitor.FADE_COLLECT.add(System.nanoTime() - tCollect);
        return result;
    }

    private it.unimi.dsi.fastutil.longs.Long2FloatMap collectFadeBlocksImpl(BlockGetter level) {
        boolean fadeEnabled = Config.isFadeEnabled() && !cachedDisableIndoorFade;
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

        // 走査中不変な設定・オプションはループ外で1回だけ評価（per-block再評価の回避）
        boolean fadeEnabled = Config.isFadeEnabled() && !cachedDisableIndoorFade;
        boolean nearTranslucencyEnabled = Config.isPlayerNearTranslucencyEnabled();
        boolean ladderOcclude = Config.isLadderOccludeEnabled();
        boolean stairOcclude = Config.isStaircaseExclusionEnabled();
        boolean treeOcclude = Config.isTreeOccludeEnabled();
        // FASTグラフィックの葉は半透明にすると見た目が変わるため、フェード集合へ入れず消す。
        boolean fastGraphics = Minecraft.getInstance().options.graphicsMode().get()
                == net.minecraft.client.GraphicsStatus.FAST;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);
                    boolean isNearTarget = nearTranslucencyEnabled && isPlayerNearBlock(mutablePos, pX, pY, pZ);

                    double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(x + 0.5, y + 0.5, z + 0.5, pX, pY, pZ, cX, cY, cZ);
                    double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(mutablePos, pX, pY, pZ, cX, cZ);

                    float cylinderAlpha;
                    if (normalizedDistSq < 0 || normalizedDistSq > 1.0) cylinderAlpha = 1.0f;
                    else if (normalizedDistSq <= cachedFadeStart) cylinderAlpha = (float) cachedFadeNearAlpha;
                    else {
                        double t = (normalizedDistSq - cachedFadeStart) / (1.0 - cachedFadeStart);
                        cylinderAlpha = (float) (cachedFadeNearAlpha + t * (1.0 - cachedFadeNearAlpha));
                    }
                    float tempAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);

                    long posLong = mutablePos.asLong();
                    boolean isTarget = false;
                    float finalAlpha = tempAlpha;
                    boolean cylinderCulled = tempAlpha < 1.0f;

                    if (isNearTarget && cylinderCulled) {
                        // 近接半透明化はカリング済みのブロックだけを対象にする
                        isTarget = true;
                        finalAlpha = (float) Config.getPlayerNearTranslucencyAlpha();
                    } else if (cylinderCulled && tempAlpha > 0.0f && fadeEnabled) {
                        isTarget = true;
                        fadeTransitionController.onBlockFaded(posLong);
                    } else if (fadeEnabled && tempAlpha >= 1.0f
                            && fadeTransitionController.isHandoffActive(posLong)) {
                        isTarget = true;
                        finalAlpha = 1.0f;
                        fadeTransitionController.activateHandoff(posLong);
                    }

                    if (!isTarget) continue;

                    if (cachedViewWedgeProtection
                            && normalizedDistSq >= 0.0 && normalizedDistSq <= 1.0
                            && !OcclusionCalculator.isWithinViewWedge(
                                    mutablePos.getX() + 0.5, mutablePos.getZ() + 0.5,
                                    pX, pZ, viewDirX, viewDirZ, cachedViewWedgeCos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    if (isProtectedBlock(mutablePos, state, pY, level)) continue;

                    if (cachedIndoorElementActive && ceilingSliceCuller.isCeilingSliceBlock(posLong)) continue;
                    // 覆いブロックは覆い側の時間差カリングに任せる(円柱フェードと二重に扱わない)
                    if (cachedCoverCullingActive && coverHandler.isCoverBlock(mutablePos)) continue;
                    if (ladderOcclude && ladderHandler.isProtectedPosition(mutablePos)) continue;
                    if (stairOcclude && stairHandler.isExcludedStairBlock(mutablePos)) continue;
                    if (treeOcclude && treeHandler.isOccludedLog(posLong, mutablePos)) continue;

                    if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) && fastGraphics) {
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