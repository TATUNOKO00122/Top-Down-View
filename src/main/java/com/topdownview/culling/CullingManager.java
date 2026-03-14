package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
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

/**
 * カリングマネージャー
 * Embeddium連携でチャンク再構築を制御
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class CullingManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    private static final long CHUNK_REBUILD_INTERVAL_MS = 50;
    private static final String EMBEDDIUM_CLASS = "me.jellysquid.mods.sodium.client.SodiumClientMod";
    private static final String SODIUM_RENDERER_CLASS = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";

    private static Boolean embeddiumLoaded = null;
    private static long lastChunkRebuildTime = 0;
    private static Method instanceMethod = null;
    private static Method rebuildMethod = null;
    private static Class<?> rendererClass = null;

    private CullingManager() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean isEmbeddiumLoaded() {
        if (embeddiumLoaded == null) {
            try {
                Class.forName(EMBEDDIUM_CLASS);
                embeddiumLoaded = true;
                LOGGER.info("Embeddium detected");
            } catch (ClassNotFoundException e) {
                embeddiumLoaded = false;
                LOGGER.info("Embeddium not detected");
            }
        }
        return embeddiumLoaded;
    }

    private static boolean initializeReflection() {
        // 成功時のみキャッシュ（再試行を許可）
        if (instanceMethod != null && rebuildMethod != null) {
            return true;
        }

        try {
            rendererClass = Class.forName(SODIUM_RENDERER_CLASS);
            instanceMethod = rendererClass.getMethod("instance");
            rebuildMethod = rendererClass.getMethod(
                    "scheduleRebuildForBlockArea",
                    int.class, int.class, int.class,
                    int.class, int.class, int.class,
                    boolean.class);

            LOGGER.info("Embeddium reflection initialized successfully");
            return true;

        } catch (ClassNotFoundException e) {
            LOGGER.debug("SodiumWorldRenderer class not found (may load later): {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.warn("Required method not found in SodiumWorldRenderer: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize Embeddium reflection: {}", e.getMessage());
        }

        // 失敗時も再試行可能（フラグを設定しない）
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
            CULLER.update();
        }

        if (ModState.STATUS.isEnabled()) {
            scheduleChunkRebuildIfNeeded();
        }
    }

    private static void scheduleChunkRebuildIfNeeded() {
        if (!isEmbeddiumLoaded()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkRebuildTime < CHUNK_REBUILD_INTERVAL_MS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 cameraPos = ModState.CAMERA.getCameraPosition();

        if (cameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            return;
        }

        // モードに応じて円柱のサイズを選択
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

        if (scheduleChunkRebuildInternal(box)) {
            lastChunkRebuildTime = currentTime;
        }
    }

    private static boolean scheduleChunkRebuildInternal(AABB box) {
        if (!initializeReflection()) {
            return false;
        }

        try {
            Object renderer = instanceMethod.invoke(null);
            if (renderer == null) {
                LOGGER.debug("SodiumWorldRenderer instance is null");
                return false;
            }

            rebuildMethod.invoke(renderer,
                    (int) box.minX, (int) box.minY, (int) box.minZ,
                    (int) box.maxX, (int) box.maxY, (int) box.maxZ,
                    true);

            LOGGER.debug("Scheduled chunk rebuild for area: [{}, {}, {}] to [{}, {}, {}]",
                    (int) box.minX, (int) box.minY, (int) box.minZ,
                    (int) box.maxX, (int) box.maxY, (int) box.maxZ);

            return true;

        } catch (IllegalAccessException e) {
            LOGGER.warn("Cannot access Embeddium method: {}", e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOGGER.warn("Embeddium method invocation failed: {}",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Failed to schedule chunk rebuild: {}", e.getMessage());
        }

        return false;
    }

    public static boolean isCulled(BlockPos pos) {
        return CULLER.isCulled(pos);
    }

    public static void reset() {
        CULLER.reset();
        lastChunkRebuildTime = 0;
    }

    public static void forceChunkRebuild(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options == null) {
            return;
        }

        CULLER.reset();
        lastChunkRebuildTime = 0;

        if (isEmbeddiumLoaded() && initializeReflection()) {
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
