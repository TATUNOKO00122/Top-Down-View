package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.client.OreBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

public final class TopDownCuller {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    // 不変オブジェクトでアトミック更新（volatileで可視性確保）
    private static final record CullingContext(Vec3 playerPos, Vec3 cameraPos) {
        static final CullingContext EMPTY = new CullingContext(Vec3.ZERO, Vec3.ZERO);
    }

    private static final int UPDATE_FREQUENCY = 1;
    private static final double INTERACTION_RANGE_HORIZONTAL = 3.0;
    private static final int INTERACTION_RANGE_VERTICAL = 2;

    // マイニングモード用スライス設定：足元より下も含めて保護
    private static final int MINING_MODE_SLICE_OFFSET = -3; // 足元より3ブロック下から
    private static final int MINING_MODE_SLICE_HEIGHT = 5; // 合計5層（y-3からy+1まで）

    // マイニングモード時の鉱石除外範囲（プレイヤーからの距離）
    private static final double ORE_EXCLUDE_RADIUS = 2.0;

    // キャッシュクリア判定用の前回位置（ブロック座標）
    private int lastPlayerBlockX = Integer.MIN_VALUE;
    private int lastPlayerBlockY = Integer.MIN_VALUE;
    private int lastPlayerBlockZ = Integer.MIN_VALUE;

    private volatile CullingContext context = CullingContext.EMPTY;

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();

    private TopDownCuller() {
    }

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    public void clearCache() {
        cullingCache.clear();
        fadeCache.clear();
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            cullingCache.clear();
            return false;
        }

        if (level == null) {
            return false;
        }

        Boolean cached = cullingCache.get(pos);
        if (cached != null) {
            return cached;
        }

