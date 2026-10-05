package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.culling.geometry.BlockChangeBox;
import com.topdownview.spatial.BlockMap;
import com.topdownview.state.ModState;
import com.topdownview.util.PerfMonitor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class CullingManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final TopDownCuller CULLER = TopDownCuller.getInstance();
    private static final long CHUNK_REBUILD_INTERVAL_MS = 50;
    private static final String SODIUM_RENDERER_CLASS = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";
    /** ビルドが続いてもアップロード保留を打ち切る上限フレーム数(スタック防止)。 */
    private static final int MAX_UPLOAD_HOLD_FRAMES = 60;
    /**
     * 次元変更後にカリングを保留する上限ティック数(保険)。通常はロード画面が閉じた時点で
     * 早期解除される。無限に保留しないための安全弁。
     */
    private static final int DIMENSION_SETTLE_MAX_TICKS = 200;
    /** これ以上の辺を持つ再構築ボックスは異常(旧次元のカメラ等)として捨てる。 */
    private static final double MAX_REBUILD_SPAN = 512.0;

    /** 現在の次元。変更を検出して settle に入る。 */
    private static ResourceKey<Level> lastDimension = null;
    private static int settleTicks = 0;

    // カリング断面がセクションを跨ぐため、バッチの全セクションが揃うまでアップロードを保留する。
    // 保留対象はこのバッチのセクションだけに限定する(新規チャンクロード等の無関係な
    // アップロードを巻き込むと、画面外周のチャンクがアップロードされず穴になる)。
    // さらに、確定するまで次の再構築を積まないことで、バッチの割り込みを防ぐ。
    // これで f2a1eb9 の同期と同じ「同一フレーム確定」を描画スレッドをブロックせずに再現する。
    private static boolean batchPending = false;
    private static int batchFrames = 0;
    /** 現在保留中のバッチのスケジュール時刻(ms)。復元帳簿の締め判定に使う。 */
    private static long batchScheduledAtMs;
    /** 現在のバッチに含まれるセクション座標({@link SectionPos#asLong})。 */
    private static final LongOpenHashSet batchSections = new LongOpenHashSet();

    private static boolean initialized = false;
    private static boolean initializationFailed = false;
    private static long lastChunkRebuildTime = 0;
    private static Method instanceMethod = null;
    private static Method rebuildMethod = null;

    private static int lastRebuildPlayerX = Integer.MIN_VALUE;
    private static int lastRebuildPlayerY = Integer.MIN_VALUE;
    private static int lastRebuildPlayerZ = Integer.MIN_VALUE;
    private static int lastRebuildCameraX = Integer.MIN_VALUE;
    private static int lastRebuildCameraY = Integer.MIN_VALUE;
    private static int lastRebuildCameraZ = Integer.MIN_VALUE;
    private static long lastRebuildGeneration = Long.MIN_VALUE;
    /** 前回再構築時に確定したカメラ文脈の改訂番号。回転(フレーム毎)で変わる。 */
    private static long lastRebuildContextRevision = Long.MIN_VALUE;

    private CullingManager() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean initializeReflection() {
        if (initialized) return true;
        if (initializationFailed) return false;

        try {
            Class<?> rendererClass = Class.forName(SODIUM_RENDERER_CLASS);
            instanceMethod = rendererClass.getMethod("instance");
            rebuildMethod = rendererClass.getMethod(
                    "scheduleRebuildForBlockArea",
                    int.class, int.class, int.class,
                    int.class, int.class, int.class,
                    boolean.class);
            initialized = true;
            LOGGER.info("Embeddium reflection initialized successfully");
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("Embeddium/Sodium not found. TopDownView requires Embeddium. Class: {}", SODIUM_RENDERER_CLASS);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Required method not found in SodiumWorldRenderer: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Embeddium reflection: {}", e.getMessage());
        }

        initializationFailed = true;
        return false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // ブロックを消して見通せるトップダウン中は、EntityCulling のカメラ基準カリングを止める
        EntityCullingIntegration.setSuspended(ModState.STATUS.isEnabled() && ModState.STATUS.isCullingEnabled());

        // 次元変更直後は旧次元のカリング判定/probe/再構築を引きずるため、いったん状態を捨てる。
        // 新レベルが描画可能になるまでは旧カメラ座標で走らせないが、ロード画面が閉じたら
        // 待たずに即再開する(固定待ちだとカリング開始が遅れる)。
        ResourceKey<Level> dimension = mc.level.dimension();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            settleTicks = DIMENSION_SETTLE_MAX_TICKS;
            CULLER.reset();
            commitBatch();
            resetLastRebuildCoords();
        }
        if (settleTicks > 0) {
            if (isLevelReadyForCulling(mc)) {
                settleTicks = 0;
            } else {
                settleTicks--;
                return;
            }
        }

        int frequency = CULLER.getFrequency();
        if (mc.player.tickCount % frequency == 0) {
            long tUpdate = System.nanoTime();
            CULLER.update();
            PerfMonitor.CULL_UPDATE.add(System.nanoTime() - tUpdate);
        }

        if (ModState.STATUS.isEnabled()) {
            scheduleChunkRebuildIfNeeded();
        }
    }

    /**
     * レンダーフレーム単位のフック({@code RenderSectionManagerMixin} から毎フレーム呼ぶ)。
     *
     * <p>カリング文脈と再構築スケジュールをクライアントtick(20Hz)だけで回すと、カメラ回転が
     * 1tick(最大50ms)遅れて追従し、さらにバッチ確定後に次tickを待つ分だけメッシュ更新が
     * 遅れる。フレーム毎にカメラ文脈を更新し、空いているバッチへ再構築を積むことで追従を揃える。
     * スケジュールは batchPending で直列化されるため、バッチ確定以上の頻度では走らない。
     */
    public static void onRenderFrame() {
        // 2587d62 のフレーム毎文脈更新は bisect で境界ブロックのカリング/復元の往復を
        // 再現する事象源と確定したため無効化した。カメラ文脈は tick 側
        // (TopDownCuller.update → syncCameraContext)で更新され、再構築も 50ms スロットル
        // のメインパスのみで回る (cdfbb3c 相当の挙動)。回転追従は最大 50ms 遅れるだけで、
        // 量子化された判定上は culling 領域の形状変化は接到で起きないため許容。
    }

    /**
     * 新レベルを描画できる状態か。ロード画面が閉じ、カメラがプレイヤー近傍にある
     * (別次元の残存座標でない)とき true。true になった時点でカリングを再開する。
     */
    private static boolean isLevelReadyForCulling(Minecraft mc) {
        if (mc.screen != null) {
            return false;
        }
        Vec3 cameraPos = ModState.CAMERA.getCameraPosition();
        if (!com.topdownview.state.CameraState.isPositionValid(cameraPos)) {
            return false;
        }
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        return cameraPos.distanceToSqr(eyePos) <= MAX_REBUILD_SPAN * MAX_REBUILD_SPAN;
    }

    private static void scheduleChunkRebuildIfNeeded() {
        if (!initializeReflection()) {
            return;
        }

        // 未確定のバッチがある間は新しい再構築を積まない。積むとバッチが完了前に陳腐化し、
        // 部分アップロードで断面に穴が残る。確定(アップロード)後に改めて評価する。
        if (batchPending) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 cameraPos = ModState.CAMERA.getCameraPosition();

        if (!com.topdownview.state.CameraState.isPositionValid(cameraPos)) {
            return;
        }

        int pX = (int) Math.floor(playerPos.x);
        int pY = (int) Math.floor(playerPos.y);
        int pZ = (int) Math.floor(playerPos.z);
        int cX = (int) Math.floor(cameraPos.x);
        int cY = (int) Math.floor(cameraPos.y);
        int cZ = (int) Math.floor(cameraPos.z);

        // 復元フェードのメッシュ専用ホールドが解除されたら、メッシュを戻す再構築を一度だけ要求する。
        boolean meshHoldRelease = CULLER.isMeshHoldRebuildPending();
        // 復元開示(覆いドロップ・走査差分)が未反映なら座標が変わらなくても再構築が必要。
        boolean revealRebuild = CULLER.hasPendingRevealChange();
        // カリング集合の世代(天井スライス+覆い)。覆い集合が入れ替わったら座標が同じでも再構築する。
        long generation = CULLER.getCullingGeneration();
        boolean generationChanged = generation != lastRebuildGeneration;
        // カメラ文脈の改訂番号。回転(フレーム毎)や真上付近での yaw 回転でも変化する。
        long contextRevision = CULLER.getViewContextRevision();
        boolean contextChanged = contextRevision != lastRebuildContextRevision;
        if (!meshHoldRelease && !revealRebuild && !generationChanged && !contextChanged
                && pX == lastRebuildPlayerX && pY == lastRebuildPlayerY && pZ == lastRebuildPlayerZ
                && cX == lastRebuildCameraX && cY == lastRebuildCameraY && cZ == lastRebuildCameraZ) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkRebuildTime < CHUNK_REBUILD_INTERVAL_MS) {
            // ホールド解除/復元開示の再構築は間隔待ちを飛ばす(穴即閉鎖優先)。再構築予約があっても
            // 50ms 待つと、復元ゴーストの寿命が尽きて1フレーム穴が出る。
            // カメラ回転による contextChanged は 2587d62 でバイパス扱いだったが、自動回転中は
            // 毎フレーム contextChanged が立って再構築がバッチ確定間隔で連発し、チューブ境界が
            // サブブロックずれるたびに境界ブロックのカリング/復元が往復した。回転は
            // 50ms スロットル (≈20Hz) で十分視覚的に追従する。
            if (!meshHoldRelease && !revealRebuild) {
                return;
            }
        }

        int radiusH;
        int radiusV;
        if (ModState.STATUS.isMiningMode()) {
            radiusH = Config.getMiningCylinderRadius();
            radiusV = Config.getMiningCylinderRadius();
        } else {
            radiusH = Config.getCylinderRadiusHorizontal();
            radiusV = Config.getCylinderRadiusVertical();
        }
        // 覆いは円柱より広い。箱を覆い半径まで広げて該当セクションを再構築する。円柱ボックスの
        // ままだと覆い領域(円柱半径の外側)のセクションが再構築されず、カリング済みなのに
        // メッシュが残って遅れて消える(円柱モードでは起きない覆いモード固有の不具合)。
        AABB box = new AABB(playerPos, cameraPos).inflate(radiusH, radiusV, radiusH);
        if (CULLER.isCoverCullingActive() || meshHoldRelease) {
            int coverRadius = Config.getCoverCullingRadius();
            box = box.inflate(
                    Math.max(0, coverRadius - radiusH),
                    Math.max(0, coverRadius - radiusV),
                    Math.max(0, coverRadius - radiusH));
        }
        // 旧次元のカメラ位置などでプレイヤーと大きく離れた箱は、Embeddium に膨大なセクションを
        // 再構築させてフリーズさせるため捨てる。
        if (box.getXsize() > MAX_REBUILD_SPAN || box.getYsize() > MAX_REBUILD_SPAN
                || box.getZsize() > MAX_REBUILD_SPAN) {
            LOGGER.warn("[TopDownView] skipped abnormal rebuild box {}x{}x{} (player {} {} {}, camera {} {} {})",
                    (int) box.getXsize(), (int) box.getYsize(), (int) box.getZsize(),
                    pX, pY, pZ, cX, cY, cZ);
            return;
        }

        // 差分(天井スライス)がある要素再構築と、復元開示(覆いドロップ・走査差分・ホールド解除)。
        // 復元位置はプレイヤー↔カメラボックスの外にいることがあるため、revealPending のときは
        // 差分ボックスを必ず union する(ボックスが届かずメッシュが復帰しない事象の修正)。
        boolean elementRebuild = generationChanged && CULLER.isIndoorElementActive();
        boolean wideElementRebuild = false;
        if (elementRebuild || revealRebuild) {
            BlockChangeBox pending = CULLER.getPendingElementChange();
            if (!pending.isEmpty()) {
                // 天井スライス等の差分セルだけを再構築する。集合の変化は通常数ブロック
                // なので、探索キャッシュ全域(RADIUS_XZ)を再構築するより大幅に軽い。
                box = new AABB(
                        Math.min(box.minX, pending.getMinX()),
                        Math.min(box.minY, pending.getMinY()),
                        Math.min(box.minZ, pending.getMinZ()),
                        Math.max(box.maxX, pending.getMaxX() + 1.0),
                        Math.max(box.maxY, pending.getMaxY() + 1.0),
                        Math.max(box.maxZ, pending.getMaxZ() + 1.0));
            } else if (elementRebuild) {
                // 差分不明時のみ探索キャッシュ全域へ広げる。
                wideElementRebuild = true;
                int revealRadius = BlockMap.RADIUS_XZ;
                box = box.inflate(
                        Math.max(0, revealRadius - radiusH),
                        Math.max(0, revealRadius - radiusV),
                        Math.max(0, revealRadius - radiusH));
            }
        }

        if (scheduleChunkRebuildInternal(box, true)) {
            lastChunkRebuildTime = currentTime;
            CULLER.consumeMeshHoldRebuildPending();
            lastRebuildPlayerX = pX;
            lastRebuildPlayerY = pY;
            lastRebuildPlayerZ = pZ;
            lastRebuildCameraX = cX;
            lastRebuildCameraY = cY;
            lastRebuildCameraZ = cZ;
            lastRebuildGeneration = generation;
            lastRebuildContextRevision = contextRevision;
            if (elementRebuild || revealRebuild) {
                // 差分を消費したのでリセットする。要素非アクティブ時の変更は残しておき、
                // 次に要素カリングが有効になった再構築でまとめて反映する。
                CULLER.clearPendingElementChange();
            }
            if (wideElementRebuild) {
                PerfMonitor.CHUNK_REBUILDS_WIDE.increment();
            }
        }
    }

    /**
     * 再構築要求ボックスを Embeddium の遅延キューへ流す。
     *
     * <p>IMPORTANT(同期)は描画スレッドを {@code awaitCompletion} と {@code tryStealTask} で
     * ブロックし、移動中のFPSを直撃するため使わない。代わりに {@code holdUploads} のとき
     * {@link #batchPending} を立て、{@link #shouldHoldUpload} 経由の Mixin がビルド完了
     * (キュー空かつワーカー停止)までアップロードを保留して断面を1フレームで確定させる。
     * 確定までは {@link #scheduleChunkRebuildIfNeeded} が次の再構築を積まない。
     */
    private static boolean scheduleChunkRebuildInternal(AABB box, boolean holdUploads) {
        if (!initialized) return false;

        long start = System.nanoTime();
        boolean scheduled = false;

        int minX = (int) box.minX;
        int minY = (int) box.minY;
        int minZ = (int) box.minZ;
        int maxX = (int) box.maxX;
        int maxY = (int) box.maxY;
        int maxZ = (int) box.maxZ;

        int sx = (maxX - minX) / 16 + 1;
        int sy = (maxY - minY) / 16 + 1;
        int sz = (maxZ - minZ) / 16 + 1;
        long sectionCount = (long) Math.max(sx, 1) * Math.max(sy, 1) * Math.max(sz, 1);

        try {
            Object renderer = instanceMethod.invoke(null);
            if (renderer == null) {
                LOGGER.debug("SodiumWorldRenderer instance is null");
            } else {
                rebuildMethod.invoke(renderer, minX, minY, minZ, maxX, maxY, maxZ, false);
                scheduled = true;
                if (holdUploads) {
                    collectBatchSections(minX, minY, minZ, maxX, maxY, maxZ);
                    batchPending = true;
                    batchFrames = 0;
                    batchScheduledAtMs = System.currentTimeMillis();
                } else {
                    batchPending = false;
                    batchSections.clear();
                }

                PerfMonitor.CHUNK_REBUILDS.increment();
                PerfMonitor.CHUNK_REBUILD_SECTIONS.add(sectionCount);
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("Cannot access Embeddium method: {}", e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Embeddium method invocation failed: {}",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to schedule chunk rebuild: {}", e.getMessage());
        }
        PerfMonitor.CHUNK_REBUILD.add(System.nanoTime() - start);

        return scheduled;
    }

    public static boolean isCulled(BlockPos pos) {
        return CULLER.isCulled(pos);
    }

    /** メッシュ構築ワーカー用。メインスレッドの level ではなくそのスレッドの WorldSlice で判定する。 */
    public static boolean isBlockCulled(BlockPos pos, net.minecraft.world.level.BlockGetter level) {
        return CULLER.isBlockCulled(pos, level);
    }

    /** メッシュ構築専用。復元フラッシュのメッシュ専用ホールドも尊重する。 */
    public static boolean isBlockCulledForMesh(BlockPos pos, net.minecraft.world.level.BlockGetter level) {
        return CULLER.isBlockCulledForMesh(pos, level);
    }

    private static void collectBatchSections(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        batchSections.clear();
        int minCx = minX >> 4;
        int minCy = minY >> 4;
        int minCz = minZ >> 4;
        int maxCx = maxX >> 4;
        int maxCy = maxY >> 4;
        int maxCz = maxZ >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    batchSections.add(SectionPos.asLong(cx, cy, cz));
                }
            }
        }
    }

    /** 確定待ちのバッチがあるか。RenderSectionManagerMixin が保留分を合流させる判断に使う。 */
    public static boolean hasPendingBatch() {
        return batchPending;
    }

    /** 指定セクションが現在のバッチに含まれるか(セクション座標)。 */
    public static boolean isBatchSection(int sectionX, int sectionY, int sectionZ) {
        return batchSections.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    /** 現在のバッチのセクション座標。Mixin が各セクションの構築完了を個別に判定するのに使う。 */
    public static LongOpenHashSet getBatchSections() {
        return batchSections;
    }

    /** バッチ開始からの経過フレーム。Mixin が投入待ちの猶予に使う。 */
    public static int getBatchFrames() {
        return batchFrames;
    }

    /**
     * RenderSectionManagerMixin が {@code uploadChunks} の先頭で毎フレーム呼ぶ。
     *
     * <p>結果が 1 つも無いフレームでは {@code processChunkBuildResults} が呼ばれないため、
     * 保留時間の計測をそこに置くと安全弁が動かず {@link #batchPending} が刺さり得る。
     * 毎フレーム必ず呼ばれるこの経路で計測し、上限を超えたら強制解放する。
     */
    public static void tickBatchHold() {
        if (!batchPending) {
            return;
        }
        if (++batchFrames >= MAX_UPLOAD_HOLD_FRAMES) {
            batchPending = false;
            batchFrames = 0;
        }
    }

    /**
     * RenderSectionManagerMixin から呼ぶ。true の間はバッチのセクションの結果を保留する。
     *
     * <p>再構築ボックスの各セクションはワーカーで並列に焼かれ、完了順にフレームを跨いで
     * アップロードされる。カリング断面がセクション境界を跨ぐと、片側だけ先に更新されて
     * 穴が見える。バッチが揃うまでバッチ分だけアップロードを保留し、まとめて1フレームで
     * 確定させることで、描画スレッドをブロックせずに同期再構築と同じ原子性を得る。
     * 無関係なアップロード(新規チャンクロード等)は保留しない。時間の打ち切りは
     * {@link #tickBatchHold} が行う。
     *
     * @param batchComplete バッチの全セクションが構築済みか
     */
    public static boolean shouldHoldUpload(boolean batchComplete) {
        return batchPending && !batchComplete;
    }

    /** バッチを確定する(保留分をアップロード済みにして次を受け付ける)。 */
    public static void commitBatch() {
        batchPending = false;
        batchFrames = 0;
        // メッシュが実際に更新された瞬間。復元帳簿の締め処理(実ブロックがメッシュに乗った
        // 確定でゴーストを消す)を行う。バッチのスケジュール時刻を渡し、ホールド失効前に
        // スケジュールされた(メッシュに実ブロックが乗っていない)確定では閉じない。
        CULLER.onMeshCommit(batchSections, batchScheduledAtMs);
        batchSections.clear();
    }

    public static void reset() {
        CULLER.reset();
        lastChunkRebuildTime = 0;
        commitBatch();
        resetLastRebuildCoords();
    }

    private static void resetLastRebuildCoords() {
        lastRebuildPlayerX = Integer.MIN_VALUE;
        lastRebuildPlayerY = Integer.MIN_VALUE;
        lastRebuildPlayerZ = Integer.MIN_VALUE;
        lastRebuildCameraX = Integer.MIN_VALUE;
        lastRebuildCameraY = Integer.MIN_VALUE;
        lastRebuildCameraZ = Integer.MIN_VALUE;
        lastRebuildGeneration = Long.MIN_VALUE;
        lastRebuildContextRevision = Long.MIN_VALUE;
    }

    public static void forceChunkRebuild(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options == null) {
            return;
        }

        CULLER.reset();
        lastChunkRebuildTime = 0;
        resetLastRebuildCoords();

        if (initializeReflection()) {
            Vec3 playerPos = mc.player.getEyePosition(1.0f);
            double renderDistance = mc.options.getEffectiveRenderDistance() * 16;
            AABB box = new AABB(
                playerPos.x - renderDistance, playerPos.y - 64, playerPos.z - renderDistance,
                playerPos.x + renderDistance, playerPos.y + 64, playerPos.z + renderDistance
            );
            scheduleChunkRebuildInternal(box, false);
            LOGGER.debug("Forced chunk rebuild on top-down disable");
        }
    }
}