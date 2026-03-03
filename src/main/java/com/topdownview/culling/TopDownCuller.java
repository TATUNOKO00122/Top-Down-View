package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * トップダウンビュー用カリング実装
 */
public final class TopDownCuller {

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final int MAX_CACHE_SIZE = 8000;
    private static final int MAX_TRANSLUCENT_CACHE_SIZE = 500;
    private static final int MAX_FADE_CACHE_SIZE = 2000;
    private static final double CACHE_CLEAR_DISTANCE_SQ = 100.0;

    private volatile Vec3 currentPlayerPos = Vec3.ZERO;
    private volatile Vec3 currentCameraPos = Vec3.ZERO;

    private final Map<BlockPos, Boolean> cullingCache = new ConcurrentHashMap<>(1000);
    private final Set<BlockPos> translucentTrapdoorsCache = ConcurrentHashMap.newKeySet(100);
    private final Map<BlockPos, Float> fadeAlphaCache = new ConcurrentHashMap<>(500);
    private final Map<BlockPos, Float> fadeBlocksCache = new ConcurrentHashMap<>(500);

    private TopDownCuller() {
    }

    public static TopDownCuller getInstance() {
        return INSTANCE;
    }

    public int getFrequency() {
        return UPDATE_FREQUENCY;
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            if (!cullingCache.isEmpty()) {
                cullingCache.clear();
            }
            return false;
        }

        if (level == null)
            return false;

