package com.topdownview.culling;

import com.topdownview.TopDownViewMod;
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
 * カリング更新とチャンク再構築を制御
 * パフォーマンス最適化：
 * - チャンク再構築を間引き
 * - カリング判定を間引き（tick単位）
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class CullingManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    // チャンク再構築の最小間隔（ミリ秒）- より頻繁に更新
    private static final long CHUNK_REBUILD_INTERVAL_MS = 50;
    // Embeddiumのクラス名
    private static final String EMBEDDIUM_CLASS = "me.jellysquid.mods.sodium.client.SodiumClientMod";
    private static final String SODIUM_RENDERER_CLASS = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";

    // Embeddiumの存在確認（遅延初期化）
    private static Boolean embeddiumLoaded = null;
    // 前回のチャンク再構築時刻
    private static long lastChunkRebuildTime = 0;
    // リフレクションで取得したメソッド（キャッシュ）
    private static Method instanceMethod = null;
    private static Method rebuildMethod = null;
    private static Class<?> rendererClass = null;
    private static boolean reflectionInitialized = false;

    private CullingManager() {
        throw new AssertionError("ユーティリティクラス");
    }

    /**
     * Embeddiumが読み込まれているか確認
     */
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

    /**
     * リフレクションを初期化
     * メソッドシグネチャを検証し、安全にアクセス
     */
    private static boolean initializeReflection() {
        if (reflectionInitialized) {
            return instanceMethod != null && rebuildMethod != null;
        }

        try {
            rendererClass = Class.forName(SODIUM_RENDERER_CLASS);

            // instance() メソッドを取得
            instanceMethod = rendererClass.getMethod("instance");

            // scheduleRebuildForBlockArea メソッドを取得
            rebuildMethod = rendererClass.getMethod(
                    "scheduleRebuildForBlockArea",
                    int.class, int.class, int.class,
                    int.class, int.class, int.class,
                    boolean.class);

            LOGGER.info("Embeddium reflection initialized successfully");
            reflectionInitialized = true;
            return true;

        } catch (ClassNotFoundException e) {
            LOGGER.debug("SodiumWorldRenderer class not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.warn("Required method not found in SodiumWorldRenderer: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize Embeddium reflection: {}", e.getMessage());
        }

        reflectionInitialized = true;
        return false;
    }

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        tickCounter++;
        if (tickCounter % 100 == 0) {
            LOGGER.info("[TopDownView] CullingManager tick {}, isTopDownView={}, culledBlocks={}, cacheSize={}",
                    tickCounter,
                    com.topdownview.client.ClientForgeEvents.isTopDownView(),
                    CULLER.getCulledBlockCount(),
                    CULLER.getCacheSize());
        }

        // 頻度制御：player.tickCount % frequency == 0 の時のみ更新
        int frequency = CULLER.getFrequency();
        if (mc.player.tickCount % frequency == 0) {
            // カリング状態を更新
            CULLER.update();
        }

        // トップダウンビューが有効な場合はチャンクを再構築
        if (com.topdownview.client.ClientForgeEvents.isTopDownView()) {
            scheduleChunkRebuildIfNeeded();
        }
    }

    /**
     * 必要に応じてチャンクを再構築
     * 間引きを適用してパフォーマンスを最適化
     */
    private static void scheduleChunkRebuildIfNeeded() {
        if (!isEmbeddiumLoaded()) {
            return;
        }

        // 間引きチェック
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkRebuildTime < CHUNK_REBUILD_INTERVAL_MS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 cameraPos = com.topdownview.client.ClientForgeEvents.getCameraPosition();

        if (cameraPos == null) {
            return;
        }

        // プレイヤーとカメラの間の範囲を計算
        AABB box = new AABB(playerPos, cameraPos).inflate(1, 0, 1);
        double distance = playerPos.distanceTo(cameraPos);
        box = box.inflate(distance, distance, distance);

        // リフレクションを使用してチャンク再構築
        if (scheduleChunkRebuildInternal(box)) {
            lastChunkRebuildTime = currentTime;
        }
    }

    /**
     * リフレクションを使用してチャンク再構築をスケジュール
     */
    private static boolean scheduleChunkRebuildInternal(AABB box) {
        if (!initializeReflection()) {
            return false;
        }

        try {
            // SodiumWorldRendererのインスタンスを取得
            Object renderer = instanceMethod.invoke(null);
            if (renderer == null) {
                LOGGER.debug("SodiumWorldRenderer instance is null");
                return false;
            }

            // チャンク再構築をスケジュール
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

    /**
     * ブロックがカリング対象か判定
     */
    public static boolean isCulled(BlockPos pos) {
        return CULLER.isCulled(pos);
    }

    /**
     * カリングをリセット
     */
    public static void reset() {
        CULLER.reset();
        lastChunkRebuildTime = 0;
    }
}
