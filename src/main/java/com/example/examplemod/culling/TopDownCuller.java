package com.example.examplemod.culling;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * トップダウンビュー用カリング実装 - Dungeons Perspective方式（軽量化版）
 * 
 * 設計方針：
 * 1. ベクトル内積方式 - レイキャストより高速
 * 2. ブロック単位判定 - 面単位より呼び出し回数を減らす
 * 3. 高さフィルタ - プレイヤー頭上のみを対象
 * 4. 距離・角度による簡易判定
 * 5. ブロックごとキャッシュ - 同じブロックの再計算を防止
 * 
 * スレッドセーフ設計：
 * Chunk Render Task Executorは複数スレッドで動作するため、
 * ConcurrentHashMapを使用してスレッドセーフを確保
 */
public final class TopDownCuller implements Culler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final TopDownCuller INSTANCE = new TopDownCuller();

    // 最大カリング距離
    private static final double MAX_CULLING_DISTANCE = 48.0;
    // 高さオフセット（プレイヤー高さ+この値以上を対象）
    private static final double HEIGHT_OFFSET = 1.5;
    // 円柱半径（プレイヤー-カメラ線分からの水平距離）
    private static final double CYLINDER_RADIUS = 5.0;
    // 更新頻度（毎tick更新 - Dungeons Perspective方式）
    private static final int UPDATE_FREQUENCY = 1;
    // キャッシュ最大サイズ（ConcurrentHashMapの負荷を考慮して小さめに）
    private static final int MAX_CACHE_SIZE = 8000;
    // キャッシュ有効時間（ミリ秒）- 少し長めに
    private static final long CACHE_DURATION_MS = 150;

    private final Minecraft mc = Minecraft.getInstance();

    // カリング対象ブロックのキャッシュ
    // ConcurrentHashMapを使用してマルチスレッドアクセスに対応
    // Key: ブロック位置, Value: カリング結果（true=カリング対象）
    private final ConcurrentHashMap<BlockPos, Boolean> cullingCache = new ConcurrentHashMap<>(1000);
    
    // キャッシュのタイムスタンプ
    private volatile long cacheTimestamp = 0;
    // 前回のプレイヤー位置（キャッシュ無効化判定用）
    private volatile Vec3 lastPlayerPos = Vec3.ZERO;
    // 前回のカメラ位置
    private volatile Vec3 lastCameraPos = Vec3.ZERO;

    private TopDownCuller() {}

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    @Override
    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    @Override
    public boolean isCulled(BlockPos pos) {
        return isBlockCulled(pos);
    }

    /**
     * ブロックがカリング対象かどうかを判定（高速化版）
     * 
     * Dungeons Perspective方式の最適化：
     * 1. 高さチェックを最初に（最も軽い判定）
     * 2. 距離チェック（軽量）
     * 3. ベクトル内積による角度判定
     * 4. キャッシュを最大限活用
     */
    public boolean isBlockCulled(BlockPos pos) {
        if (!ClientForgeEvents.isTopDownView()) {
            if (!cullingCache.isEmpty()) {
                cullingCache.clear();
            }
            return false;
        }

        if (mc.player == null || mc.level == null) {
            return false;
        }

        // プレイヤー位置は除外
        BlockPos playerBlockPos = mc.player.blockPosition();
        if (pos.equals(playerBlockPos) || pos.equals(playerBlockPos.above())) {
            return false;
        }

        // キャッシュチェック
        Boolean cached = getCachedResult(pos);
        if (cached != null) {
            return cached;
        }

        // ブロックが空気か液体かチェック（装飾ブロックもカリング対象に含める）
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cacheResult(pos, false);
            return false;
        }

        // プレイヤーとカメラの位置を取得
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 cameraPos = ClientForgeEvents.getCameraPosition();

        if (cameraPos == null) {
            cacheResult(pos, false);
            return false;
        }

        // 高さチェック：プレイヤー頭上のみを対象（最も軽い判定を先に）
        double blockY = pos.getY() + 0.5;
        double playerY = mc.player.getY();
        if (blockY <= playerY + HEIGHT_OFFSET) {
            cacheResult(pos, false);
            return false;
        }

        // 距離が遠すぎる場合はカリングしない
        double distanceToPlayer = pos.distToCenterSqr(playerPos);
        if (distanceToPlayer > MAX_CULLING_DISTANCE * MAX_CULLING_DISTANCE) {
            cacheResult(pos, false);
            return false;
        }

        // 円柱カリング：プレイヤー-カメラ間の線分を中心軸とする円柱内のブロックのみをカリング
        boolean isCulled = shouldCullByVector(pos, playerPos, cameraPos);
        
        cacheResult(pos, isCulled);
        return isCulled;
    }

    /**
     * キャッシュから結果を取得
     * プレイヤー/カメラが移動した場合はキャッシュを無効化
     */
    private Boolean getCachedResult(BlockPos pos) {
        long currentTime = System.currentTimeMillis();
        
        // プレイヤーまたはカメラが移動したらキャッシュをクリア
        Vec3 currentPlayerPos = mc.player != null ? mc.player.getEyePosition(1.0f) : Vec3.ZERO;
        Vec3 currentCameraPos = ClientForgeEvents.getCameraPosition();
        
        if (currentCameraPos == null) {
            return null;
        }
        
        // 位置が変わった、またはキャッシュ期限切れの場合はクリア
        // しきい値を大きくして頻繁なキャッシュクリアを防止
        boolean playerMoved = currentPlayerPos.distanceToSqr(lastPlayerPos) > 0.25;
        boolean cameraMoved = currentCameraPos.distanceToSqr(lastCameraPos) > 0.25;
        boolean cacheExpired = (currentTime - cacheTimestamp) > CACHE_DURATION_MS;
        
        if (playerMoved || cameraMoved || cacheExpired) {
            cullingCache.clear();
            lastPlayerPos = currentPlayerPos;
            lastCameraPos = currentCameraPos;
            cacheTimestamp = currentTime;
            return null;
        }
        
        return cullingCache.get(pos);
    }

    /**
     * 結果をキャッシュ
     * サイズ制限を超えた場合は古いエントリを削除
     */
    private void cacheResult(BlockPos pos, boolean result) {
        // サイズ制限を超えている場合はクリア
        if (cullingCache.size() >= MAX_CACHE_SIZE) {
            cullingCache.clear();
        }
        cullingCache.put(pos.immutable(), result);
    }

    /**
     * 円柱カリング判定
     * プレイヤー-カメラ線分を中心軸とする円柱内のブロックをカリング
     */
    private boolean shouldCullByVector(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        // 円柱の中心軸（プレイヤー→カメラ線分）とブロックの距離を計算
        Vec3 axis = cameraPos.subtract(playerPos);
        double axisLengthSqr = axis.lengthSqr();
        
        // プレイヤーとカメラが同じ位置の場合はカリングしない
        if (axisLengthSqr < 0.0001) {
            return false;
        }
        
        // 線分上の最近接点パラメータtを計算
        Vec3 toBlock = blockCenter.subtract(playerPos);
        double t = toBlock.dot(axis) / axisLengthSqr;
        
        // 線分の外側にある場合はカリング対象外
        if (t < 0.0 || t > 1.0) {
            return false;
        }
        
        // 最近接点を計算
        Vec3 closestPoint = playerPos.add(axis.scale(t));
        
        // 水平距離（Y座標を無視）で円柱判定
        double dx = blockCenter.x - closestPoint.x;
        double dz = blockCenter.z - closestPoint.z;
        double horizontalDistSqr = dx * dx + dz * dz;
        
        // 円柱半径内ならカリング対象
        return horizontalDistSqr <= CYLINDER_RADIUS * CYLINDER_RADIUS;
    }

    /**
     * Dungeons Perspective方式の簡易判定
     * ベクトル内積で角度を計算（レイキャストより高速）
     * 
     * @param pos ブロック位置
     * @param playerPos プレイヤー位置
     * @param cameraPos カメラ位置
     * @return カリング対象ならtrue
     */
    private boolean shouldCullByDotProduct(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        // Dungeons Perspective方式：ブロック→プレイヤー と カメラ→プレイヤー の内積
        // 両方が同じ方向からプレイヤーを見ている = プレイヤーとカメラの間にある
        Vec3 fromBlock = playerPos.subtract(blockCenter).normalize();
        Vec3 fromCamera = playerPos.subtract(cameraPos).normalize();
        
        // 内積を計算（cosθ）
        double dotProduct = fromBlock.dot(fromCamera);
        
        // cosθ > 0.71 (約45度以内) ならカリング対象
        // Dungeons Perspectiveと同じ閾値
        if (dotProduct > 0.71) {
            return true;
        }
        
        // カメラから3ブロック以内もカリング対象
        double distToCamera = blockCenter.distanceToSqr(cameraPos);
        return distToCamera < 9.0; // 3^2 = 9
    }

    @Override
    public void update() {
        // トップダウンビューが無効になった場合はキャッシュをクリア
        if (!ClientForgeEvents.isTopDownView() && !cullingCache.isEmpty()) {
            cullingCache.clear();
        }
    }

    @Override
    public void reset() {
        cullingCache.clear();
        lastPlayerPos = Vec3.ZERO;
        lastCameraPos = Vec3.ZERO;
        cacheTimestamp = 0;
    }

    /**
     * カリング対象ブロック数を取得（デバッグ用）
     */
    public int getCulledBlockCount() {
        int count = 0;
        for (boolean isCulled : cullingCache.values()) {
            if (isCulled) count++;
        }
        return count;
    }

    /**
     * キャッシュサイズを取得（デバッグ用）
     */
    public int getCacheSize() {
        return cullingCache.size();
    }

    /**
     * キャッシュを参照のみ（計算なし）
     * BlockOcclusionCacheMixinからの高速アクセス用
     * 
     * @param pos ブロック位置
     * @return カリング対象ならtrue、未計算/非対象ならfalse
     */
    public boolean isBlockCulledCached(BlockPos pos) {
        if (!ClientForgeEvents.isTopDownView()) {
            return false;
        }
        
        Boolean cached = cullingCache.get(pos);
        return cached != null && cached;
    }
}
