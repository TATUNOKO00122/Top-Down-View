package com.topdownview.culling;

import com.topdownview.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;

/**
 * トップダウンビュー用カリング実装 - Dungeons Perspective方式 (スレッドセーフ版)
 */
public final class TopDownCuller implements Culler {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    // カリング計算用のカメラ距離（Configから取得）
    private static double get_culling_camera_distance() {
        return com.topdownview.Config.cullingRange;
    }

    // カリング角度しきい値（約10度: cosθ ≈ 0.9848）
    private static final double CULLING_ANGLE_COS = 0.9848;
    // プレイヤー背面（奥側）のカリング停止距離
    private static final double BACK_SIDE_CULL_LIMIT = 3.0;
    // 更新頻度
    private static final int UPDATE_FREQUENCY = 1;
    // キャッシュ最大サイズ
    private static final int MAX_CACHE_SIZE = 8000;
    // キャッシュ有効時間
    private static final long CACHE_DURATION_MS = 150;

    // スレッドセーフなデータ保持（メインスレッドで更新）
    private volatile Vec3 currentPlayerPos = Vec3.ZERO;
    private volatile Vec3 currentCameraPos = Vec3.ZERO;

    // カリング対象ブロックのキャッシュ
    private final ConcurrentHashMap<BlockPos, Boolean> cullingCache = new ConcurrentHashMap<>(1000);
    // キャッシュのタイムスタンプ
    private volatile long cacheTimestamp = 0;

    private TopDownCuller() {
    }

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    @Override
    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    @Override
    public boolean isCulled(BlockPos pos) {
        // エンティティ描画用などのメインスレッドアクセス
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        return isBlockCulled(pos, mc.level);
    }

    /**
     * ブロックがカリング対象かどうかを判定
     */
    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ClientForgeEvents.isTopDownView()) {
            if (!cullingCache.isEmpty()) {
                cullingCache.clear();
            }
            return false;
        }

        if (level == null) {
            return false;
        }

        // キャッシュチェック（簡易版）
        long currentTime = System.currentTimeMillis();
        if ((currentTime - cacheTimestamp) > CACHE_DURATION_MS) {
            cullingCache.clear();
            cacheTimestamp = currentTime;
        } else {
            Boolean cached = cullingCache.get(pos);
            if (cached != null)
                return cached;
        }

        // ブロック状態取得
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cacheResult(pos, false);
            return false;
        }

        // 特殊ブロック除外（常に表示）
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.LadderBlock) {
            cacheResult(pos, false);
            return false;
        }

        // 位置取得（スレッドセーフ）
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            cacheResult(pos, false);
            return false;
        }

        // チェスト・ドア除外（プレイヤーの足元から4ブロック以内）
        if (block instanceof net.minecraft.world.level.block.ChestBlock ||
                block instanceof net.minecraft.world.level.block.EnderChestBlock ||
                block instanceof net.minecraft.world.level.block.TrappedChestBlock ||
                block instanceof net.minecraft.world.level.block.DoorBlock) {
            if (pos.getY() <= pPos.y - 1.5 + 4.0) {
                cacheResult(pos, false);
                return false;
            }
        }

        // 判定
        boolean isCulled = shouldCullByVector(pos, level, pPos, cPos);
        cacheResult(pos, isCulled);
        return isCulled;
    }

    private void cacheResult(BlockPos pos, boolean result) {
        if (cullingCache.size() >= MAX_CACHE_SIZE) {
            cullingCache.clear();
        }
        cullingCache.put(pos.immutable(), result);
    }

    private boolean shouldCullByVector(BlockPos pos, BlockGetter level, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 cameraDirection = playerPos.subtract(cameraPos).normalize();
        double cullingDist = get_culling_camera_distance();
        Vec3 virtualCameraPos = playerPos.subtract(cameraDirection.scale(cullingDist));

        Vec3 viewAxis = cameraDirection;
        double distToPlayer = cullingDist;

        Vec3 vCameraToBlock = blockCenter.subtract(virtualCameraPos);
        double distToBlock = vCameraToBlock.length();
        Vec3 blockDir = vCameraToBlock.normalize();

        double cosTheta = viewAxis.dot(blockDir);

        if (cosTheta < CULLING_ANGLE_COS) {
            return false;
        }

        // プレイヤーからブロックへのベクトル
        Vec3 playerToBlock = blockCenter.subtract(playerPos);

        // 高さチェック用の相対座標（プレイヤーの足元を0とした高さ）
        double relativeHeight = (pos.getY() + 0.5) - (playerPos.y - 1.5);

        // 手前(Near Side)か奥(Far Side)かの判定を「水平距離」ベースに変更
        Vec3 horizontalViewDir = new Vec3(viewAxis.x, 0, viewAxis.z).normalize();
        double horizontalOffset = playerToBlock.x * horizontalViewDir.x + playerToBlock.z * horizontalViewDir.z;

        // 判定：プレイヤーより少し手前（-0.2ブロック）以上なら、進行方向の「奥側(Far Side)」とする
        boolean isNearSide = horizontalOffset < -0.2;

        if (isNearSide) {
            // プレイヤーより手前（カメラ側）：視界を遮るため高さで判定
            return relativeHeight > com.topdownview.Config.cullingHeightThreshold;
        } else {
            // プレイヤーより奥（進行方向/背面側）：接地判定ロジックを適用

            // 1. プレイヤーより大幅に奥にあるブロックは除外（背景の維持）
            double projDist = distToBlock * cosTheta;
            if (projDist > distToPlayer + BACK_SIDE_CULL_LIMIT) {
                return false;
            }

            // 2. 接地判定ベースのカリング
            if (relativeHeight > com.topdownview.Config.cullingHeightThreshold) {
                // 足元より高いブロックは、真下が空気なら「浮いている（天井/梁）」とみなしてカリング
                // 真下が固体なら「山/壁/地形」とみなして表示を維持
                return level.getBlockState(pos.below()).isAir();
            } else {
                // 低所（足元以下）：地形などは常に表示
                return false;
            }
        }
    }

    @Override
    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        Vec3 pPos = mc.player.getEyePosition(1.0f);
        Vec3 cPos = ClientForgeEvents.getCameraPosition();

        if (cPos == null) {
            cPos = Vec3.ZERO;
        }

        // 位置が大きく変わったらキャッシュクリア
        if (pPos.distanceToSqr(this.currentPlayerPos) > 0.05 || cPos.distanceToSqr(this.currentCameraPos) > 0.05) {
            cullingCache.clear();
            cacheTimestamp = System.currentTimeMillis();
        }

        this.currentPlayerPos = pPos;
        this.currentCameraPos = cPos;
    }

    @Override
    public void reset() {
        cullingCache.clear();
        this.currentPlayerPos = Vec3.ZERO;
        this.currentCameraPos = Vec3.ZERO;
        cacheTimestamp = 0;
    }

    public boolean isBlockCulledCached(BlockPos pos) {
        if (!ClientForgeEvents.isTopDownView())
            return false;
        Boolean cached = cullingCache.get(pos);
        return cached != null && cached;
    }

    public int getCulledBlockCount() {
        return (int) cullingCache.values().stream().filter(b -> b).count();
    }

    public int getCacheSize() {
        return cullingCache.size();
    }
}