        // マイニングモード時はカリング無効、代わりにスライス表示
        if (ModState.STATUS.isMiningMode()) {
            boolean cull = isBlockCulledInMiningMode(pos, level);
            cullingCache.put(pos, cull);
            return cull;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cullingCache.put(pos, false);
            return false;
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        int playerBlockX = (int) Math.floor(pPos.x);
        int playerBlockZ = (int) Math.floor(pPos.z);
        int playerFeetY = (int) Math.floor(pPos.y) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cullingCache.put(pos, false);
                return false;
            }
        }

        if (cPos == Vec3.ZERO) {
            cullingCache.put(pos, false);
            return false;
        }

        float alpha = calculateFadeAlpha(pos, level, state);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(pos, isCulled);
        return isCulled;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state) {
        if (level == null) {
            return 1.0f;
        }

        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 1.0f;
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (isProtectedBlock(pos, state, level, pPos, cPos)) {
            return 1.0f;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(pos, pPos, cPos);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(pos, pPos);

        double fadeStart = Config.getFadeStart();
        double fadeNearAlpha = Config.getFadeNearAlpha();

        float cylinderAlpha;

        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
            cylinderAlpha = 1.0f;
        } else if (normalizedDistSq <= fadeStart) {
            cylinderAlpha = (float) fadeNearAlpha;
        } else {
            double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
            cylinderAlpha = (float) (fadeNearAlpha + t * (1.0 - fadeNearAlpha));
        }

        return (float) Math.max(cylinderAlpha, pyramidFactor);
    }

    private boolean isProtectedBlock(BlockPos pos, BlockState state, BlockGetter level, Vec3 pPos, Vec3 cPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (cPos == Vec3.ZERO) {
            return true;
        }

        if (blockCenter.y < pPos.y) {
            return true;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return true;
            }
        }

        return false;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 candidatePos = new Vec3(
                Math.floor(eyePos.x) + 0.5,
                Math.floor(eyePos.y) + 0.5,
                Math.floor(eyePos.z) + 0.5);
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        // カメラ位置が未初期化の場合、プレイヤー位置のみ更新して早期リターン
        // 次のフレームでカメラ位置が設定された後にカリングが有効になる
        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            // プレイヤー位置のみ更新（カメラ位置はZERO）
            this.context = new CullingContext(candidatePos, Vec3.ZERO);
            return;
        }

        // カメラ位置をブロック座標にスナップ（判定の安定化のため）
        Vec3 cPos = new Vec3(
                Math.floor(rawCameraPos.x) + 0.5,
                Math.floor(rawCameraPos.y) + 0.5,
                Math.floor(rawCameraPos.z) + 0.5);

        // プレイヤーのブロック座標を計算
        int playerBlockX = (int) Math.floor(eyePos.x);
        int playerBlockY = (int) Math.floor(eyePos.y);
        int playerBlockZ = (int) Math.floor(eyePos.z);

        // 位置が変わった場合のみキャッシュクリア（パフォーマンス最適化）
        if (playerBlockX != lastPlayerBlockX
                || playerBlockY != lastPlayerBlockY
                || playerBlockZ != lastPlayerBlockZ) {
            cullingCache.clear();
            lastPlayerBlockX = playerBlockX;
            lastPlayerBlockY = playerBlockY;
            lastPlayerBlockZ = playerBlockZ;
        }

        // 不変オブジェクトでアトミック更新（synchronized不要）
        this.context = new CullingContext(candidatePos, cPos);
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        this.context = CullingContext.EMPTY;
        // 前回位置もリセット
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
    }

    /**
     * マイニングモード時のスライス方式カリング
     * 真円柱範囲内だけスライス方式を適用、円柱外は表示
     * 鉱石はプレイヤーから2ブロック以内のみカリング除外
     * 階段は無条件でカリング除外
     */
    private boolean isBlockCulledInMiningMode(BlockPos pos, BlockGetter level) {
        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        // カメラ位置が未初期化の場合は表示
        if (cPos == Vec3.ZERO) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        // 階段は無条件でカリング除外
        if (state.getBlock() instanceof StairBlock) {
            return false;
        }

        // 鉱石はプレイヤーから2ブロック以内かつ足元から3ブロックの高さのみカリング除外
        if (OreBlocks.isOre(state)) {
            double dx = (pos.getX() + 0.5) - pPos.x;
            double dz = (pos.getZ() + 0.5) - pPos.z;
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            if (distXZ <= ORE_EXCLUDE_RADIUS) {
                // 高さ制限: 足元から3ブロックまで
                int playerFeetY = (int) Math.floor(pPos.y) - 1;
                if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 3) {
                    return false; // 2ブロック以内かつ足元から3ブロック以内はカリング除外
                }
            }
            // 2ブロック以上離れた、または高さ制限外の鉱石は通常のスライスカリングに従う
        }

        // マイニングモード用の真円柱範囲内かチェック
        double radius = Config.getMiningCylinderRadius();
        double forwardShift = Config.getMiningCylinderForwardShift();
        float yaw = ModState.CAMERA.getYaw();
        if (!CylinderCalculator.isInMiningCylinder(pos, pPos, cPos, radius, yaw, forwardShift)) {
            // 円柱外は表示（カリングしない）
            return false;
        }

        // 円柱内はスライス方式でカリング
        int playerFeetY = (int) Math.floor(pPos.y) - 1;

        // 保護範囲の基本設定
        int protectedMinY = playerFeetY + MINING_MODE_SLICE_OFFSET;
        int protectedMaxY = protectedMinY + MINING_MODE_SLICE_HEIGHT - 1;

        // カメラ側（手前）では保護を5段減らす（断面表示・アリの巣観察キット風）
        if (isCameraSide(pos, pPos)) {
            protectedMaxY -= 5;
        } else {
            // 奥側では距離に応じて保護を増やす（円柱の半径に比例して最大5段まで）
            double distanceFactor = getBackwardDistanceFactor(pos, pPos);
            int additionalLayers = (int) (distanceFactor * 5);
            protectedMaxY += additionalLayers;
        }

        // 保護範囲内なら表示、それ以外は非表示
        return pos.getY() < protectedMinY || pos.getY() > protectedMaxY;
    }

    /**
     * ブロックがカメラ側（手前）にあるか判定
     * カメラのYawとPitchを使用して正確な方向を計算
     */
    private boolean isCameraSide(BlockPos pos, Vec3 playerPos) {
        float pitch = ModState.CAMERA.getPitch();

        // ピッチ角がほぼ90度（真上）なら、水平方向のオフセットが存在しないため false
        if (pitch >= MathConstants.PITCH_NEAR_VERTICAL) {
            return false;
        }

        float yaw = ModState.CAMERA.getYaw();
        double radYaw = Math.toRadians(yaw);

        // プレイヤー→カメラの水平方向ベクトル（手前方向）
        double dxToCamera = Math.sin(radYaw);
        double dzToCamera = -Math.cos(radYaw);

        // プレイヤー→ブロックの水平方向ベクトル
        double dxBlock = (pos.getX() + 0.5) - playerPos.x;
        double dzBlock = (pos.getZ() + 0.5) - playerPos.z;

        // プレイヤー→カメラ方向との内積
        double dot = dxBlock * dxToCamera + dzBlock * dzToCamera;

        // 内積 > DOT_PRODUCT_THRESHOLD で明確にカメラ側（手前）にあると判定（微小な誤差を無視）
        return dot > MathConstants.DOT_PRODUCT_THRESHOLD;
    }

    /**
     * 奥側（プレイヤーより奥）での距離係数を計算
     * 円柱の半径に対する距離の割合（0.0〜1.0）
     */
    private double getBackwardDistanceFactor(BlockPos pos, Vec3 playerPos) {
        float pitch = ModState.CAMERA.getPitch();

        // ピッチ角がほぼ90度（真上）なら距離係数なし
        if (pitch >= 89.9f) {
            return 0.0;
        }

        float yaw = ModState.CAMERA.getYaw();
        double radYaw = Math.toRadians(yaw);

        // プレイヤー→奥方向の水平ベクトル（カメラ方向の逆）
        double dxBackward = -Math.sin(radYaw);
        double dzBackward = Math.cos(radYaw);

        // プレイヤー→ブロックの水平方向ベクトル
        double dxBlock = (pos.getX() + 0.5) - playerPos.x;
        double dzBlock = (pos.getZ() + 0.5) - playerPos.z;

        // 奥方向との内積（正の値 = 奥側）
        double dot = dxBlock * dxBackward + dzBlock * dzBackward;

        // 奥側でない場合は0
        if (dot <= MathConstants.DOT_PRODUCT_THRESHOLD) {
            return 0.0;
        }

        // 円柱の半径に対する割合（最大1.0）
        double radius = Config.getMiningCylinderRadius();
        return Math.min(dot / radius, 1.0);
    }

    public int getCulledBlockCount() {
        return cullingCache.getCulledCount();
    }

    public int getCacheSize() {
        return cullingCache.size();
    }

    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            return 1.0f;
        }

        // マイニングモード時はフェード無効（通常表示）
        if (ModState.STATUS.isMiningMode()) {
            return 1.0f;
        }

        if (!Config.isFadeEnabled()) {
            return 1.0f;
        }

        Float cached = fadeCache.getFadeAlpha(pos);
        if (cached != null) {
            return cached;
        }

        BlockState state = level.getBlockState(pos);
        float alpha = calculateFadeAlpha(pos, level, state);
        fadeCache.putFadeAlpha(pos, alpha);

        return alpha;
    }

    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !Config.isFadeEnabled()) {
            return false;
        }

        // マイニングモード時はフェードブロックとして扱わない
        if (ModState.STATUS.isMiningMode()) {
            return false;
        }

        if (level == null) {
            return false;
        }

        float alpha = getFadeAlpha(pos, level);
        if (alpha >= 1.0f) {
            return false;
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        int playerBlockX = (int) Math.floor(pPos.x);
        int playerBlockY = (int) Math.floor(pPos.y);
        int playerBlockZ = (int) Math.floor(pPos.z);

        if (pos.getX() == playerBlockX && pos.getY() == playerBlockY - 1 && pos.getZ() == playerBlockZ) {
            return false;
        }

        double dx = (pos.getX() + 0.5) - (playerBlockX + 0.5);
        double dz = (pos.getZ() + 0.5) - (playerBlockZ + 0.5);

        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double dot = dx * forwardX + dz * forwardZ;
        if (dot < 0) {
            return false;
        }

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ > INTERACTION_RANGE_HORIZONTAL) {
            return false;
        }

        int relY = pos.getY() - playerBlockY;
        if (relY < 0 || relY > INTERACTION_RANGE_VERTICAL) {
            return false;
        }

        return true;
    }

    public Map<BlockPos, Float> getFadeBlocks(BlockGetter level) {
        fadeCache.clearFadeBlocks();

        if (!ModState.STATUS.isEnabled()) {
            return fadeCache.getFadeBlocksCache();
        }

        // マイニングモード時はフェードブロックを返さない
        if (ModState.STATUS.isMiningMode()) {
            return fadeCache.getFadeBlocksCache();
        }

        if (!Config.isFadeEnabled()) {
            return fadeCache.getFadeBlocksCache();
        }

        // 不変オブジェクトでアトミックに読み取り
        CullingContext ctx = this.context;
        Vec3 pPos = ctx.playerPos();
        Vec3 cPos = ctx.cameraPos();

        if (level == null || cPos == Vec3.ZERO) {
            return fadeCache.getFadeBlocksCache();
        }

        int rangeH = Config.getCylinderRadiusHorizontal() + 2;
        int rangeV = Config.getCylinderRadiusVertical() + 3;

        int scanRangeXZ = Math.max(rangeH, 4);

        int minX = (int) Math.floor(pPos.x) - scanRangeXZ;
        int maxX = (int) Math.floor(pPos.x) + scanRangeXZ;
        int minY = (int) Math.floor(pPos.y) - 1;
        int maxY = (int) Math.floor(pPos.y) + rangeV;
        int minZ = (int) Math.floor(pPos.z) - scanRangeXZ;
        int maxZ = (int) Math.floor(pPos.z) + scanRangeXZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    float alpha = calculateFadeAlpha(mutablePos, level, state);
                    if (alpha < 1.0f && alpha > 0.0f) {
                        fadeCache.putFadeBlock(mutablePos.immutable(), alpha);

                        if (fadeCache.isFadeBlocksFull()) {
                            return fadeCache.getFadeBlocksCache();
                        }
                    }
                }
            }
        }

        return fadeCache.getFadeBlocksCache();
    }

    public Map<BlockPos, Float> getFadeBlocksCache() {
        return fadeCache.getFadeBlocksCache();
    }
}
