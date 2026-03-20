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
            CULLER.update();
        }

        if (ModState.STATUS.isEnabled()) {
            scheduleChunkRebuildIfNeeded();
        }
    }

    private static void scheduleChunkRebuildIfNeeded() {
        if (!initializeReflection()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkRebuildTime < CHUNK_REBUILD_INTERVAL_MS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 cameraPos = ModState.CAMERA.getCameraPosition();

        if (!com.topdownview.state.CameraState.isPositionValid(cameraPos)) {
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

        if (scheduleChunkRebuildInternal(box)) {
            lastChunkRebuildTime = currentTime;
        }
    }

    private static boolean scheduleChunkRebuildInternal(AABB box) {
        if (!initialized) return false;

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

            return true;

        } catch (IllegalAccessException e) {
            LOGGER.error("Cannot access Embeddium method: {}", e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Embeddium method invocation failed: {}",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to schedule chunk rebuild: {}", e.getMessage());
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