package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.culling.geometry.BlockChangeBox;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.spatial.BlockMap;
import com.topdownview.state.ModState;
import com.topdownview.util.PerfMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

    // 円柱境界の隣接セクションを同一フレームで確定させるためのフラグ。
    // RenderSectionManagerMixin が allowImportantRebuilds() を true に上書きする。
    private static volatile boolean forceImportantRebuild = false;

    private static boolean initialized = false;
    private static boolean initializationFailed = false;
    private static long lastChunkRebuildTime = 0;
    private static Method instanceMethod = null;
    private static Method rebuildMethod = null;
    private static Method rebuildChunkMethod = null;

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
            rebuildChunkMethod = rendererClass.getMethod(
                    "scheduleRebuildForChunk",
                    int.class, int.class, int.class, boolean.class);
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

    private static void scheduleChunkRebuildIfNeeded() {
        if (!initializeReflection()) {
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

        boolean plainCylinder = !ModState.STATUS.isMiningMode() && !coverReleasing && !elementRebuild;

        if (scheduleChunkRebuildInternal(box, false)) {
            double movement;
            if (lastRebuildPlayerX == Integer.MIN_VALUE) {
                movement = Double.MAX_VALUE;
            } else {
                movement = Math.max(
                        Math.abs(pX - lastRebuildPlayerX) + Math.abs(pY - lastRebuildPlayerY) + Math.abs(pZ - lastRebuildPlayerZ),
                        Math.abs(cX - lastRebuildCameraX) + Math.abs(cY - lastRebuildCameraY) + Math.abs(cZ - lastRebuildCameraZ));
            }

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

            // 円柱境界を横切るセクションだけを重要(同期)に格上げし、隣接セクションを同一フレームで
            // 確定させて継ぎ目の1フレーム穴を消す。全体ボックスは上で遅延として流してある。
            if (plainCylinder) {
                scheduleCylinderShell(box, movement);
            }
        }
    }

    private static boolean scheduleChunkRebuildInternal(AABB box, boolean important) {
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
                forceImportantRebuild = important;
                try {
                    rebuildMethod.invoke(renderer, minX, minY, minZ, maxX, maxY, maxZ, important);
                } finally {
                    forceImportantRebuild = false;
                }
                scheduled = true;

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

    /**
     * 円柱境界を横切るセクション（境界シェル）だけを重要(同期)再構築として登録する。
     *
     * <p>ボックス全体を同期するとフレームが止まる（半径10で約40セクション）。実際にカリング状態が
     * 変わるのは境界を跨ぐセクションだけなので、そこだけ同期して継ぎ目の1フレーム穴を消しつつ
     * 同期コストを数〜十数セクションに抑える。全体ボックスは別途遅延で流しておく。
     *
     * @param movement 前回再構築からの移動量(ブロック)。境界がこのぶん動いた分をバンドに含める。
     */
    private static void scheduleCylinderShell(AABB box, double movement) {
        if (rebuildChunkMethod == null || movement > 16.0) {
            return;
        }

        double cameraX = Math.floor(ModState.CAMERA.getCameraX()) + 0.5;
        double cameraY = Math.floor(ModState.CAMERA.getCameraY()) + 0.5;
        double cameraZ = Math.floor(ModState.CAMERA.getCameraZ()) + 0.5;

        // 正規化距離^2 の境界 d²=1 付近の勾配 |∇d²|≤2/rMin を考慮し、movement ブロック分の余裕を
        // d² 空間のバンドへ変換する。+2 は隣接面が現れる1ブロックと余裕。
        double rMin = Math.max(1.0, Math.min(
                Math.min(Config.getCylinderRadiusHorizontal(), Config.getCylinderRadiusVertical()), 16.0));
        double bandSq = 2.0 * (movement + 2.0) / rMin;

        int minCx = ((int) Math.floor(box.minX)) >> 4;
        int minCy = ((int) Math.floor(box.minY)) >> 4;
        int minCz = ((int) Math.floor(box.minZ)) >> 4;
        int maxCx = ((int) Math.floor(box.maxX)) >> 4;
        int maxCy = ((int) Math.floor(box.maxY)) >> 4;
        int maxCz = ((int) Math.floor(box.maxZ)) >> 4;

        try {
            Object renderer = instanceMethod.invoke(null);
            if (renderer == null) {
                return;
            }
            forceImportantRebuild = true;
            try {
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cy = minCy; cy <= maxCy; cy++) {
                        for (int cz = minCz; cz <= maxCz; cz++) {
                            if (sectionStraddlesCylinder(cx, cy, cz, cameraX, cameraY, cameraZ, bandSq)) {
                                rebuildChunkMethod.invoke(renderer, cx, cy, cz, true);
                                PerfMonitor.SHELL_REBUILD_SECTIONS.increment();
                            }
                        }
                    }
                }
            } finally {
                forceImportantRebuild = false;
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("Cannot access Embeddium method: {}", e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Embeddium method invocation failed: {}",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to schedule cylinder shell rebuild: {}", e.getMessage());
        }
    }

    /** セクションの8隅(ブロック中心)が円柱境界 d²=1 の前後 bandSq 以内に入るか。 */
    private static boolean sectionStraddlesCylinder(int cx, int cy, int cz,
            double cameraX, double cameraY, double cameraZ, double bandSq) {
        double x0 = (cx << 4) + 0.5;
        double y0 = (cy << 4) + 0.5;
        double z0 = (cz << 4) + 0.5;
        double minValid = Double.POSITIVE_INFINITY;
        double maxAll = Double.NEGATIVE_INFINITY;

        for (int ix = 0; ix < 2; ix++) {
            double x = ix == 0 ? x0 : x0 + 15.0;
            for (int iy = 0; iy < 2; iy++) {
                double y = iy == 0 ? y0 : y0 + 15.0;
                for (int iz = 0; iz < 2; iz++) {
                    double z = iz == 0 ? z0 : z0 + 15.0;
                    double d = CylinderCalculator.getNormalizedDistanceSq(x, y, z, cameraX, cameraY, cameraZ);
                    if (d < 0.0) {
                        // 軸の端(キャップ)外。キャップ境界のセクションも拾うため最大側は無限大とする。
                        maxAll = Double.POSITIVE_INFINITY;
                    } else {
                        if (d < minValid) {
                            minValid = d;
                        }
                        if (d > maxAll) {
                            maxAll = d;
                        }
                    }
                }
            }
        }

        return minValid <= 1.0 + bandSq && maxAll >= 1.0 - bandSq;
    }

    /** RenderSectionManagerMixin 用。true の間は Embeddium が重要(同期)再構築を行う。 */
    public static boolean isForceImportantRebuild() {
        return forceImportantRebuild;
    }

    public static boolean isCulled(BlockPos pos) {
        return CULLER.isCulled(pos);
    }

    public static void reset() {
        CULLER.reset();
        lastChunkRebuildTime = 0;
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