        Boolean cached = cullingCache.get(pos);
        if (cached != null)
            return cached;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            cacheResult(pos, false);
            return false;
        }

        // トラップドアは専用のカリング判定を使用
        if (state.getBlock() instanceof TrapDoorBlock) {
            boolean shouldCull = shouldCullTrapdoor(pos, level, state);
            cacheResult(pos, shouldCull);
            return shouldCull;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(currentPlayerPos.y)) {
                cacheResult(pos, false);
                return false;
            }
        }

        int playerBlockX = (int) Math.floor(currentPlayerPos.x);
        int playerBlockZ = (int) Math.floor(currentPlayerPos.z);
        int playerFeetY = (int) Math.floor(currentPlayerPos.y) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            if (pos.getY() >= playerFeetY && pos.getY() <= playerFeetY + 1) {
                cacheResult(pos, false);
                return false;
            }
        }

        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            cacheResult(pos, false);
            return false;
        }

        float alpha = calculateFadeAlpha(pos, level);
        boolean isCulled = alpha < 1.0f;
        cacheResult(pos, isCulled);
        return isCulled;
    }

    /**
     * 逆ピラミッド保護係数の計算
     * 
     * @return 0.0（保護なし）〜1.0（完全保護）
     */
    private double calculatePyramidProtectionFactor(BlockPos pos, Vec3 playerPos) {
        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);

        // カメラの前方ベクトル（水平のみ）
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // ブロックへのベクトル（水平のみ）
        double dx = pos.getX() + 0.5 - playerPos.x;
        double dz = pos.getZ() + 0.5 - playerPos.z;

        // 内積で前方180度判定（真横を含む）
        double dot = dx * forwardX + dz * forwardZ;
        if (dot < 0) {
            return 0.0;
        }

        // 水平距離
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        // 連続的な保護高さを計算 (最大3ブロック)
        double protectedHeight = Math.min(distXZ, 3.0);

        // 地面の高さを基準にする
        double groundY = Math.floor(playerPos.y) - 1.0;
        double targetY = groundY + protectedHeight;

        // ブロックの中心Y座標
        double blockY = pos.getY() + 0.5;

        // ブロック中心と保護境界（targetY）の縦方向の差
        double diff = targetY - blockY;

        // ブロックが境界より下（完全に保護される領域内）にあれば完全保護
        if (diff >= 0.0) {
            return 1.0;
        }
        // 境界のすぐ上（diff が マイナスだが近い場合）は、隣接フェードのグラデーションを適用する
        // 例: 境界から上方向へ 1.5 ブロックの厚さのオーラ（フェード）を作る
        double fadeThickness = 1.5;
        if (diff >= -fadeThickness) {
            // diff = 0.0 のとき t = 1.0 (完全保護に近い)
            // diff = -1.5 のとき t = 0.0 (最も透明に近い)
            double t = (fadeThickness + diff) / fadeThickness;
            double fadeNearAlpha = Config.fadeNearAlpha;
            return fadeNearAlpha + t * (1.0 - fadeNearAlpha);
        }

        return 0.0;
    }

    /**
     * トラップドア専用のカリング判定
     */
    private boolean shouldCullTrapdoor(BlockPos pos, BlockGetter level, BlockState state) {
        if (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM) {
            if (pos.getY() <= Math.floor(currentPlayerPos.y)) {
                return false;
            }
        } else {
            if (pos.getY() < Math.floor(currentPlayerPos.y)) {
                return false;
            }
        }

        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return false;
        }

        if (pos.getY() <= Math.floor(pPos.y) + 3) {
            if (hasLadderOrScaffoldingBelow(pos, level)) {
                return false;
            }
        }

        return isInCylinderForTrapdoor(pos, pPos, cPos);
    }

    private void cacheResult(BlockPos pos, boolean result) {
        if (cullingCache.size() >= MAX_CACHE_SIZE) {
            cullingCache.clear();
        }
        cullingCache.put(pos.immutable(), result);
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 candidatePos = new Vec3(
                Math.floor(eyePos.x) + 0.5,
                Math.floor(eyePos.y) + 0.5,
                Math.floor(eyePos.z) + 0.5);
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            this.currentPlayerPos = candidatePos;
            this.currentCameraPos = Vec3.ZERO;
            return;
        }

        Vec3 cPos = new Vec3(
                Math.floor(rawCameraPos.x) + 0.5,
                Math.floor(rawCameraPos.y) + 0.5,
                Math.floor(rawCameraPos.z) + 0.5);

        if (candidatePos.distanceToSqr(this.currentPlayerPos) > CACHE_CLEAR_DISTANCE_SQ) {
            cullingCache.clear();
        }

        this.currentPlayerPos = candidatePos;
        this.currentCameraPos = cPos;
    }

    public void reset() {
        cullingCache.clear();
        fadeAlphaCache.clear();
        fadeBlocksCache.clear();
        this.currentPlayerPos = Vec3.ZERO;
        this.currentCameraPos = Vec3.ZERO;
    }

    public int getCulledBlockCount() {
        int count = 0;
        for (Boolean value : cullingCache.values()) {
            if (value)
                count++;
        }
        return count;
    }

    public int getCacheSize() {
        return cullingCache.size();
    }

    /**
     * カリング境界フェードの透明度を取得
     */
    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            return 1.0f;
        }

        if (!Config.fadeEnabled) {
            return 1.0f;
        }

        Float cached = fadeAlphaCache.get(pos);
        if (cached != null) {
            return cached;
        }

        float alpha = calculateFadeAlpha(pos, level);

        if (fadeAlphaCache.size() >= MAX_FADE_CACHE_SIZE) {
            fadeAlphaCache.clear();
        }
        fadeAlphaCache.put(pos.immutable(), alpha);

        return alpha;
    }

    /**
     * 指定されたブロックの中心からシリンダー中心軸への正規化された距離の2乗を計算します。
     * 
     * @return 1.0以下ならシリンダー内。負の場合は計算不能（カメラ位置異常など）
     */
    private double getNormalizedDistanceSq(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double shift = Config.cylinderForwardShift;
        Vec3 shiftedPlayerPos = new Vec3(
                playerPos.x + forwardX * shift,
                playerPos.y,
                playerPos.z + forwardZ * shift);

        Vec3 dir = shiftedPlayerPos.subtract(cameraPos);
        double dirLength = dir.length();
        if (dirLength < 1.0E-8) {
            return -1.0;
        }

        Vec3 normDir = dir.normalize();

        double radiusH = Config.cylinderRadiusHorizontal;
        double radiusV = Config.cylinderRadiusVertical;

        Vec3 segVec = shiftedPlayerPos.subtract(cameraPos);
        double segLengthSq = segVec.lengthSqr();

        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(segVec) / segLengthSq;

        double segLength = Math.sqrt(segLengthSq);
        double extensionT = 3.0 / segLength;
        if (t < -extensionT) {
            return -1.0;
        }

        t = Math.max(-extensionT, Math.min(t, 1.0));

        Vec3 closestPoint = cameraPos.add(segVec.scale(t));
        Vec3 relPos = blockCenter.subtract(closestPoint);
        double alongAxis = relPos.dot(normDir);
        Vec3 perpPos = relPos.subtract(normDir.scale(alongAxis));

        double distXZ = Math.sqrt(perpPos.x * perpPos.x + perpPos.z * perpPos.z);
        double distY = Math.abs(perpPos.y);

        return (distXZ * distXZ) / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);
    }

    /**
     * ブロック自身が描画対象（保護されている）かどうかを判定します。
     * 
     * @return true: カリングされない（描画される）、false: カリング対象
     */
    private boolean isProtectedBlock(BlockPos pos, BlockState state, BlockGetter level) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return true;
        }

        // カメラ側の壁は保護しない（手前を描画しないためのカリング）
        if (blockCenter.y < pPos.y) {
            return true; // 足元より下は常に保護（地面）
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return true;
            }
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            if (!shouldCullTrapdoorForFade(pos, level, state, pPos, cPos)) {
                return true;
            }
        }

        return false;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level) {
        if (level == null)
            return 1.0f;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 1.0f;
        }

        // まず、自身が保護対象ならそのまま描画 (alpha=1.0)
        if (isProtectedBlock(pos, state, level)) {
            return 1.0f;
        }

        // --- 円柱ベースのフェード判定 ---
        double normalizedDistSq = getNormalizedDistanceSq(pos, this.currentPlayerPos, this.currentCameraPos);
        double pyramidFactor = calculatePyramidProtectionFactor(pos, this.currentPlayerPos);

        double fadeStart = Config.fadeStart;
        double fadeNearAlpha = Config.fadeNearAlpha;

        float cylinderAlpha;

        // シリンダーの計算ができない（カメラより後方など）、またはシリンダーより外側のブロックは基本は保護される（アルファ1.0）
        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
            cylinderAlpha = 1.0f;
        } else if (normalizedDistSq <= fadeStart) {
            cylinderAlpha = (float) fadeNearAlpha; // シリンダーの中心付近は最も透明
        } else {
            // fadeStart 〜 1.0 の間で、fadeNearAlpha から 1.0 まで滑らかにフェード
            double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
            cylinderAlpha = (float) (fadeNearAlpha + t * (1.0 - fadeNearAlpha));
        }

        // --- アルファ値の合成 ---
        // cylinderAlpha と pyramidFactor の大きい方（より不透明な方）を採用する。
        // これにより、カリングされるべき円柱内のブロックでも、逆ピラミッド境界（pyramidFactorのグラデーション）に触れていればフェード表示される。
        return (float) Math.max(cylinderAlpha, pyramidFactor);
    }

    private boolean shouldCullTrapdoorForFade(BlockPos pos, BlockGetter level, BlockState state, Vec3 playerPos,
            Vec3 cameraPos) {
        if (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM) {
            if (pos.getY() <= Math.floor(playerPos.y)) {
                return false;
            }
        } else {
            if (pos.getY() < Math.floor(playerPos.y)) {
                return false;
            }
        }

        if (pos.getY() <= Math.floor(playerPos.y) + 3) {
            if (hasLadderOrScaffoldingBelow(pos, level)) {
                return false;
            }
        }

        return isInCylinderForTrapdoor(pos, playerPos, cameraPos);
    }

    /**
     * フェード対象ブロックのセットを取得
     */
    /**
     * フェードブロックが当たり判定を持つか判定
     * 条件: フェード描画中 かつ プレイヤー前方180度・半径3ブロック・高さ3ブロックの円柱内 かつ 足元ブロックでない
     */
    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !Config.fadeEnabled) {
            return false;
        }

        if (level == null) {
            return false;
        }

        float alpha = getFadeAlpha(pos, level);
        if (alpha >= 1.0f) {
            return false;
        }

        int playerBlockX = (int) Math.floor(currentPlayerPos.x);
        int playerBlockY = (int) Math.floor(currentPlayerPos.y);
        int playerBlockZ = (int) Math.floor(currentPlayerPos.z);

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
        if (distXZ > 3.0) {
            return false;
        }

        int relY = pos.getY() - playerBlockY;
        if (relY < 0 || relY > 2) {
            return false;
        }

        return true;
    }

    public Map<BlockPos, Float> getFadeBlocks(BlockGetter level) {
        fadeBlocksCache.clear();

        if (!ModState.STATUS.isEnabled()) {
            return fadeBlocksCache;
        }

        if (!Config.fadeEnabled) {
            return fadeBlocksCache;
        }

        if (level == null || currentCameraPos == Vec3.ZERO) {
            return fadeBlocksCache;
        }

        int rangeH = Config.cylinderRadiusHorizontal + 2;
        int rangeV = Config.cylinderRadiusVertical + 3; // 保護高さ分少し広めに取る

        // 逆ピラミッド部分はプレイヤーの背後に広がるため、後方（探索半径）もカバーする必要がある
        // 余裕をもって前方・後方・左右ともに広めに範囲を取る (例: 保護高さの最大値 3、距離最大 3程度)
        int scanRangeXZ = Math.max(rangeH, 4); // 最低でも背後4ブロック分は確保

        int minX = (int) Math.floor(currentPlayerPos.x) - scanRangeXZ;
        int maxX = (int) Math.floor(currentPlayerPos.x) + scanRangeXZ;
        int minY = (int) Math.floor(currentPlayerPos.y) - 1; // 足元の下も念のため
        int maxY = (int) Math.floor(currentPlayerPos.y) + rangeV;
        int minZ = (int) Math.floor(currentPlayerPos.z) - scanRangeXZ;
        int maxZ = (int) Math.floor(currentPlayerPos.z) + scanRangeXZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    float alpha = calculateFadeAlphaForBlock(mutablePos, state, level);
                    if (alpha < 1.0f && alpha > 0.0f) {
                        fadeBlocksCache.put(mutablePos.immutable(), alpha);

                        if (fadeBlocksCache.size() >= MAX_FADE_CACHE_SIZE) {
                            return fadeBlocksCache;
                        }
                    }
                }
            }
        }

        return fadeBlocksCache;
    }

    private float calculateFadeAlphaForBlock(BlockPos pos, BlockState state, BlockGetter level) {
        return calculateFadeAlpha(pos, level);
    }

    public Map<BlockPos, Float> getFadeBlocksCache() {
        return fadeBlocksCache;
    }

    /**
     * 透明化対象のトラップドア位置のセットを取得
     */
    public Set<BlockPos> getTranslucentTrapdoors(BlockGetter level) {
        translucentTrapdoorsCache.clear();

        if (!ModState.STATUS.isEnabled()) {
            return translucentTrapdoorsCache;
        }

        if (!Config.trapdoorTranslucencyEnabled) {
            return translucentTrapdoorsCache;
        }

        if (level == null || currentCameraPos == Vec3.ZERO) {
            return translucentTrapdoorsCache;
        }

        int rangeH = Config.cylinderRadiusHorizontal + 2;
        int rangeV = Config.cylinderRadiusVertical + 2;

        int minX = (int) Math.floor(currentPlayerPos.x) - rangeH;
        int maxX = (int) Math.floor(currentPlayerPos.x) + rangeH;
        int minY = (int) Math.floor(currentPlayerPos.y);
        int maxY = (int) Math.floor(currentPlayerPos.y) + rangeV;
        int minZ = (int) Math.floor(currentPlayerPos.z) - rangeH;
        int maxZ = (int) Math.floor(currentPlayerPos.z) + rangeH;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.getBlock() instanceof TrapDoorBlock) {
                        if (hasLadderOrScaffoldingBelow(mutablePos, level)) {
                            continue;
                        }

                        if (shouldMakeTranslucent(mutablePos, currentPlayerPos, currentCameraPos)) {
                            translucentTrapdoorsCache.add(mutablePos.immutable());

                            if (translucentTrapdoorsCache.size() >= MAX_TRANSLUCENT_CACHE_SIZE) {
                                return translucentTrapdoorsCache;
                            }
                        }
                    }
                }
            }
        }

        return translucentTrapdoorsCache;
    }

    private boolean hasLadderOrScaffoldingBelow(BlockPos pos, BlockGetter level) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos checkPos = pos.below(dy);
            BlockState belowState = level.getBlockState(checkPos);
            if (belowState.is(Blocks.LADDER) || belowState.is(Blocks.SCAFFOLDING)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldMakeTranslucent(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        return isInCylinderForTrapdoor(pos, playerPos, cameraPos);
    }

    private boolean isInCylinderForTrapdoor(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        Vec3 dir = playerPos.subtract(cameraPos);
        double dirLength = dir.length();
        if (dirLength < 1.0E-8) {
            return false;
        }

        Vec3 normDir = dir.normalize();

        double radiusH = Config.cylinderRadiusHorizontal;
        double radiusV = Config.cylinderRadiusVertical;

        Vec3 segVec = playerPos.subtract(cameraPos);
        double segLengthSq = segVec.lengthSqr();

        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(segVec) / segLengthSq;

        double segLength = Math.sqrt(segLengthSq);
        double extensionT = 3.0 / segLength;
        if (t < -extensionT) {
            return false;
        }

        t = Math.max(-extensionT, Math.min(t, 1.0));

        Vec3 closestPoint = cameraPos.add(segVec.scale(t));
        Vec3 relPos = blockCenter.subtract(closestPoint);
        double alongAxis = relPos.dot(normDir);
        Vec3 perpPos = relPos.subtract(normDir.scale(alongAxis));

        double distXZ = Math.sqrt(perpPos.x * perpPos.x + perpPos.z * perpPos.z);
        double distY = Math.abs(perpPos.y);

        double normalizedDistSq = (distXZ * distXZ) / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);

        return normalizedDistSq <= 1.0;
    }
}
