package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.culling.geometry.BlockChangeBox;
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
            if (!pending.isEmpty() && !CULLER.hasConeChangePending()) {
                // 壁パネル/天井スライスの差分セルだけを再構築する。集合の変化は通常数ブロック
                // なので、探索キャッシュ全域(RADIUS_XZ)を再構築するより大幅に軽い。
                box = new AABB(
                        Math.min(box.minX, pending.getMinX()),
                        Math.min(box.minY, pending.getMinY()),
                        Math.min(box.minZ, pending.getMinZ()),
                        Math.max(box.maxX, pending.getMaxX() + 1.0),
                        Math.max(box.maxY, pending.getMaxY() + 1.0),
                        Math.max(box.maxZ, pending.getMaxZ() + 1.0));
            } else {
                // コーン変化や差分不明時のみ探索キャッシュ全域へ広げる。
                wideElementRebuild = true;
                int revealRadius = BlockMap.RADIUS_XZ;
                box = box.inflate(
                        Math.max(0, revealRadius - radiusH),
                        Math.max(0, revealRadius - radiusV),
                        Math.max(0, revealRadius - radiusH));
            }
        }

        if (scheduleChunkRebuildInternal(box)) {
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

    private static boolean scheduleChunkRebuildInternal(AABB box) {
        if (!initialized) return false;

        long start = System.nanoTime();
        boolean scheduled = false;
        try {
            Object renderer = instanceMethod.invoke(null);
            if (renderer == null) {
                LOGGER.debug("SodiumWorldRenderer instance is null");
            } else {
                rebuildMethod.invoke(renderer,
                        (int) box.minX, (int) box.minY, (int) box.minZ,
                        (int) box.maxX, (int) box.maxY, (int) box.maxZ,
                        true);
                scheduled = true;

                // 再構築規模の目安として要求ボックスのセクション数を積算する。
                int sx = ((int) box.maxX - (int) box.minX) / 16 + 1;
                int sy = ((int) box.maxY - (int) box.minY) / 16 + 1;
                int sz = ((int) box.maxZ - (int) box.minZ) / 16 + 1;
                PerfMonitor.CHUNK_REBUILDS.increment();
                PerfMonitor.CHUNK_REBUILD_SECTIONS.add((long) Math.max(sx, 1) * Math.max(sy, 1) * Math.max(sz, 1));
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
            scheduleChunkRebuildInternal(box);
            LOGGER.debug("Forced chunk rebuild on top-down disable");
        }
    }
}