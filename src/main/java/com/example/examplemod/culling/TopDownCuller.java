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
 * トップダウンビュー用カリング実装 - Dungeons Perspective方式
 * 
 * Dungeons Perspective方式のアルゴリズム：
 * 1. カメラ→プレイヤー方向（視線方向）とカメラ→ブロック方向の内積を計算
 * 2. cosθ > 閾値（約45度以内）で視線方向にあるブロックを判定
 * 3. ブロックがプレイヤーより手前（カメラ側）にある場合のみカリング
 * 4. 結果：プレイヤーの奥にある壁は表示され、手前の壁はカリングされる
 * 
 * 設計方針：
 * - ベクトル内積方式（acos不要）- Dungeons Perspectiveと同じ計算方式
 * - ブロック単位判定 - 面単位より呼び出し回数を減らす
 * - 高さフィルタ - プレイヤー頭上のみを対象
 * - ブロックごとキャッシュ - 同じブロックの再計算を防止
 * 
 * スレッドセーフ設計：
 * Chunk Render Task Executorは複数スレッドで動作するため、
 * ConcurrentHashMapを使用してスレッドセーフを確保
 */
public final class TopDownCuller implements Culler {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    // 最大カリング距離（プレイヤー中心 半径5ブロック = 直径10ブロック）
    private static final double MAX_CULLING_DISTANCE = 5.0;
    // 高さオフセット（プレイヤー高さ+この値以上を対象）- プレイヤー奥側用
    private static final double HEIGHT_OFFSET = 1.5;
    // 近距離時の高さオフセット - プレイヤーカメラ側（手前）用
    private static final double HEIGHT_OFFSET_NEAR = 1.0;
    // 角度閾値（cos 25° ≈ 0.906）- より視線に近いブロックに絞る
    private static final double ANGLE_THRESHOLD = 0.91;
    // 近距離判定用：カメラからの距離（ブロック単位）
    private static final double PROXIMITY_DISTANCE = 3.0;
    private static final double PROXIMITY_RADIUS_SQR = 2.0 * 2.0; // ニアシリンダーの半径
    // カメラからの近距離カリング閾値（ブロック単位）
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

        // 円状+視線ベクトルカリング：半径5ブロックの円内かつカメラ視線上のブロックをカリング
        // 高さチェックも内部で行う（プレイヤー距離に応じた閾値調整あり）
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
     * 円錐+円柱ハイブリッドカリング：視線ベクトル周囲の一定範囲をカリング
     *
     * 改善内容：
     * 1. カメラ近傍 (PROXIMITY_DISTANCE) では角度を無視し、軸近傍のブロックを無条件カリング
     * 2. 遠方では角度（円錐）で絞りつつ、最大半径（MAX_CULLING_DISTANCE=5.0）で広がりを制限
     */
    private boolean shouldCullByVector(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // カメラからプレイヤーへのベクトル（中心軸）
        Vec3 toPlayer = playerPos.subtract(cameraPos);
        double distToPlayerSq = toPlayer.lengthSqr();

        if (distToPlayerSq < 0.0001) {
            return false;
        }

        // カメラからブロックへのベクトル
        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double distToBlockSq = toBlock.lengthSqr();
        double distToBlock = Math.sqrt(distToBlockSq);

        // 距離チェック：プレイヤーより奥にあるブロックは除外
        if (distToBlockSq > distToPlayerSq + 2.25) {
            return false;
        }

        // 軸（視線ベクトル）への投影と距離の計算
        double dot = toBlock.dot(toPlayer);
        double t = dot / distToPlayerSq;

        // カメラより手前（t < 0）の場合は除外
        if (t < 0.0)
            return false;

        // 軸からの最短距離を計算
        Vec3 projection = cameraPos.add(toPlayer.scale(t));
        double distFromAxisSq = blockCenter.distanceToSqr(projection);

        // --- 判定ロジック ---
        boolean matchesRange = false;

        if (distToBlock < PROXIMITY_DISTANCE) {
            // 改善1: カメラ至近距離（ニアシリンダー）
            // 角度がつきすぎて消えにくいカメラ直下の壁を半径指定で消す
            matchesRange = distFromAxisSq < PROXIMITY_RADIUS_SQR;
        } else {
            // 改善2: 角度判定 + 最大半径制限
            double cosTheta = dot / (distToBlock * Math.sqrt(distToPlayerSq));
            // 角度が視線に近く（円錐）、かつ広がりすぎていない（円柱）こと
            matchesRange = (cosTheta > ANGLE_THRESHOLD)
                    && (distFromAxisSq < MAX_CULLING_DISTANCE * MAX_CULLING_DISTANCE);
        }

        if (!matchesRange) {
            return false;
        }

        // --- 高さチェック（床の消失防止） ---
        double heightOffset = (t < 1.0) ? HEIGHT_OFFSET_NEAR : HEIGHT_OFFSET;
        double blockY = pos.getY() + 0.5;
        double playerY = playerPos.y - 1.5;

        return blockY > playerY + heightOffset;
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
            if (isCulled)
                count++;
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
