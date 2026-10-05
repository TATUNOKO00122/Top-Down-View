package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.config.CullingConfig;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.client.MouseRaycast;
import com.topdownview.client.TranslucentBlockRenderer;
import com.topdownview.compat.VerticalUnitHelper;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.SurfaceHeightCache;
import com.topdownview.culling.geometry.BlockChangeBox;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.BlockMap;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.ModState;
import com.topdownview.culling.ladder.LadderHelper;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.topdownview.util.PerfMonitor;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.GraphicsStatus;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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
    /** ピラミッド境界ヒステリシスの解放しきい値。diff がこの値より下まで下がると解放する。 */
    private static final double PYRAMID_STICKY_RELEASE = 1.0;
    /** 半空間クリップ境界のヒステリシス幅(dm)。dot の絶対値がこの値を超えるまで側を反転しない。 */
    private static final double CLIP_STICKY_MARGIN = 0.75;
    /** フレーム同期の方向改訂しきい値(≈3°)。これ未満の回転では判定・再構築を起こさない。 */
    private static final double CONTEXT_DIR_EPS_SQ = 0.05 * 0.05;
    /** 円柱出口ヒステリシスの余白(ブロック)。境界がこれだけ外へ押し戻されるまで内側扱いを続ける。 */
    private static final double CYLINDER_EXIT_STICKY_BLOCKS = 1.5;
    private static final double ENTITY_PROTECTION_RADIUS_SQ = 4.0;
    private static final int CACHE_CLEAR_MOVE_THRESHOLD = 1;
    /** 遷移フェード走査集合の上限。各ハンドラの収集ライムと共有する唯一の定義。 */
    public static final int MAX_FADE_POSITIONS = 4000;
    /** カメラ埋没と判定するために固体中を走破する距離(ブロック)。 */
    private static final double BURIED_ENTER_DISTANCE = 10.0;
    /** 埋没判定を解除する距離(ブロック)。走破距離がここまで減ったら解除する(往復防止)。 */
    private static final double BURIED_EXIT_DISTANCE = 5.0;

    private double playerX;
    private double playerY;
    private double playerZ;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private boolean contextValid = false;

    /** update() で確定したプレイヤーのブロック座標。ブロック毎の floor 再計算を避ける。 */
    private int cachedPlayerBlockX = Integer.MIN_VALUE;
    private int cachedPlayerBlockZ = Integer.MIN_VALUE;
    private int cachedPlayerFloorY = Integer.MIN_VALUE;
    private int cachedPlayerFeetY = Integer.MIN_VALUE;

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
    /** 走査集合の凍結条件に含める、覆い集合/天井スライスの直近世代。 */
    private long lastFadeCoverGen = Long.MIN_VALUE;
    private long lastFadeSliceGen = Long.MIN_VALUE;
    private long lastFadeWallGen = Long.MIN_VALUE;
    private boolean cacheClearedOnDisabled = false;
    private boolean spaceClearedOnDisabled = false;

    private boolean currentSpaceEnclosed = false;
    private SpaceProbe.Result currentSpaceResult = null;
    // ドールハウス表示用: 屋内ヒステリシスを通さない生の検出結果。カリングの遅延に追従させない。
    private volatile SpaceProbe.Result rawSpaceResult = null;
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
    private static final int ENCLOSED_STICKY_MOVE = 1;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final SurfaceHeightCache surfaceHeightCache = new SurfaceHeightCache();
    /** 遷移フェードの対象となる「今カリングされている位置」の集合。走査ごとに作り直す。 */
    private final LongOpenHashSet fadePositions = new LongOpenHashSet(500);

    /**
     * ピラミッド境界のヒステリシス帳簿。採用=diff≥0、解放=diff<−1ブロック。
     * 唯一の書き手はフェード走査(メインスレッド)。ワーカーは volatile スナップショットを読む。
     */
    private final Long2ByteOpenHashMap pyramidSticky = new Long2ByteOpenHashMap();
    private volatile LongOpenHashSet pyramidStickyView = new LongOpenHashSet();
    private boolean pyramidStickyDirty = false;

    /**
     * 半空間クリップ(dot=0 の境界線)のヒステリシス帳簿。近接天頂カメラでは歩行のたびに
     * 量子化された視線方向が揺れて境界線が再方向づけされ、線上のブロックが保護/カリングを
     * 毎歩で反転する。排 dob が ±幅を超えたときだけ側を確定する。
     */
    private final Long2ByteOpenHashMap clipSticky = new Long2ByteOpenHashMap();
    private volatile LongOpenHashSet clipStickyView = new LongOpenHashSet();
    private boolean clipStickyDirty = false;

    /**
     * 円柱境界の出口ヒステリシス帳簿。走査(メインスレッド)が現在のチューブ幾何で更新し、
     * 境界が余白ぶん押し戻されるまで「内側だった」状態を維持する。ワーカーはスナップショット参照。
     */
    private final Long2ByteOpenHashMap cylinderSticky = new Long2ByteOpenHashMap();
    private volatile LongOpenHashSet cylinderStickyView = new LongOpenHashSet();
    private boolean cylinderStickyDirty = false;

    /** 円柱出口ヒステリシスの判定限界(norm)。半径に応じて余白ブロックを変換する。 */
    private double cylinderExitStickyLimit() {
        int radiusH = Math.max(1, Config.getCylinderRadiusHorizontal());
        return 1.0 + CYLINDER_EXIT_STICKY_BLOCKS / radiusH;
    }
    private final MutableBlockPos entityGroundedPos = new MutableBlockPos();
    /** 下支え判定用。isBlockCulled はワーカースレッドからも呼ばれるため ThreadLocal で共有回避。 */
    private static final ThreadLocal<MutableBlockPos> SUPPORT_CHECK_POS =
            ThreadLocal.withInitial(MutableBlockPos::new);
    /** 視線判定用。同じくワーカースレッドから呼ばれるため作業座標は ThreadLocal で確保する。 */
    private static final ThreadLocal<MutableBlockPos> LOS_CHECK_POS =
            ThreadLocal.withInitial(MutableBlockPos::new);

    private final StairCullingHandler stairHandler = new StairCullingHandler();
    private final TreeCullingHandler treeHandler = new TreeCullingHandler();
    private final CeilingSliceCuller ceilingSliceCuller = new CeilingSliceCuller();
    private final CoverCullingHandler coverHandler = new CoverCullingHandler();
    private final ConnectedWallHandler connectedWallHandler = new ConnectedWallHandler();
    /**
     * 復元が確定した(=メッシュに戻る必要がある)位置の開示ボックス。
     * 復元位置はプレイヤー↔カメラボックスの外にいることがある(覆いは覆い半径+余白の外で
     * 初めてドロップされる)ため、ボックスだけに頼ると再構築されずに消えたままになる。
     */
    private final BlockChangeBox revealChange = new BlockChangeBox();

    private final FadeTransitionController fadeTransitionController =
            new FadeTransitionController(posLong -> revealChange.includeCell(posLong));

    private int cachedCylinderRadiusHorizontal;
    private int cachedCylinderRadiusVertical;
    /** Newモード時にカメラ側クリップを行うか。奥の壁を保護する。 */
    private boolean cachedCameraSideClip;
    /** カメラ側クリップを扇形(旧方式)にするか。false は角度制限なしの半空間。 */
    private boolean cachedCameraSideClipWedge;
    private double cachedViewWedgeCos;
    /** 遷移フェード(ゴースト表示)が有効な期間を通して保持するフラグ。切替でトラッカーをリセットする。 */
    private boolean cachedFadeTransitionsActive = false;
    /** 天井スライス等の要素集合の差分を union した再構築範囲。 */
    private final BlockChangeBox pendingElementChange = new BlockChangeBox();
    private boolean cachedCoverCullingActive;
    private boolean cachedDisableIndoorFade;
    /** カメラが地形に埋没している(固体中を一定距離走破した)と確定した状態。 */
    private boolean cachedCameraBuried;
    /** カメラが固体中を連続して走破した距離(ブロック)。ヒステリシス用。 */
    private double cameraBuriedTravel;
    private int lastBuriedCamX = Integer.MIN_VALUE;
    private int lastBuriedCamY = Integer.MIN_VALUE;
    private int lastBuriedCamZ = Integer.MIN_VALUE;
    /** 埋没判定用のカメラブロック位置(再利用)。 */
    private final MutableBlockPos cameraProbe = new MutableBlockPos();
    private int cachedCullingMode;
    private boolean cachedIndoorElementActive;
    private boolean cachedIndoorCeilingEnabled;
    private boolean cachedProtectInteractablesOutdoors = true;
    private double viewDirX = 0.0;
    private double viewDirZ = 1.0;

    /**
     * カメラ由来のカリング文脈(カメラブロック・視線方向)の改訂番号。値が変わると再構築が必要。
     * クライアントtick(20Hz)では回転追従が1tick遅れるため、フレーム毎の
     * {@link #syncCameraContext()} から進める。CullingManager の再構築トリガに使う。
     */
    private long viewContextRevision = 0L;
    /** フレーム毎のカメラ文脈変化検出用。tick 側の lastCameraBlock とは独立に持つ。 */
    private int lastSyncCamBlockX = Integer.MIN_VALUE;
    private int lastSyncCamBlockY = Integer.MIN_VALUE;
    private int lastSyncCamBlockZ = Integer.MIN_VALUE;
    private double lastSyncViewDirX = Double.NaN;
    private double lastSyncViewDirZ = Double.NaN;

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
        fadePositions.clear();
        surfaceHeightCache.clear();
        stairHandler.clearCache();
        treeHandler.clearCache();
        ceilingSliceCuller.clearCache();
        coverHandler.clearCache();
        connectedWallHandler.clearCache();
        fadeTransitionController.clearCache();
        
        currentSpaceEnclosed = false;
        cachedDisableIndoorFade = false;
        cachedCameraBuried = false;
        cameraBuriedTravel = 0.0;
        lastBuriedCamX = Integer.MIN_VALUE;
        lastBuriedCamY = Integer.MIN_VALUE;
        lastBuriedCamZ = Integer.MIN_VALUE;
        cachedFadeTransitionsActive = false;
        cachedIndoorElementActive = false;
        cachedCoverCullingActive = false;
        currentSpaceResult = null;
        rawSpaceResult = null;
        spaceScratch.clear();
        lastSpaceSeed = null;
        lastSpaceDimension = null;
        lastEnclosedSeed = null;
        probeEpoch++;
        probeOutcome = null;
        probeInFlight = false;
        probeRetryAfterNanos = 0L;
        pendingElementChange.reset();
        // 次元/ワールドをまたいだ差分を持ち越さない。revealChange を残すと、settle 後の
        // 初回再構築で旧次元座標と新次元ボックスが union され、2次元にまたがる巨大ボックス
        // (実測 sections=111928)になる。
        revealChange.reset();
        ceilingSliceCuller.clearPendingChange();
        pyramidSticky.clear();
        pyramidStickyView = new LongOpenHashSet();
        pyramidStickyDirty = false;
        clipSticky.clear();
        clipStickyView = new LongOpenHashSet();
        clipStickyDirty = false;
        cylinderSticky.clear();
        cylinderStickyView = new LongOpenHashSet();
        cylinderStickyDirty = false;
        LadderHelper.clearCache();
        NaturalTreeDetector.clearCache();
        resetLastBlockCoords();
    }

    private void resetLastBlockCoords() {
        lastFadePBlockX = Integer.MIN_VALUE;
        lastFadePBlockY = Integer.MIN_VALUE;
        lastFadePBlockZ = Integer.MIN_VALUE;
        lastFadeCBlockX = Integer.MIN_VALUE;
        lastFadeCBlockY = Integer.MIN_VALUE;
        lastFadeCBlockZ = Integer.MIN_VALUE;
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
        cachedPlayerBlockX = Integer.MIN_VALUE;
        cachedPlayerBlockZ = Integer.MIN_VALUE;
        cachedPlayerFloorY = Integer.MIN_VALUE;
        cachedPlayerFeetY = Integer.MIN_VALUE;
        lastCameraBlockX = Integer.MIN_VALUE;
        lastCameraBlockY = Integer.MIN_VALUE;
        lastCameraBlockZ = Integer.MIN_VALUE;
        lastSyncCamBlockX = Integer.MIN_VALUE;
        lastSyncCamBlockY = Integer.MIN_VALUE;
        lastSyncCamBlockZ = Integer.MIN_VALUE;
        lastSyncViewDirX = Double.NaN;
        lastSyncViewDirZ = Double.NaN;
        viewContextRevision = 0L;
    }

    /**
     * カメラ由来のカリング文脈(カメラ座標・円柱軸・視線方向)を更新する。
     *
     * <p>カメラはレンダーフレーム単位で滑らかに動くが {@link #update()} はクライアントtick(20Hz)
     * でしか走らないため、回転時に文脈が1tick分遅れる。さらにピッチが真上に近いとカメラブロックが
     * 変わらず yaw 方向だけが回るため、tick のブロック変化検出ではキャッシュが破棄されず
     * カリングが全く追従しない。フレーム毎にここで文脈を確定し、変化があれば判定キャッシュを
     * 破棄して改訂番号を進める(再構築トリガは CullingManager 側)。
     *
     * <p>プレイヤーの量子化座標は tick 側の {@link #update()} が確定した値を使う。メインスレッド専用。
     */
    public void syncCameraContext() {
        if (!contextValid) {
            return;
        }
        int camBlockX = (int) Math.floor(ModState.CAMERA.getCameraX());
        int camBlockY = (int) Math.floor(ModState.CAMERA.getCameraY());
        int camBlockZ = (int) Math.floor(ModState.CAMERA.getCameraZ());
        cameraX = camBlockX + 0.5;
        cameraY = camBlockY + 0.5;
        cameraZ = camBlockZ + 0.5;

        CylinderCalculator.updateCache(quantizeYaw(ModState.CAMERA.getYaw()), Config.getCylinderForwardShift(),
                playerX, playerY, playerZ, cameraX, cameraY, cameraZ);

        double wedgeDirX = playerX - cameraX;
        double wedgeDirZ = playerZ - cameraZ;
        double wedgeDirLen = Math.sqrt(wedgeDirX * wedgeDirX + wedgeDirZ * wedgeDirZ);
        if (wedgeDirLen < 1.0E-4) {
            // カメラが真上付近: yaw から前方向を求める(CylinderCalculator と同じ規約)
            double yawRad = Math.toRadians(quantizeYaw(ModState.CAMERA.getYaw()));
            viewDirX = -Math.sin(yawRad);
            viewDirZ = Math.cos(yawRad);
        } else {
            viewDirX = wedgeDirX / wedgeDirLen;
            viewDirZ = wedgeDirZ / wedgeDirLen;
        }

        boolean changed = camBlockX != lastSyncCamBlockX || camBlockY != lastSyncCamBlockY
                || camBlockZ != lastSyncCamBlockZ;
        if (!changed) {
            double dirDeltaX = viewDirX - lastSyncViewDirX;
            double dirDeltaZ = viewDirZ - lastSyncViewDirZ;
            changed = dirDeltaX * dirDeltaX + dirDeltaZ * dirDeltaZ > CONTEXT_DIR_EPS_SQ;
        }
        if (changed) {
            cullingCache.clear();
            lastSyncCamBlockX = camBlockX;
            lastSyncCamBlockY = camBlockY;
            lastSyncCamBlockZ = camBlockZ;
            lastSyncViewDirX = viewDirX;
            lastSyncViewDirZ = viewDirZ;
            viewContextRevision++;
        }
    }

    /** カメラ由来の文脈の改訂番号。値が変わると再構築が必要(CullingManager が参照)。 */
    public long getViewContextRevision() {
        return viewContextRevision;
    }

    /**
     * yaw を15度刻みに量子化する。円柱軸の前シフト端点(shift=既定1ブロック)は生のyawで
     * カメラの滑らか追従のたびに連続的に滑り、チューブの端/径向境界がフレームごとに
     * 積まれて移動し、判定がカリング/復元を連続で往復する。量子化で端点の移動を段階化し
     * (1段階あたり約0.26ブロック)、連続としての flip を解消する。自動追従yawでも効く。
     */
    private static double quantizeYaw(double yaw) {
        return Math.round(yaw / 15.0) * 15.0;
    }

    /** カリング文脈(プレイヤー/カメラ座標)が確定しているか。フレーム毎フックの前提条件。 */
    public boolean isContextValid() {
        return contextValid;
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return isBlockCulled(pos, mc.level);
    }

    /**
     * メッシュ構築専用のカリング判定。判定本体に加えて、復元フラッシュ中のメッシュ専用ホールド
     * (穴を保ってゴーストのフェードインを見せる)を尊重する。レイキャスト・エンティティ・空間
     * 走査はこのホールドを見ないため、ゲームプレイの判定は {@link #isBlockCulled} のまま不変。
     */
    public boolean isBlockCulledForMesh(BlockPos pos, BlockGetter level) {
        if (isBlockCulled(pos, level)) {
            return true;
        }
        if (!fadeTransitionController.isMeshHoldActive(pos.asLong())) {
            return false;
        }
        // ゴーストはプレイヤーから一定距離以内しか描かない。それより遠くまでメッシュを保留すると、
        // 覆うゴーストの無い穴が残り、視点移動で穴の境界が掃引して波状に見える。範囲外は保留しない。
        double ghostDistance = TranslucentBlockRenderer.GHOST_RENDER_DISTANCE;
        double dx = pos.getX() + 0.5 - playerX;
        double dy = pos.getY() + 0.5 - playerY;
        double dz = pos.getZ() + 0.5 - playerZ;
        return dx * dx + dy * dy + dz * dz <= ghostDistance * ghostDistance;
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            if (!cacheClearedOnDisabled) {
                cullingCache.clear();
                fadePositions.clear();
                LadderHelper.clearCache();
                NaturalTreeDetector.clearCache();
                cacheClearedOnDisabled = true;
            }
            return false;
        }
        cacheClearedOnDisabled = false;
        if (level == null) return false;

        long posLong = pos.asLong();
        if (Config.isPerformanceMonitorEnabled()) {
            PerfMonitor.recordBlockCullSample(posLong);
        }
        byte cached = cullingCache.get(posLong);
        if (cached != CullingCacheManager.UNKNOWN) return cached == 1;

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

        int playerBlockX = cachedPlayerBlockX;
        int playerBlockZ = cachedPlayerBlockZ;
        int playerFeetY = cachedPlayerFeetY;
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

        // 雪の層・カーペット・植物など、下の支えが消えると宙に浮く薄い面ブロックは
        // 支え側のカリングに追従させて消す。保護より先に判定して、装飾の保護で残らないようにする。
        if (isRestingOnCulledBlock(pos, state, level)) {
            cullingCache.put(posLong, true);
            return true;
        }

        // 検出した階段は保護する。歩行中の階段が消えると足場が見えなくなる。
        if (Config.isStaircaseExclusionEnabled() && stairHandler.isProtectedStairBlock(pos)) {
            cullingCache.put(posLong, false);
            return false;
        }

        if (isProtectedBlock(pos, state, pY, level)) {
            cullingCache.put(posLong, false);
            return false;
        }

        if (cachedCoverCullingActive && coverHandler.isCoverCulled(pos)) {
            cullingCache.put(posLong, true);
            return true;
        }

        if (connectedWallHandler.isConnectedCulled(posLong)) {
            cullingCache.put(posLong, true);
            return true;
        }

        float alpha = calculateFadeAlpha(pos, level, state, pX, pY, pZ, cX, cZ);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(posLong, isCulled);
        return isCulled;
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

    /**
     * 円柱フェードの判定。円柱内(カメラ〜プレイヤー)はカリング対象(0.0)、
     * それ以外は不透明(1.0)。奥の半空間・ピラミッド保護で残すブロックは 1.0 以上になる。
     */
    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (normalizedDistSq < 0.0) {
            return 1.0f;
        }
        if (normalizedDistSq > 1.0) {
            if (!(normalizedDistSq <= cylinderExitStickyLimit()
                    && cylinderStickyView.contains(pos.asLong()))) {
                return 1.0f;
            }
            // 円柱出口ヒステリシス: 直近まで円柱内だった位置は、境界が余白ぶん回復するまで
            // チューブ内扱いで判定を通す。カメラ回転のフレーム同期でチューブがサブブロック
            // 滑っても、境界ブロックのカリング/復元が毎回反転しない。
        }

        // 円柱内でもプレイヤーより奥のブロックは保護する。扇形(旧方式)では角度外も保護し、
        // 半空間では角度制限なしでカメラ側をカリングして左右の視野を確保する。
        if (cachedCameraSideClip) {
            if (cachedCameraSideClipWedge) {
                if (!OcclusionCalculator.isWithinViewWedge(
                        pos.getX() + 0.5, pos.getZ() + 0.5,
                        pX, pZ, viewDirX, viewDirZ, cachedViewWedgeCos)) {
                    return 1.0f;
                }
            } else if (clipStickyView.contains(pos.asLong())
                    || OcclusionCalculator.isBeyondPlayerHorizontally(
                    pos.getX() + 0.5, pos.getZ() + 0.5,
                    pX, pZ, viewDirX, viewDirZ)) {
                return 1.0f;
            }
        }

        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                pos, pX, pY, pZ, cX, cZ);
        if (pyramidFactor < 1.0f
                && pyramidStickyView.contains(pos.asLong())
                && PyramidProtectionCalc.calculateBoundaryDiff(pos, pX, pY, pZ, cX, cZ) >= -PYRAMID_STICKY_RELEASE) {
            // 境界ヒステリシス: 最近保護側だったブロックは、傾斜がブロック1つ以上下がるまで
            // 保護を維持する。プレイヤーの1歩で近接リングの diff が ±1 動くため、単純な
            // 0 しきい値だと境界ブロックがカリング/保護を往復し(消失→復元→消失…)、
            // 復元ゴーストの単発点滅になる。
            pyramidFactor = 1.0;
        }
        float finalAlpha = (float) pyramidFactor;
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

        int playerFeetY = cachedPlayerFeetY;

        if (state.getBlock() instanceof LadderBlock) {
            if (LadderHelper.isLadderInLongChain(pos, level)) {
                int chainBottomY = LadderHelper.getChainBottomY(pos, level);
                if (chainBottomY >= playerFeetY && chainBottomY <= playerFeetY + 1) {
                    return true;
                }
            }
        }

        if (LadderHelper.isBlockBehindLadderChain(pos, level, playerFeetY)) {
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
            if (currentSpaceEnclosed || cachedProtectInteractablesOutdoors) {
                int protectY = currentSpaceEnclosed ? playerFeetY + 3 : playerFeetY + 1;
                // 視線が通っていれば階違いでも残す。Yバンド制限のみを上書きし、屋外設定などのゲートは維持する。
                if (blockY <= protectY || hasClearLineOfSight(level, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * プレイヤー目線（量子化したアイブロック中心）から対象ブロック中心まで、衝突形状を持つ
     * 遮蔽物が無いかを判定する。カリング対象のインタラクションブロックを「見えていれば残す」
     * ために使い、Yバンド制限を上書きする。
     *
     * <p>isBlockCulled は Embeddium のチャンク構築ワーカーからも呼ばれるため、{@link BlockGetter}
     * の読み取りだけで完結させ、作業用座標は ThreadLocal で確保する。
     */
    private boolean hasClearLineOfSight(BlockGetter level, BlockPos target) {
        double startX = playerX;
        double startY = playerY;
        double startZ = playerZ;
        double dirX = target.getX() + 0.5 - startX;
        double dirY = target.getY() + 0.5 - startY;
        double dirZ = target.getZ() + 0.5 - startZ;

        int x = (int) Math.floor(startX);
        int y = (int) Math.floor(startY);
        int z = (int) Math.floor(startZ);
        int targetX = target.getX();
        int targetY = target.getY();
        int targetZ = target.getZ();
        if (x == targetX && y == targetY && z == targetZ) {
            return true;
        }

        int stepX = Double.compare(dirX, 0);
        int stepY = Double.compare(dirY, 0);
        int stepZ = Double.compare(dirZ, 0);
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dirX) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dirY) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dirZ) : Double.MAX_VALUE;
        double tMaxX = stepX > 0 ? ((x + 1) - startX) * tDeltaX
                : stepX < 0 ? (startX - x) * tDeltaX : Double.MAX_VALUE;
        double tMaxY = stepY > 0 ? ((y + 1) - startY) * tDeltaY
                : stepY < 0 ? (startY - y) * tDeltaY : Double.MAX_VALUE;
        double tMaxZ = stepZ > 0 ? ((z + 1) - startZ) * tDeltaZ
                : stepZ < 0 ? (startZ - z) * tDeltaZ : Double.MAX_VALUE;

        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        int maxSteps = (int) Math.min(length * 3 + 8, 1024);
        MutableBlockPos check = LOS_CHECK_POS.get();
        for (int i = 0; i < maxSteps; i++) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }

            if (x == targetX && y == targetY && z == targetZ) {
                return true;
            }
            check.set(x, y, z);
            BlockState blocking = level.getBlockState(check);
            if (blocking.isAir() || blocking.is(Blocks.BARRIER)) {
                continue;
            }
            if (!blocking.getCollisionShape(level, check).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    /**
     * 薄い面ブロック（雪の層・カーペット・植物・松明など）の下の支えがカリング済みかを判定する。
     *
     * <p>支えが消えると宙に浮いて見えるため、支え側と同じタイミングで消す。フルブロックは
     * 上方へ連鎖させる（建物ごと消す）と過剰カリングになるため対象外。トラップドアは
     * 専用ハンドラの歩行判定を優先して除外する。
     */
    private boolean isRestingOnCulledBlock(BlockPos pos, BlockState state, BlockGetter level) {
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof TrapDoorBlock) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        if (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 1.0) {
            return false;
        }
        // チェスト・バレル等は高さ14/16で「薄い面」に誤判定されるが装飾ではなく操作対象。
        // 支えが消えても連鎖させず、後段の isProtectedBlock に保護判定を委ねる。
        if (InteractableBlocks.isInteractable(state, level, pos)) {
            return false;
        }
        MutableBlockPos below = SUPPORT_CHECK_POS.get();
        below.set(pos.getX(), pos.getY() - 1, pos.getZ());
        return isBlockCulled(below, level);
    }

    public void update() {
        // カリングが無効でも、ドールハウス表示が有効なら空間判定(flood/segment)だけは走らせる。
        final boolean cullingEnabled = ModState.STATUS.isCullingEnabled();
        if (!ModState.STATUS.isEnabled() || (!cullingEnabled && !Config.isDollhouseEnabled())) {
            if (!spaceClearedOnDisabled) {
                clearCache();
                spaceClearedOnDisabled = true;
            }
            return;
        }
        spaceClearedOnDisabled = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            contextValid = false;
            return;
        }

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getEyeY();
        double eyeZ = mc.player.getZ();

        int currentBlockX = (int) Math.floor(eyeX);
        int currentBlockY = (int) Math.floor(eyeY);
        int currentBlockZ = (int) Math.floor(eyeZ);

        // プレイヤーのブロック座標は update() で1度だけ floor し、ブロック毎の再計算を避ける。
        // playerX/Y/Z は floor+0.5 で表すため、floor(playerX)=currentBlockX が成り立つ。
        cachedPlayerBlockX = currentBlockX;
        cachedPlayerBlockZ = currentBlockZ;
        cachedPlayerFloorY = currentBlockY;
        cachedPlayerFeetY = currentBlockY - 1;

        if (!com.topdownview.state.CameraState.isPositionValid(ModState.CAMERA.getCameraPosition())) {
            playerX = currentBlockX + 0.5;
            playerY = currentBlockY + 0.5;
            playerZ = currentBlockZ + 0.5;
            contextValid = false;
            return;
        }

        // カリング座標はブロック中心に量子化する。カリング結果キャッシュは「カメラ/プレイヤーの
        // ブロックが変わったら破棄」で、ブロック内では判定が一定である前提のため、生座標だと
        // 1tickごとに文脈が変わってキャッシュが古くなり、範囲内なのに未カリング/誤復元が起きる。
        playerX = currentBlockX + 0.5;
        playerY = currentBlockY + 0.5;
        playerZ = currentBlockZ + 0.5;
        contextValid = true;
        // カメラ座標・円柱軸・視線方向はフレーム毎の syncCameraContext() と同じ経路で更新する。
        syncCameraContext();

        cachedCylinderRadiusHorizontal = Config.getCylinderRadiusHorizontal();
        cachedCylinderRadiusVertical = Config.getCylinderRadiusVertical();
        cachedCullingMode = Config.getCullingMode();
        cachedIndoorCeilingEnabled = Config.isIndoorCeilingCullingEnabled();
        cachedProtectInteractablesOutdoors = Config.isProtectInteractablesOutdoors();
        cachedCameraSideClip = cachedCullingMode == CullingConfig.CULLING_MODE_COVER_CORRIDOR;
        cachedCameraSideClipWedge = Config.isCameraSideClipWedge();
        cachedViewWedgeCos = Math.cos(Math.toRadians(Config.getViewWedgeHalfAngle()));

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

        int currentCamBlockX = (int) Math.floor(ModState.CAMERA.getCameraX());
        int currentCamBlockY = (int) Math.floor(ModState.CAMERA.getCameraY());
        int currentCamBlockZ = (int) Math.floor(ModState.CAMERA.getCameraZ());

        updateCameraBuried(mc, currentCamBlockX, currentCamBlockY, currentCamBlockZ);

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
            fadePositions.clear();
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
        // 要素集合(天井スライス等)が変わったら、ワーカーの判定キャッシュを破棄して
        // 再構築後のメッシュが古い判定を拾わないようにする。
        if (getCullingGeneration() != genBefore) {
            cullingCache.clear();
        }
        if (!cullingEnabled) {
            // ドールハウス表示専用: カリング固有の処理(フェード/エンティティカリング等)は走らせない。
            return;
        }
        // 屋内判定が変わったらキャッシュを破棄して、フェードの切替を即座に反映する
        boolean disableIndoorFade = Config.isDisableFadeIndoors() && currentSpaceEnclosed;
        if (disableIndoorFade != cachedDisableIndoorFade) {
            cullingCache.clear();
            fadePositions.clear();
        }
        cachedDisableIndoorFade = disableIndoorFade;
        // フラッシュ/復元どちらの抑制エントリも期限切れを掃除する(ホールド方式は廃止)。
        fadeTransitionController.tick();
        // メッシュ専用ホールドの変更をワーカー読み用スナップショットへ反映(再構築より前に)。
        fadeTransitionController.publishMeshHoldView();
        // 覆いの距離保持を毎tick判定する。走査(3ブロック間隔)を待つと、半径+余白を越えた
        // 覆いが余白ぶん遠くまで残り「離れても復元されない」遅延になる。走査しきい値は不変。
        if (cachedCoverCullingActive) {
            coverHandler.updateRetention(cachedPlayerBlockX, cachedPlayerBlockZ);
        }
        // 連鎖の距離保持も毎tick判定する(離脱の応答性)。集合の再計算はプローブ時のみ。
        if (Config.isConnectedWallCullingEnabled()) {
            connectedWallHandler.updateRetention(cachedPlayerBlockX, cachedPlayerBlockZ);
        }
        // 集合の離脱(覆い/スライス/連鎖)の消費は毎tick行う。プローブ時のみだと、毎tickの
        // 保持が解放した位置の開示(メッシュ復帰)が最大3ブロックぶん遅れる(復元が遅い)。
        drainFadeRestores();
        // 遷移フェードの走査/差分検出を同じティックで行う。チャンク再構築のスケジューリング
        // (ClientForgeEvents の CullingManager.tick 後段)より先にフラッシュとメッシュホールドを
        // 確定させる。描画パスで遅れて検出すると、実ブロックが先にメッシュから消えてから
        // 消失フラッシュが始まる(α=1が一瞬見えてからフェードに差し替わる)レースが残る。
        updateFadePositions(mc.level);
        long tEntity = System.nanoTime();
        updateEntityCulling(mc);
        PerfMonitor.ENTITY_CULL.add(System.nanoTime() - tEntity);
    }

    /**
     * カメラが地形に埋没しているかを判定する。カメラのブロックが固体のまま一定距離
     * ({@link #BURIED_ENTER_DISTANCE} ブロック)走破したら埋没とし、遷移フェードを抑制する。
     *
     * <p>地表ハイトマップや Y を使わないため次元に依存しない。単一ブロックのかすめ
     * (走破距離が届かない)や、カメラ位置が空気の開けた洞窟(固体条件が偽)では発動しない。
     * 離脱は {@link #BURIED_EXIT_DISTANCE} まで減衰してからで、固体/空洞が交互の地形でも往復しない。
     */
    private void updateCameraBuried(Minecraft mc, int camBlockX, int camBlockY, int camBlockZ) {
        if (!Config.isDisableFadeBuried() || mc.level == null) {
            cachedCameraBuried = false;
            cameraBuriedTravel = 0.0;
            return;
        }
        cameraProbe.set(camBlockX, camBlockY, camBlockZ);
        BlockState camState = mc.level.getBlockState(cameraProbe);
        boolean buriedRaw = !camState.isAir()
                && !camState.getCollisionShape(mc.level, cameraProbe).isEmpty();

        double step = 0.0;
        if (lastBuriedCamX != Integer.MIN_VALUE) {
            double dx = camBlockX - lastBuriedCamX;
            double dy = camBlockY - lastBuriedCamY;
            double dz = camBlockZ - lastBuriedCamZ;
            step = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        lastBuriedCamX = camBlockX;
        lastBuriedCamY = camBlockY;
        lastBuriedCamZ = camBlockZ;

        if (buriedRaw) {
            cameraBuriedTravel = Math.min(cameraBuriedTravel + step, BURIED_ENTER_DISTANCE);
        } else {
            cameraBuriedTravel = Math.max(cameraBuriedTravel - step, 0.0);
        }
        if (!cachedCameraBuried && cameraBuriedTravel >= BURIED_ENTER_DISTANCE) {
            cachedCameraBuried = true;
        } else if (cachedCameraBuried && cameraBuriedTravel <= BURIED_EXIT_DISTANCE) {
            cachedCameraBuried = false;
        }
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
        // ドールハウス表示は生の検出結果を即座に反映する（カリングのヒステリシスに追従させない）。
        rawSpaceResult = probed;
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
        // カリング無効時はドールハウス表示用に空間判定だけを提供し、カリング固有の走査は一切走らせない。
        final boolean cullingEnabled = ModState.STATUS.isCullingEnabled();
        boolean elementActive = cullingEnabled && currentSpaceEnclosed && cachedIndoorCeilingEnabled;
        cachedIndoorElementActive = elementActive;
        cachedCoverCullingActive = cullingEnabled && cachedCullingMode != CullingConfig.CULLING_MODE_CYLINDER;

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

        if (!cullingEnabled) {
            return;
        }

        // 受理時点のプレイヤー位置で走査する (依頼時の座標は probe 遅延で既に古い可能性がある)。
        final int currentBlockX = (int) Math.floor(mc.player.getX());
        final int currentBlockY = (int) Math.floor(mc.player.getEyeY());
        final int currentBlockZ = (int) Math.floor(mc.player.getZ());

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
            // 覆い集合の入れ替わりでカリング判定が反転する。判定キャッシュが古い値を保持した
            // ままだと、メッシュからの除去が走査(フェード開始)より遅れて発生し、フェード途中で
            // ブロックが弾かれる(カリングがフェードに先行する)。集合確定の同ティックで判定を
            // 反転させ、メッシュの除去とフェード開始を揃える。
            cullingCache.clear();
        } else {
            coverHandler.clearCache();
        }

        // 連鎖壁カリングも覆いと同じ「プローブ間隔」で再計算する(毎ティック再構築は入力の
        // 1ティック揺れをすべてメンバーシップの揺れにして不安定になる)。シードは幾何のみで、
        // 判定の出力(=フェード収集合)を参照しないため自己参照ループが無い。
        if (ModState.STATUS.isEnabled() && Config.isConnectedWallCullingEnabled()) {
            long wallGenBefore = connectedWallHandler.getGeneration();
            connectedWallHandler.update(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ,
                    viewDirX, viewDirZ, cachedCameraSideClipWedge, cachedViewWedgeCos,
                    cachedCylinderRadiusHorizontal, cachedCylinderRadiusVertical,
                    (pos, state) -> isProtectedBlock(pos, state, playerY, level));
            if (connectedWallHandler.getGeneration() != wallGenBefore) {
                // 覆いと同じ: 集合確定の同ティックで判定を反転させる。
                cullingCache.clear();
            }
        } else {
            connectedWallHandler.clearCache();
        }
    }

    /**
     * カリング側の集合差分(天井スライス・覆い)の離脱イベントを消費する。
     *
     * <p>ここでは復元フラッシュを登録しない。復元は走査差分({@code processCullSet})が唯一の
     * 発生源であり、ドレインが個別に復元を登録すると走査差分と二重に発火し、「出現→一度消え→
     * 再度復元」の二回復元(フェードOFFでも点滅)になる。走査集合は覆いの全メンバー
     * ({@code addOverdueCullPositions})を含むため、覆いの離脱は必ず次の走査差分で検出され、
     * ホールド/ゴーストはそこで一貫して管理される。判定キャッシュだけを破棄し、
     * 再構築が新しい生判定で焼かれるようにする。
     */
    private void drainFadeRestores() {
        LongOpenHashSet leaving = new LongOpenHashSet();
        ceilingSliceCuller.takeLeavingPositions(leaving);
        coverHandler.takeDroppedPositions(leaving);
        connectedWallHandler.takeDroppedPositions(leaving);
        if (!leaving.isEmpty()) {
            cullingCache.clear();
            LongIterator iterator = leaving.iterator();
            while (iterator.hasNext()) {
                revealChange.includeCell(iterator.nextLong());
            }
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
        Vec3 eyePos = mc.player.getEyePosition();
        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Player && entity == mc.player) continue;
                if (entity instanceof Cullable cullable) {
                    if (!isCullableEntityType(entity)) {
                        cullable.topdownview_setCulled(false);
                        continue;
                    }
                    boolean shouldCull = (entity instanceof Mob || entity instanceof ItemEntity)
                        ? shouldCullSupportedEntity(entity, mc, playerFeetBlockY, eyePos)
                        : shouldCullDecorativeEntity(entity, playerX, playerY, playerZ);
                    cullable.topdownview_setCulled(shouldCull);
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            LOGGER.debug("[TopDownView] Entity list modified concurrently during culling update, will retry next frame", e);
        }
    }

    /**
     * プレイヤーより上の階（2階など）にいるMobや、カリング対象のブロックの上に落ちている
     * ドロップアイテムをカリングする。
     *
     * <p>足元の支え（接地している面）がカリング対象なら、その床が消されて見えてしまっている
     * 上の階のエンティティとみなす。旧実装はプレイヤーとMobの間のブロックまで縦スキャンしていたため、
     * 階段などで少し高い位置にいるMobまで消えていた。支えの1点だけを見ることで視認性を保つ。
     *
     * <p>支えがカリング対象でも、プレイヤー目線から遮蔽されていなければ残す。床だけが消えて
     * Mob自体は見えている場合に、見えるMobまで消えるのを防ぐ。
     */
    private boolean shouldCullSupportedEntity(Entity entity, Minecraft mc, int playerFeetBlockY, Vec3 eyePos) {
        if (mc.level == null) return false;
        int entityBlockY = entity.getBlockY();
        if (entityBlockY <= playerFeetBlockY + 1) return false;
        int ex = entity.getBlockX();
        int ez = entity.getBlockZ();
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            entityGroundedPos.set(ex, entityBlockY - yOffset, ez);
            if (mc.level.getBlockState(entityGroundedPos).isAir()) continue;
            if (!isBlockCulled(entityGroundedPos, mc.level)) {
                return false;
            }
            return !MouseRaycast.INSTANCE.hasLineOfSight(mc, eyePos, entity);
        }
        return false;
    }

    private boolean shouldCullDecorativeEntity(Entity entity, double pX, double pY, double pZ) {
        Vec3 pos = entity.position();
        double dx = pos.x - pX;
        double dy = pos.y - pY;
        double dz = pos.z - pZ;
        if (dx * dx + dy * dy + dz * dz <= ENTITY_PROTECTION_RADIUS_SQ) return false;
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(pos.x, pos.y, pos.z);
        if (normalizedDistSq < 0) return false;
        return normalizedDistSq <= 1.0;
    }

    private boolean isCullableEntityType(Entity entity) {
        if (entity instanceof Mob) return Config.isMobCullingEnabled();
        if (entity instanceof ItemEntity) return Config.isItemCullingEnabled();
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

    /** 覆いカリング(Newモード)が有効か。再構築ボックスを覆い半径まで広げる判断に使う。 */
    public boolean isCoverCullingActive() {
        return cachedCoverCullingActive;
    }

    /** デバッグHUD用: 連鎖カリング中のブロック数。 */
    public int getChainCount() {
        return connectedWallHandler.getChainCount();
    }

    /** メッシュ再構築バッチの確定通知。復元帳簿の締め処理を行う。 */
    public void onMeshCommit(LongOpenHashSet committedSections, long batchScheduledAtMs) {
        fadeTransitionController.onMeshCommit(System.currentTimeMillis(), committedSections, batchScheduledAtMs);
    }

    /** メッシュ専用ホールドの解除でメッシュ復帰の再構築が必要か。 */
    public boolean isMeshHoldRebuildPending() {
        return fadeTransitionController.isMeshHoldRebuildPending();
    }

    public void consumeMeshHoldRebuildPending() {
        fadeTransitionController.consumeMeshHoldRebuildPending();
    }

    /** 復元フェードの差分トラッカー(描画側が遷移時刻を読むため公開)。 */
    public FadeTransitionController getFadeController() {
        return fadeTransitionController;
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
     * 覆い集合(Newモード)も列集合が入れ替わったときに世代を進め、覆い半径まで広げた
     * 再構築ボックスの再構築を誘発する。
     */
    public long getCullingGeneration() {
        return ceilingSliceCuller.getGeneration() * 31L + coverHandler.getGeneration() * 7L
                + connectedWallHandler.getGeneration();
    }

    /**
     * 直近の空間プローブ結果（部屋ドールハウス表示のマスク生成元）。未プローブなら null。
     *
     * <p>屋内ヒステリシスを通さない生の結果を返す。カリング用の {@code currentSpaceResult} は
     * 段差でのブレを抑えるため数ブロック遅延するが、ドールハウス表示はそれに追従させない。
     */
    public SpaceProbe.Result getSpaceResult() {
        return rawSpaceResult;
    }

    /**
     * カリング適用の基準となる空間プローブ結果 (屋内ヒステリシス適用後)。未確定なら null。
     *
     * <p>空間デバッグ表示はこの結果に単一化する。デバッグ独自のプローブは
     * カリング実体とゲート (移動閾値・ヒステリシス) がずれ、表示と動作の乖離を生むため。
     */
    public SpaceProbe.Result getAppliedSpaceResult() {
        return currentSpaceResult;
    }

    /** 受理済みプローブの BlockMap (建物分類などの追加解析用)。未確定なら null。 */
    public com.topdownview.spatial.BlockMap getSpaceBlockMap() {
        return spaceScratch != null ? spaceScratch.getBlockMap() : null;
    }

    /** 階段ハンドラが直近の走査で検出した階段一覧 (デバッグ表示用)。 */
    public List<Staircase> getDetectedStaircases() {
        return stairHandler.getDetectedStaircases();
    }

    /** 天井スライスの差分範囲を返す。空なら差分追跡できている集合の変化は無い。 */
    public BlockChangeBox getPendingElementChange() {
        pendingElementChange.reset();
        pendingElementChange.includeBox(ceilingSliceCuller.getPendingChange());
        // 復元開示(覆いドロップ・走査差分・ホールド解除)も同じボックス経路で配る。
        pendingElementChange.includeBox(revealChange);
        return pendingElementChange;
    }

    /** 復元が確定した位置の開示ボックスが空でないか(再構築トリガ)。 */
    public boolean hasPendingRevealChange() {
        return !revealChange.isEmpty();
    }

    /** 再構築を実際にスケジュールした後に呼ぶ。次回の差分を新しく蓄積し直す。 */
    public void clearPendingElementChange() {
        ceilingSliceCuller.clearPendingChange();
        revealChange.reset();
    }

    /**
     * カリング境界の半ゴースト表示の間、マウスレイキャストをブロックするか。
     * 遷移フェード(消失/復元)のフラッシュ中の位置は実ブロックとみなして触れられる。
     */
    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled() || ModState.STATUS.isMiningMode() || level == null) return false;
        if (!cachedFadeTransitionsActive) return false;
        return com.topdownview.client.TranslucentBlockRenderer.isGhostVisible(pos.asLong());
    }

    /**
     * FASTグラフィックの葉は不透明テクスチャで描かれるため、半透明ゴーストにできない。
     * {@code calculateFadeAlpha} と同じ判定を近接半透明化側でも使う。
     */
    private boolean isFastGraphicsLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES)
                && Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /**
     * 遷移フェードの走査をティック(update)内で実行する。チャンク再構築のスケジューリング
     * (同 tick の後続)より先にフラッシュ/メッシュホールドを確定させるため、ここでしか走らない。
     */
    public void updateFadePositions(BlockGetter level) {
        long tCollect = System.nanoTime();
        collectCullSetImpl(level);
        PerfMonitor.FADE_COLLECT.add(System.nanoTime() - tCollect);
    }

    /**
     * 走査済みの遷移フェード対象集合を返す(描画パス用・走査はしない)。
     */
    public LongOpenHashSet getCollectedFadePositions() {
        return fadePositions;
    }

    private LongOpenHashSet collectCullSetImpl(BlockGetter level) {
        // フェード抑制中(設定OFF/屋内/カメラ埋没)は収集も差分も行わない。収集集合の消費者は
        // フラッシュ登録と描画のみで、どちらも抑制中は働かない。ただし円柱走査の幾何パスは
        // メッシュ境界ヒステリシス帳簿の維持のため常時走らせる(collect=false で呼ぶ)。
        boolean transitionsActive = ModState.STATUS.isEnabled() && ModState.STATUS.isCullingEnabled()
                && !ModState.STATUS.isMiningMode() && Config.isFadeEnabled()
                && !cachedDisableIndoorFade && !cachedCameraBuried && contextValid;
        if (!transitionsActive && cachedFadeTransitionsActive) {
            cachedFadeTransitionsActive = false;
            fadeTransitionController.clearCache();
        }
        if (transitionsActive && !cachedFadeTransitionsActive) {
            cachedFadeTransitionsActive = true;
        }
        if (level == null) {
            return fadePositions;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            int pBX = cachedPlayerBlockX;
            int pBY = cachedPlayerFloorY;
            int pBZ = cachedPlayerBlockZ;
            int cBX = (int) Math.floor(cameraX);
            int cBY = (int) Math.floor(cameraY);
            int cBZ = (int) Math.floor(cameraZ);
            // 覆い集合/天井スライスは非同期プローブ受理で任意ティックに入れ替わる。凍結条件を
            // 座標変化だけにすると、静止中の集合交代で判定だけ反転し再構築が先に走り
            // (メッシュからブロックが消える)、フェード走査の検出が遅れて「カリング後に出現」に
            // 見える。集合の世代変化でも走査を再実行する。
            long coverGen = coverHandler.getGeneration();
            long sliceGen = ceilingSliceCuller.getGeneration();
            long wallGen = connectedWallHandler.getGeneration();
            if (pBX == lastFadePBlockX && pBY == lastFadePBlockY && pBZ == lastFadePBlockZ
                    && cBX == lastFadeCBlockX && cBY == lastFadeCBlockY && cBZ == lastFadeCBlockZ
                    && coverGen == lastFadeCoverGen && sliceGen == lastFadeSliceGen
                    && wallGen == lastFadeWallGen) {
                return fadePositions;
            }
            lastFadePBlockX = pBX;
            lastFadePBlockY = pBY;
            lastFadePBlockZ = pBZ;
            lastFadeCBlockX = cBX;
            lastFadeCBlockY = cBY;
            lastFadeCBlockZ = cBZ;
            lastFadeCoverGen = coverGen;
            lastFadeSliceGen = sliceGen;
            lastFadeWallGen = wallGen;
        }

        fadePositions.clear();
        LongOpenHashSet current = fadePositions;

        long tHandlers = System.nanoTime();
        if (transitionsActive) {
            if (cachedCoverCullingActive) {
                coverHandler.addOverdueCullPositions(current);
            }
            if (cachedIndoorElementActive) {
                ceilingSliceCuller.forEachSlicePosition(current);
            }
        }
        PerfMonitor.FADE_SCAN_HANDLERS.add(System.nanoTime() - tHandlers);

        long tCylinder = System.nanoTime();
        collectCylinderCullPositions(level, playerX, playerY, playerZ, cameraX, cameraY, cameraZ,
                transitionsActive, current);
        PerfMonitor.FADE_SCAN_CYLINDER.add(System.nanoTime() - tCylinder);

        // 連鎖メンバーの最終除外(2層目)。階段/ラダー/木/覆い/スライスの各収集器は位置を直接
        // add するため、円柱ループ内の除外だけでは連鎖メンバーが再混入する。差分の前に一括で
        // 弾くことで、連鎖の当落・予算消費がフェードへ漏れない。
        current.removeIf(connectedWallHandler::isConnectedCulled);

        // 消失/復元の差分を検出する。収集漏れは生判定で保持され、復元ホールドが立つ。
        // 収集漏れの保持(再カリングの誤フラッシュ防止)のため生判定で検証する。
        long tDiff = System.nanoTime();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        // 描画側のゴースト距離より僅かに広く取る。これより遠い遷移はゴーストが見えず
        // メッシュホールドも掛からないため、登録せず地図の肥大と遷移枠の浪費を防ぐ。
        double flashDist = TranslucentBlockRenderer.GHOST_RENDER_DISTANCE + 1.0;
        if (transitionsActive) {
            // 連鎖メンバーは既にメッシュから消えている。帳簿に載せておかないと、連鎖から外れて
            // 円柱側でカリング継続になった時に新規消失扱いとなり、半透明で現れてすぐ消える。
            fadeTransitionController.markCulledSilently(connectedWallHandler.getMembers());
            fadeTransitionController.processCullSet(current, posLong -> {
                probe.set(BlockPos.getX(posLong), BlockPos.getY(posLong), BlockPos.getZ(posLong));
                return isBlockCulled(probe, level);
            }, cachedPlayerBlockX, cachedPlayerFloorY, cachedPlayerBlockZ, flashDist * flashDist);
        }
        PerfMonitor.FADE_SCAN_DIFF.add(System.nanoTime() - tDiff);

        fadeTransitionController.publishMeshHoldView();
        publishPyramidStickyView();
        return current;
    }

    /**
     * 円柱フェード帯(カメラとプレイヤー間で半透明表示の対象になっていたブロック)の
     * 内、カリング条件と一致する位置を収集する。地下カリングはここでは扱わない
     * (遠方地下の消去はフェードなしの即時切替)。
     *
     * <p>{@code collect=false}(フェード抑制中)は位置を収集せず、境界ヒステリシス帳簿のみを
     * 幾何で更新する。getBlockState を呼ばないため埋没空間での走査コストが激減する。
     */
    private void collectCylinderCullPositions(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, boolean collect, LongOpenHashSet out) {
        int radiusH = cachedCylinderRadiusHorizontal;
        int radiusV = cachedCylinderRadiusVertical;
        int margin = 2;

        int minX = (int) Math.floor(Math.min(pX, cX)) - radiusH - margin;
        int maxX = (int) Math.floor(Math.max(pX, cX)) + radiusH + margin;
        int minY = (int) Math.floor(Math.min(pY, cY)) - 1;
        int maxY = (int) Math.floor(Math.max(pY, cY)) + radiusV + margin;
        int minZ = (int) Math.floor(Math.min(pZ, cZ)) - radiusH - margin;
        int maxZ = (int) Math.floor(Math.max(pZ, cZ)) + radiusH + margin;

        MutableBlockPos mutablePos = new MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);
                    // 円柱内かを先に判定する。円柱そのものと同一の CylinderCalculator を使うため
                    // 走査とメッシュ判定の境界で食い違わない。捨てボックスの約7割が円柱外のため、
                    // 生判定のパイプラインコストを円柱内ブロックだけに抑えられる。
                    double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                            x + 0.5, y + 0.5, z + 0.5);
                    if (normalizedDistSq < 0.0 || normalizedDistSq > 1.0) {
                        if (!collect) {
                            continue;
                        }
                        // 円柱外でもカリングが真になるのは下支えカリングに連動する薄いブロック類
                        // (雪・カーペット・植物など)。下支えのカリング判定だけを見て拾う。
                        BlockState state = level.getBlockState(mutablePos);
                        if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                        if (isRestingOnCulledBlock(mutablePos, state, level)) {
                            out.add(mutablePos.asLong());
                            if (out.size() >= MAX_FADE_POSITIONS) return;
                        }
                        continue;
                    }
                    if (!collect) {
                        // フェード抑制中は収集しないが、境界ヒステリシス帳簿はメッシュ判定が読むため
                        // 幾何だけで更新する。getBlockState は呼ばない(走査コストの本体)。
                        updateStickyForCell(mutablePos, normalizedDistSq);
                        continue;
                    }
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;

                    updateStickyForCell(mutablePos, normalizedDistSq);

                    // 円柱の即時計算ではなく判定キャッシュのみで集める(メッシュ構築時の値と一致させる)。
                    // メッシュと同じ判定(キャッシュ共有)で確定させる。円柱の即時計算だけで集めると
                    // 境界でメッシュと食い違い、既に穴の位置に消失ゴースト(α=1)が立つ。
                    if (!isBlockCulled(mutablePos, level)) continue;

                    long posLong = mutablePos.asLong();

                    // 覆いブロックは覆い側の時間差カリングに任せる(円柱フェードと二重に扱わない)
                    if (cachedCoverCullingActive && coverHandler.isCoverCulled(mutablePos)) continue;
                    // 連鎖メンバーは即時切替(フェード予算を消費しない)。連鎖は家に入った瞬間など
                    // 数百メンバーを一斉公開するため、フェードの512枠を埋めて覆い等の本来の
                    // 消失/復元イベントを登録拒否にしていた。帳簿(previousCulled)は復元ループの
                    // stillCulled put-back が同期する(連鎖メンバー=判定true→必ず戻る)。
                    if (connectedWallHandler.isConnectedCulled(posLong)) continue;
                    if (state.is(BlockTags.LEAVES)
                            && Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST) {
                        continue;
                    }

                    out.add(posLong);
                    if (out.size() >= MAX_FADE_POSITIONS) return;
                }
                if (out.size() >= MAX_FADE_POSITIONS) return;
            }
            if (out.size() >= MAX_FADE_POSITIONS) return;
        }

        publishPyramidStickyView();
    }

    /**
     * 円柱内セル1つ分の境界ヒステリシス帳簿を幾何だけで更新する。ブロック種別を参照しないため
     * フェード抑制中の走査でも getBlockState なしで呼べる。書き手は走査(メインスレッド)のみ。
     */
    private void updateStickyForCell(BlockPos pos, double normalizedDistSq) {
        long posLong = pos.asLong();
        // ピラミッド境界: 0以上で採用、解放幅より下で解放、中間は帳簿を保持(揺れを吸収)。
        double pyrDiff = PyramidProtectionCalc.calculateBoundaryDiff(
                pos, playerX, playerY, playerZ, cameraX, cameraZ);
        if (pyrDiff >= 0.0) {
            if (pyramidSticky.put(posLong, (byte) 1) != (byte) 1) {
                pyramidStickyDirty = true;
            }
        } else if (pyrDiff < -PYRAMID_STICKY_RELEASE) {
            if (pyramidSticky.remove(posLong) == (byte) 1) {
                pyramidStickyDirty = true;
            }
        }

        // 半空間クリップ境界: ±幅を超えたときだけ側を確定させる。
        double clipDot = (pos.getX() + 0.5 - playerX) * viewDirX + (pos.getZ() + 0.5 - playerZ) * viewDirZ;
        if (clipDot > CLIP_STICKY_MARGIN) {
            if (clipSticky.put(posLong, (byte) 1) != (byte) 1) {
                clipStickyDirty = true;
            }
        } else if (clipDot < -CLIP_STICKY_MARGIN) {
            if (clipSticky.remove(posLong) == (byte) 1) {
                clipStickyDirty = true;
            }
        }

        // 円柱境界の出口ヒステリシス: 内側の間は採用、余白明けで解放。
        if (normalizedDistSq <= 1.0) {
            if (cylinderSticky.put(posLong, (byte) 1) != (byte) 1) {
                cylinderStickyDirty = true;
            }
        } else if (normalizedDistSq > cylinderExitStickyLimit()) {
            if (cylinderSticky.remove(posLong) == (byte) 1) {
                cylinderStickyDirty = true;
            }
        }
    }

    /** ヒステリシス帳簿の変更をワーカー参照用の不変スナップショットへ反映する(定期パージ含む)。 */
    private void publishPyramidStickyView() {
        if (cylinderStickyDirty) {
            // チューブ外に滞留した帳簿をパージする(走査範囲外に誘導された場合の肥大防止)。
            double limit = cylinderExitStickyLimit();
            LongIterator purgeIterator = cylinderSticky.keySet().iterator();
            while (purgeIterator.hasNext()) {
                long posLong = purgeIterator.nextLong();
                double norm = CylinderCalculator.getNormalizedDistanceSq(
                        BlockPos.getX(posLong) + 0.5, BlockPos.getY(posLong) + 0.5,
                        BlockPos.getZ(posLong) + 0.5);
                if (norm < 0.0 || norm > limit) {
                    purgeIterator.remove();
                    cylinderStickyDirty = true;
                }
            }
            cylinderStickyView = new LongOpenHashSet(cylinderSticky.keySet());
            cylinderStickyDirty = false;
        }
        if (!pyramidStickyDirty && !clipStickyDirty) {
            return;
        }
        pyramidStickyDirty = false;
        clipStickyDirty = false;
        pyramidStickyView = new LongOpenHashSet(pyramidSticky.keySet());
        clipStickyView = new LongOpenHashSet(clipSticky.keySet());
    }
}
