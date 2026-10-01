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

    // カリング断面がセクションを跨ぐため、バッチの全セクションが揃うまでアップロードを保留する。
    // 保留対象はこのバッチのセクションだけに限定する(新規チャンクロード等の無関係な
    // アップロードを巻き込むと、画面外周のチャンクがアップロードされず穴になる)。
    // さらに、確定するまで次の再構築を積まないことで、バッチの割り込みを防ぐ。
    // これで f2a1eb9 の同期と同じ「同一フレーム確定」を描画スレッドをブロックせずに再現する。
    private static boolean batchPending = false;
    private static int batchFrames = 0;
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

        // バッチ確定まではカリング状態を凍結する。バッチ構築中にプレイヤー/カメラが動くと
        // セクション毎に別スナップショットで焼かれ、断面がずれて穴になる(f2a1eb9 は同期で
        // メインスレッドを止める=凍結でこれを防いでいた)。
        int frequency = CULLER.getFrequency();
        if (!batchPending && mc.player.tickCount % frequency == 0) {
            long tUpdate = System.nanoTime();
            CULLER.update();
            PerfMonitor.CULL_UPDATE.add(System.nanoTime() - tUpdate);
        }

        if (ModState.STATUS.isEnabled()) {
            scheduleChunkRebuildIfNeeded();
        }
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

        // 覆いカリングの時間差進行中は、プレイヤーが静止していても再構築して1つずつ消す。
        boolean coverReleasing = CULLER.hasActiveCoverRelease();
        // 屋内要素カリングは視点の回転だけで手前壁が変わる。世代が進んだら座標が同じでも再構築する。
        long generation = CULLER.getCullingGeneration();
        boolean generationChanged = generation != lastRebuildGeneration;
        if (!coverReleasing && !generationChanged
                && pX == lastRebuildPlayerX && pY == lastRebuildPlayerY && pZ == lastRebuildPlayerZ
                && cX == lastRebuildCameraX && cY == lastRebuildCameraY && cZ == lastRebuildCameraZ) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkRebuildTime < CHUNK_REBUILD_INTERVAL_MS) {
            return;
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
        AABB box = new AABB(playerPos, cameraPos).inflate(radiusH, radiusV, radiusH);
        if (coverReleasing) {
            // 覆いは円柱より広い。箱を覆い半径まで広げて該当セクションを再構築する。
            int coverRadius = Config.getCoverCullingRadius();
            box = box.inflate(
                    Math.max(0, coverRadius - radiusH),
                    Math.max(0, coverRadius - radiusV),
                    Math.max(0, coverRadius - radiusH));
        }
        boolean elementRebuild = generationChanged && CULLER.isIndoorElementActive();
        boolean wideElementRebuild = false;
        if (elementRebuild) {
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
            } else {
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
            lastRebuildPlayerX = pX;
            lastRebuildPlayerY = pY;
            lastRebuildPlayerZ = pZ;
            lastRebuildCameraX = cX;
            lastRebuildCameraY = cY;
            lastRebuildCameraZ = cZ;
            lastRebuildGeneration = generation;
            if (elementRebuild) {
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
        int sectionCount = Math.max(sx, 1) * Math.max(sy, 1) * Math.max(sz, 1);

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