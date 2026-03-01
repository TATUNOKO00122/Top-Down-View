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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * トップダウンビュー用カリング実装
 * 円柱カリングのみ（保護ロジックなし）
 */
public final class TopDownCuller {

    private static final Logger LOGGER = LoggerFactory.getLogger("TopDownView");
    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final int MAX_CACHE_SIZE = 8000;
    private static final int MAX_TRANSLUCENT_CACHE_SIZE = 500;
    private static final int MAX_FADE_CACHE_SIZE = 2000;
    private static final double CACHE_CLEAR_DISTANCE_SQ = 100.0;

    private Vec3 currentPlayerPos = Vec3.ZERO;
    private Vec3 currentCameraPos = Vec3.ZERO;

    private final Map<BlockPos, Boolean> cullingCache = new HashMap<>(1000);
    private final Set<BlockPos> translucentTrapdoorsCache = new HashSet<>(100);
    private final Map<BlockPos, Float> fadeAlphaCache = new HashMap<>(500);
    private final Map<BlockPos, Float> fadeBlocksCache = new HashMap<>(500);

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
        if (mc.level == null) return false;
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled()) {
            if (!cullingCache.isEmpty()) {
                cullingCache.clear();
            }
            return false;
        }

        if (level == null) return false;

        Boolean cached = cullingCache.get(pos);
        if (cached != null) return cached;

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

        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            cacheResult(pos, false);
            return false;
        }

        boolean isCulled = shouldCullByCylinder(pos, pPos, cPos);
        cacheResult(pos, isCulled);
        return isCulled;
    }

    /**
     * 逆ピラミッド保護判定
     * カメラの前方180度（半円）の範囲で、距離に応じて下方向への保護範囲が広がる
     * 距離1 → 高さ1, 距離2 → 高さ2, 距離3以上 → 高さ3
     */
    private boolean isInInvertedPyramid(BlockPos pos, Vec3 playerPos) {
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
            return false;
        }

        // 水平距離
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        // 保護高さ: 距離1→1, 距離2→2, 距離3以上→3
        int protectedHeight = Math.min((int) Math.floor(distXZ), 3);
        if (protectedHeight <= 0) {
            return false;
        }

        // 足元の下のブロックを0として、protectedHeight分上まで保護（< で未満判定）
        double groundY = Math.floor(playerPos.y) - 1;
        double blockY = pos.getY();

        return blockY < groundY + protectedHeight;
    }

    /**
     * トラップドア専用のカリング判定
     * Half.BOTTOM（床寄り）→ 足元以下なら保護（2ブロック）
     * Half.TOP（天井寄り）→ 足元の1ブロックのみ保護 + 3ブロック水平距離内 + ハシゴ/足場ありなら保護
     */
    private boolean shouldCullTrapdoor(BlockPos pos, BlockGetter level, BlockState state) {
        // Half.BOTTOM（床寄り）は足元以下なら保護（2ブロック）
        if (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM) {
            if (pos.getY() <= Math.floor(currentPlayerPos.y)) {
                return false;
            }
        } else {
            // Half.TOP（天井寄り）は足元の1ブロックのみ保護
            if (pos.getY() < Math.floor(currentPlayerPos.y)) {
                return false;
            }
        }

        // Half.TOP の追加保護判定
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return false;
        }

        // プレイヤー位置から上に3ブロックまでのトラップドアのみハシゴ保護を適用
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

    /**
     * 楕円柱カリング判定
     * カメラ→プレイヤーの軸に対して楕円柱カリングを適用
     * 断面は縦長の楕円（垂直方向が長い）
     */
    private boolean shouldCullByCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (blockCenter.y < playerPos.y) {
            return false;
        }

        // 逆ピラミッド保護: 前方180度のブロックを保護
        if (isInInvertedPyramid(pos, playerPos)) {
            return false;
        }

        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double shift = Config.cylinderForwardShift;
        Vec3 shiftedPlayerPos = new Vec3(
            playerPos.x + forwardX * shift,
            playerPos.y,
            playerPos.z + forwardZ * shift
        );

        Vec3 dir = shiftedPlayerPos.subtract(cameraPos);
        double dirLength = dir.length();
        if (dirLength < 1.0E-8) {
            return false;
        }

        Vec3 normDir = dir.normalize();
        Vec3 toBlockFromPlayer = blockCenter.subtract(playerPos);
        double forwardness = toBlockFromPlayer.dot(normDir);



        double radiusH = Config.cylinderRadiusHorizontal;
        double radiusV = Config.cylinderRadiusVertical;

        Vec3 segVec = shiftedPlayerPos.subtract(cameraPos);
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

        // 軸からの相対位置
        Vec3 relPos = blockCenter.subtract(closestPoint);

        // 軸方向成分を除去して、楕円断面内での位置を計算
        double alongAxis = relPos.dot(normDir);
        Vec3 perpPos = relPos.subtract(normDir.scale(alongAxis));

        // 水平成分（XZ平面）と垂直成分（Y）
        double distXZ = Math.sqrt(perpPos.x * perpPos.x + perpPos.z * perpPos.z);
        double distY = Math.abs(perpPos.y);

        // 楕円判定: (distXZ / rH)² + (distY / rV)² <= 1.0
        double normalizedDistSq = (distXZ * distXZ) / (radiusH * radiusH)
                                + (distY * distY) / (radiusV * radiusV);

        return normalizedDistSq <= 1.0;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 candidatePos = new Vec3(
            Math.floor(eyePos.x) + 0.5,
            Math.floor(eyePos.y) + 0.5,
            Math.floor(eyePos.z) + 0.5
        );
        Vec3 rawCameraPos = ModState.CAMERA.getCameraPosition();

        if (rawCameraPos == com.topdownview.state.CameraState.DEFAULT_POSITION) {
            this.currentPlayerPos = candidatePos;
            this.currentCameraPos = Vec3.ZERO;
            return;
        }

        Vec3 cPos = new Vec3(
            Math.floor(rawCameraPos.x) + 0.5,
            Math.floor(rawCameraPos.y) + 0.5,
            Math.floor(rawCameraPos.z) + 0.5
        );

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
            if (value) count++;
        }
        return count;
    }

    public int getCacheSize() {
        return cullingCache.size();
    }

    /**
     * カリング境界フェードの透明度を取得
     * @return 0.0（完全透明）〜1.0（不透明）
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

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level) {
        if (level == null) return 1.0f;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 1.0f;
        }

        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return 1.0f;
        }

        if (blockCenter.y < pPos.y) {
            return 1.0f;
        }

        if (isInInvertedPyramid(pos, pPos)) {
            return 1.0f;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return 1.0f;
            }
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            if (!shouldCullTrapdoorForFade(pos, level, state, pPos, cPos)) {
                return 1.0f;
            }
        }

        double normalizedDistSq = getNormalizedDistanceSq(pos, pPos, cPos);
        
        if (normalizedDistSq < 0) {
            return 1.0f;
        }

        double fadeStart = Config.fadeStart;
        double fadeMinAlpha = Config.fadeMinAlpha;

        if (normalizedDistSq >= 1.0) {
            return (float) fadeMinAlpha;
        }

        if (normalizedDistSq <= fadeStart) {
            return 1.0f;
        }

        double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
        return (float) (1.0 - t * (1.0 - fadeMinAlpha));
    }

    private boolean shouldCullTrapdoorForFade(BlockPos pos, BlockGetter level, BlockState state, Vec3 playerPos, Vec3 cameraPos) {
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
            playerPos.z + forwardZ * shift
        );

        Vec3 dir = shiftedPlayerPos.subtract(cameraPos);
        double dirLength = dir.length();
        if (dirLength < 1.0E-8) {
            return -1;
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
            return -1;
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
     * フェード対象ブロックのセットを取得
     * 楕円柱境界付近のブロックを半透明描画用に返す
     */
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
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 pPos = this.currentPlayerPos;
        Vec3 cPos = this.currentCameraPos;

        if (cPos == Vec3.ZERO) {
            return 1.0f;
        }

        if (blockCenter.y < pPos.y) {
            return 1.0f;
        }

        if (isInInvertedPyramid(pos, pPos)) {
            return 1.0f;
        }

        if (InteractableBlocks.isInteractableSimple(state)) {
            if (pos.getY() <= Math.floor(pPos.y)) {
                return 1.0f;
            }
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            if (!shouldCullTrapdoorForFade(pos, level, state, pPos, cPos)) {
                return 1.0f;
            }
        }

        double normalizedDistSq = getNormalizedDistanceSq(pos, pPos, cPos);
        
        if (normalizedDistSq < 0) {
            return 1.0f;
        }

        double fadeStart = Config.fadeStart;
        double fadeMinAlpha = Config.fadeMinAlpha;

        if (normalizedDistSq >= 1.0) {
            return (float) fadeMinAlpha;
        }

        if (normalizedDistSq <= fadeStart) {
            return 1.0f;
        }

        double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
        return (float) (1.0 - t * (1.0 - fadeMinAlpha));
    }

    /**
     * 透明化対象のトラップドア位置のセットを取得
     * ハシゴ/足場がある場合は除外（保護優先）
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
                        // ハシゴ/足場がある場合は半透明化しない（保護優先）
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

    /**
     * 下方向Y-1〜Y-3にハシゴ/足場があるか判定
     */
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

    /**
     * トラップドアを透明化すべきか判定（将来用）
     * 現在はConfig.trapdoorTranslucencyEnabled=falseで無効化
     */
    private boolean shouldMakeTranslucent(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        return isInCylinderForTrapdoor(pos, playerPos, cameraPos);
    }

    /**
     * トラップドア用の純粋な楕円柱判定
     * 逆ピラミッド保護などをスキップし、楕円柱内かどうかのみ判定
     */
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
