package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.cache.CullingCacheManager;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.PyramidProtectionCalc;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.ModState;
import com.topdownview.culling.ladder.LadderHelper;
import com.topdownview.culling.trapdoor.TrapdoorHelper;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * トップダウン視点用のブロックカリングを管理するシングルトンクラス。
 * 
 * <p>ハイブリッドカリング戦略:
 * <ul>
 *   <li>シリンダーゾーン: カメラとプレイヤー間の可視領域</li>
 *   <li>逆ピラミッド保護: プレイヤー周辺の足元ブロック保護</li>
 *   <li>フェード境界: カリング境界での滑らかな透過遷移</li>
 * </ul>
 */
public final class TopDownCuller {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TopDownCuller INSTANCE = new TopDownCuller();

    private static final int UPDATE_FREQUENCY = 1;
    private static final double ENTITY_PROTECTION_RADIUS_SQ = 4.0;
    // プレイヤーがこのブロック距離以上移動した時のみカリング/フェードキャッシュをクリア。
    // 1ブロック毎のクリアは地下の階段昇降・洞穴の起伏で頻発しキャッシュミス連鎖を起こすため、
    // マンハッタン距離3まではキャッシュを再利用する（フェード描画で境界変化を補間）。
    private static final int CACHE_CLEAR_MOVE_THRESHOLD = 3;

    private double playerX;
    private double playerY;
    private double playerZ;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private boolean contextValid = false;

    private int lastPlayerBlockX = Integer.MIN_VALUE;
    private int lastPlayerBlockY = Integer.MIN_VALUE;
    private int lastPlayerBlockZ = Integer.MIN_VALUE;
    private int lastCameraBlockX = Integer.MIN_VALUE;
    private int lastCameraBlockY = Integer.MIN_VALUE;
    private int lastCameraBlockZ = Integer.MIN_VALUE;

    // フェードブロック再構築判定用: 前回走査時のプレイヤー/カメラブロック座標。
    // 座標が変化した時のみ collectFadeBlocks を再実行し、プレイヤー静止時の毎tick全走査を回避。
    private int lastFadePBlockX = Integer.MIN_VALUE;
    private int lastFadePBlockY = Integer.MIN_VALUE;
    private int lastFadePBlockZ = Integer.MIN_VALUE;
    private int lastFadeCBlockX = Integer.MIN_VALUE;
    private int lastFadeCBlockY = Integer.MIN_VALUE;
    private int lastFadeCBlockZ = Integer.MIN_VALUE;
    private boolean cacheClearedOnDisabled = false;

    // 階段除外：プレイヤー足元〜足元+exclusionHeight の範囲内の階段ブロックはカリングから除外
    private final Set<BlockPos> excludedStairBlocks = new HashSet<>();
    // 視線遮蔽半透明化用: excludedStairBlocks に含まれるブロックのシーケンス情報
    private List<Staircase> detectedStaircases = List.of();
    private int lastStairScanBlockX = Integer.MIN_VALUE;
    private int lastStairScanBlockY = Integer.MIN_VALUE;
    private int lastStairScanBlockZ = Integer.MIN_VALUE;

    // ハシゴ視線遮蔽半透明化用: プレイヤー足元付近の保護対象ハシゴチェーン
    private final List<ProtectedLadderChain> protectedLadderChains = new ArrayList<>();
    // ハシゴチェーンに属する全ブロック位置（ハシゴ自身＋支え壁）。isBlockCulled/isProtectedBlock の高速判定用
    private final Set<BlockPos> protectedLadderPositions = new HashSet<>();

    // 自然木視線遮蔽半透明化用: 保護対象の自然木原木位置
    private final Set<Long> protectedTreeLogPositions = new HashSet<>();
    // 自然木視線遮蔽半透明化用: (x,z)カラムでグルーピングした幹リスト
    private final List<ProtectedTreeTrunk> protectedTreeTrunks = new ArrayList<>();
    // 視線遮蔽と判定された幹のカラムキーセット（isBlockCulled 高速判定用）
    private final Set<Long> occludedTreeTrunkColumns = new HashSet<>();

    // 空間認識（フラッドフィル部屋探索）の結果。インタラクト可能ブロック保護の拡張等に使用。
    // updateSpaceRecognition で更新される。
    private boolean currentSpaceEnclosed = false;
    private SpaceProbe.Result currentSpaceResult = null;

    // 屋内空間と判定された場合の天井ブロック位置集合。
    // 「直下(y-1)が空気セルである shell ブロック」を天井と定義し、alpha=0 で一律非表示する。
    // 保護対象ブロック（チェスト等のインタラクト可能ブロック、Trapdoor）は
    // isBlockCulled 内の isProtectedBlock 判定で先に救済されるため、ここには含まれうるが描画されない。
    private final LongOpenHashSet ceilingCullPositions = new LongOpenHashSet();

    private final CullingCacheManager cullingCache = new CullingCacheManager();
    private final FadeCacheManager fadeCache = new FadeCacheManager();
    private final MutableBlockPos entityGroundedPos = new MutableBlockPos();

    // フレーム毎にキャッシュされる Config 値
    private double cachedFadeStart;
    private double cachedFadeNearAlpha;
    private double cachedFadeBlockHitThreshold;
    private int cachedCylinderRadiusHorizontal;
    private int cachedCylinderRadiusVertical;

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
        excludedStairBlocks.clear();
        protectedTreeLogPositions.clear();
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns.clear();
        ceilingCullPositions.clear();
        currentSpaceEnclosed = false;
        currentSpaceResult = null;
        LadderHelper.clearCache();
        NaturalTreeDetector.clearCache();
        resetLastBlockCoords();
    }

    /**
     * スキャン/フェードキャッシュ用の前回座標を初期値にリセット。
     * clearCache / reset / disable 時のキャッシュ無効化で共有。
     */
    private void resetLastBlockCoords() {
        lastStairScanBlockX = Integer.MIN_VALUE;
        lastStairScanBlockY = Integer.MIN_VALUE;
        lastStairScanBlockZ = Integer.MIN_VALUE;
        lastFadePBlockX = Integer.MIN_VALUE;
        lastFadePBlockY = Integer.MIN_VALUE;
        lastFadePBlockZ = Integer.MIN_VALUE;
        lastFadeCBlockX = Integer.MIN_VALUE;
        lastFadeCBlockY = Integer.MIN_VALUE;
        lastFadeCBlockZ = Integer.MIN_VALUE;
        lastPlayerBlockX = Integer.MIN_VALUE;
        lastPlayerBlockY = Integer.MIN_VALUE;
        lastPlayerBlockZ = Integer.MIN_VALUE;
        lastCameraBlockX = Integer.MIN_VALUE;
        lastCameraBlockY = Integer.MIN_VALUE;
        lastCameraBlockZ = Integer.MIN_VALUE;
    }

    public boolean isCulled(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return isBlockCulled(pos, mc.level);
    }

    public boolean isBlockCulled(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            if (!cacheClearedOnDisabled) {
                cullingCache.clear();
                fadeCache.clear();
                LadderHelper.clearCache();
                NaturalTreeDetector.clearCache();
                cacheClearedOnDisabled = true;
            }
            return false;
        }
        cacheClearedOnDisabled = false;

        if (level == null) {
            return false;
        }

        long posLong = pos.asLong();
        Boolean cached = cullingCache.get(posLong);
        if (cached != null) {
            return cached;
        }

        if (!contextValid) {
            cullingCache.put(posLong, false);
            return false;
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        if (ModState.STATUS.isMiningMode()) {
            boolean cull = MiningModeCuller.isBlockCulled(pos, level, pX, pY, pZ, cX, cY, cZ);
            cullingCache.put(posLong, cull);
            return cull;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            cullingCache.put(posLong, false);
            return false;
        }

        // プレイヤー自身のいる場所（足元〜頭上まで保護）
        int playerBlockX = (int) Math.floor(pX);
        int playerBlockZ = (int) Math.floor(pZ);
        int playerFeetY = (int) Math.floor(pY) - 1;
        if (pos.getX() == playerBlockX && pos.getZ() == playerBlockZ) {
            int maxProtectY = Config.isPlayerNearTranslucencyEnabled() ? playerFeetY : playerFeetY + 1;
            if (pos.getY() >= playerFeetY && pos.getY() <= maxProtectY) {
                cullingCache.put(posLong, false);
                return false;
            }
        }

        // プレイヤー周囲の半透明化（保護ブロックは除外）
        if (Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(pos, pX, pY, pZ)) {
            if (!isProtectedBlock(pos, state, pY, level)) {
                cullingCache.put(posLong, true);
                return true;
            }
        }

        // ハシゴ視線遮蔽半透明化: 保護対象ハシゴチェーンに属するブロックはカリング対象とし、
        // フェードキャッシュで半透明描画する（collectLadderOcclusionBlocks で登録）。
        // isProtectedBlock より先に判定し、足元保護等に捕捉されないようにする。
        if (Config.isLadderOccludeEnabled() && !protectedLadderPositions.isEmpty()
                && protectedLadderPositions.contains(pos)) {
            cullingCache.put(posLong, true);
            return true;
        }

        // 自然木視線遮蔽半透明化: 視線を遮る幹に属する自然木原木のみカリング対象とする。
        // isProtectedBlock より先に判定し、非遮蔽幹の原木は通常の保護ロジックに流す。
        if (Config.isTreeOccludeEnabled() && !occludedTreeTrunkColumns.isEmpty()
                && protectedTreeLogPositions.contains(posLong)) {
            long columnKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            if (occludedTreeTrunkColumns.contains(columnKey)) {
                cullingCache.put(posLong, true);
                return true;
            }
        }

        // その他保護対象ブロック判定（Trapdoor, 足元より下, インタラクト可能ブロック等）
        if (isProtectedBlock(pos, state, pY, level)) {
            cullingCache.put(posLong, false);
            return false;
        }

        // 屋内空間で検出された天井ブロック（直下が airCell である shell）を一律カリング。
        // 保護ブロック（チェスト等のインタラクト可能ブロック）は上の isProtectedBlock で救済済み。
        // 屋内判定時に常時有効（Config不要）。alpha=0 相当で完全非表示とするため cullingCache に true を登録。
        if (!ceilingCullPositions.isEmpty() && ceilingCullPositions.contains(posLong)) {
            cullingCache.put(posLong, true);
            return true;
        }

        // 階段除外：プレイヤー足元〜足元+exclusionHeight の範囲内の階段ブロック
        if (Config.isStaircaseExclusionEnabled() && isExcludedStairBlock(pos, playerFeetY)) {
            boolean occludeEnabled = Config.isStaircaseOccludeEnabled();
            cullingCache.put(posLong, occludeEnabled);
            return occludeEnabled;
        }

        float alpha = calculateFadeAlpha(pos, level, state, pX, pY, pZ, cX, cY, cZ);
        boolean isCulled = alpha < 1.0f;
        cullingCache.put(posLong, isCulled);
        return isCulled;
    }

    private boolean isPlayerNearBlock(BlockPos pos, double pX, double pY, double pZ) {
        int pBX = (int) Math.floor(pX);
        int pBY = (int) Math.floor(pY);
        int pBZ = (int) Math.floor(pZ);
        int rangeH = Config.getPlayerNearTranslucencyRangeHorizontal();
        int rangeV = Config.getPlayerNearTranslucencyRangeVertical();
        return pos.getX() >= pBX - rangeH && pos.getX() <= pBX + rangeH
            && pos.getZ() >= pBZ - rangeH && pos.getZ() <= pBZ + rangeH
            && pos.getY() >= pBY && pos.getY() < pBY + rangeV;
    }

    private float calculateFadeAlpha(BlockPos pos, BlockGetter level, BlockState state,
            double pX, double pY, double pZ, double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                pos, pX, pY, pZ, cX, cZ);

        double fadeStart = this.cachedFadeStart;
        double fadeNearAlpha = this.cachedFadeNearAlpha;

        float cylinderAlpha;

        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
            cylinderAlpha = 1.0f;
        } else if (normalizedDistSq <= fadeStart) {
            cylinderAlpha = (float) fadeNearAlpha;
        } else {
            double t = (normalizedDistSq - fadeStart) / (1.0 - fadeStart);
            cylinderAlpha = (float) (fadeNearAlpha + t * (1.0 - fadeNearAlpha));
        }

        float finalAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);

        // 葉ブロックで、グラフィックス設定がFAST（透過しない設定）の場合、フェード（半透明描画）させず瞬時にカリングする
        if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                net.minecraft.client.Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
            return 0.0f;
        }

        return finalAlpha;
    }

    private boolean isProtectedBlock(BlockPos pos, BlockState state, double pY, BlockGetter level) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            // TrapdoorHelperを使用して、カリング対象外（保護対象）であればtrueを返す
            // プリミティブ値版 shouldCull() を呼び出して Vec3 生成を回避
            return !TrapdoorHelper.shouldCull(pos, level, state, playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        }

        int playerFeetY = (int) Math.floor(pY) - 1;

        // ハシゴ視線遮蔽半透明化が有効な場合は、ハシゴ保護を無効化しカリング対象に流す
        // （collectLadderOcclusionBlocks でフェードキャッシュに登録される）
        boolean ladderOcclude = Config.isLadderOccludeEnabled();

        // ハシゴ自身が3個以上連続するチェーンに属し、かつプレイヤーの立っている位置+2以内から始まる場合保護
        if (!ladderOcclude && state.getBlock() instanceof LadderBlock) {
            if (LadderHelper.isLadderInLongChain(pos, level)) {
                int chainBottomY = LadderHelper.getChainBottomY(pos, level);
                if (chainBottomY >= playerFeetY && chainBottomY <= playerFeetY + 1) {
                    return true;
                }
            }
        }

        // ハシゴの支え側ブロックで、かつそのハシゴがプレイヤー付近のチェーンなら保護
        if (!ladderOcclude && LadderHelper.isBlockBehindLadderChain(pos, level, playerFeetY)) {
            return true;
        }

        // ブロックの上面のY座標（ブロック内相対値、0.0〜1.0）を取得
        double blockHeight = 0.0;
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            blockHeight = shape.max(net.minecraft.core.Direction.Axis.Y);
        }

        // ハーフブロック（0.5）より薄いブロック（かつ空気ではない＝高さが0より大きい）
        boolean isThinnerThanSlab = blockHeight > 0.0 && blockHeight < 0.5;

        // ハーフブロックより薄いブロックの場合、保護閾値を1.0ブロック分引き上げる
        double protectThresholdY = isThinnerThanSlab ? pY + 1.0 : pY;

        if (pos.getY() + 0.5 < protectThresholdY) {
            return true;
        }

        // 自然木のログ保護（カリングから除外）。建物の木材ログは対象外。
        // 視線遮蔽幹に属する原木は isBlockCulled で既にカリングされており、
        // ここに到達するのは非遮蔽幹の原木のみ（通常の保護動作）。
        if (Config.isProtectNaturalTreeLogs()
                && state.is(net.minecraft.tags.BlockTags.LOGS)
                && NaturalTreeDetector.isNaturalTreeLog(pos.asLong())) {
            return true;
        }

        if (InteractableBlocks.isInteractable(state, level, pos)) {
            int checkY = pos.getY();
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                if (state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                    checkY--;
                }
            }
            // 通常時は足元+1（目線レベル）まで保護。
            // 屋根のある閉空間（ENCLOSED）では足元+3まで保護（天井のチェスト等に手が届くよう拡張）。
            boolean enclosed = currentSpaceEnclosed;
            int protectY = enclosed ? playerFeetY + 3 : playerFeetY + 1;
            if (checkY <= protectY) {
                return true;
            }
        }

        return false;
    }

    public void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            contextValid = false;
            return;
        }

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getEyeY();
        double eyeZ = mc.player.getZ();

        if (!com.topdownview.state.CameraState.isPositionValid(ModState.CAMERA.getCameraPosition())) {
            playerX = Math.floor(eyeX) + 0.5;
            playerY = Math.floor(eyeY) + 0.5;
            playerZ = Math.floor(eyeZ) + 0.5;
            contextValid = false;
            return;
        }

        double rawCameraX = ModState.CAMERA.getCameraX();
        double rawCameraY = ModState.CAMERA.getCameraY();
        double rawCameraZ = ModState.CAMERA.getCameraZ();

        playerX = Math.floor(eyeX) + 0.5;
        playerY = Math.floor(eyeY) + 0.5;
        playerZ = Math.floor(eyeZ) + 0.5;
        cameraX = Math.floor(rawCameraX) + 0.5;
        cameraY = Math.floor(rawCameraY) + 0.5;
        cameraZ = Math.floor(rawCameraZ) + 0.5;
        contextValid = true;

        // シリンダー計算用の事前パラメータ（sin/cos/シフト）を更新
        CylinderCalculator.updateCache(ModState.CAMERA.getYaw(), Config.getCylinderForwardShift());

        // Config値をフレームキャッシュ
        cachedFadeStart = Config.getFadeStart();
        cachedFadeNearAlpha = Config.getFadeNearAlpha();
        cachedFadeBlockHitThreshold = Config.getFadeBlockHitThreshold();
        cachedCylinderRadiusHorizontal = Config.getCylinderRadiusHorizontal();
        cachedCylinderRadiusVertical = Config.getCylinderRadiusVertical();

        int currentBlockX = (int) Math.floor(eyeX);
        int currentBlockY = (int) Math.floor(eyeY);
        int currentBlockZ = (int) Math.floor(eyeZ);

        int currentCamBlockX = (int) Math.floor(rawCameraX);
        int currentCamBlockY = (int) Math.floor(rawCameraY);
        int currentCamBlockZ = (int) Math.floor(rawCameraZ);

        boolean playerMoved = false;
        if (lastPlayerBlockX != Integer.MIN_VALUE) {
            int moveDist = Math.abs(currentBlockX - lastPlayerBlockX)
                    + Math.abs(currentBlockY - lastPlayerBlockY)
                    + Math.abs(currentBlockZ - lastPlayerBlockZ);
            if (moveDist >= CACHE_CLEAR_MOVE_THRESHOLD) {
                playerMoved = true;
            }
        } else {
            lastPlayerBlockX = currentBlockX;
            lastPlayerBlockY = currentBlockY;
            lastPlayerBlockZ = currentBlockZ;
        }

        boolean cameraMoved = false;
        if (lastCameraBlockX != Integer.MIN_VALUE) {
            if (currentCamBlockX != lastCameraBlockX ||
                currentCamBlockY != lastCameraBlockY ||
                currentCamBlockZ != lastCameraBlockZ) {
                cameraMoved = true;
            }
        } else {
            lastCameraBlockX = currentCamBlockX;
            lastCameraBlockY = currentCamBlockY;
            lastCameraBlockZ = currentCamBlockZ;
        }

        if (playerMoved || cameraMoved) {
            cullingCache.clear();
            fadeCache.clear();
            if (playerMoved) {
                lastPlayerBlockX = currentBlockX;
                lastPlayerBlockY = currentBlockY;
                lastPlayerBlockZ = currentBlockZ;
            }
            if (cameraMoved) {
                lastCameraBlockX = currentCamBlockX;
                lastCameraBlockY = currentCamBlockY;
                lastCameraBlockZ = currentCamBlockZ;
            }
        }

        // 空間認識を更新（プレイヤーがブロック境界を超えたら再探索）。
        // 階段除外は設定時のみ実行されるが、空間探索自体は常時実行。
        updateSpaceRecognition(mc, currentBlockX, currentBlockY, currentBlockZ);

        // 木の視線遮蔽判定を事前計算（各フレーム、isBlockCulled が呼ばれる前に判定）。
        updateTreeTrunkOcclusion();

        updateEntityCulling(mc);
    }

    /**
     * 空間認識と階段除外リストを更新。
     * プレイヤーが別ブロックに移動した時のみ再探索する（重い処理を毎tick走らせない）。
     * 階段検出は空間探索結果に依存せず独立スキャンするが、
     * 保護（除外リストへの追加）は空間が検出されている場合のみ適用する。
     */
    private void updateSpaceRecognition(Minecraft mc, int blockX, int blockY, int blockZ) {
        // 前回スキャン位置からのマンハッタン距離を判定し、4ブロック未満であれば再利用（走査頻度の削減）
        if (lastStairScanBlockX != Integer.MIN_VALUE &&
            lastStairScanBlockY != Integer.MIN_VALUE &&
            lastStairScanBlockZ != Integer.MIN_VALUE) {
            int dx = Math.abs(blockX - lastStairScanBlockX);
            int dy = Math.abs(blockY - lastStairScanBlockY);
            int dz = Math.abs(blockZ - lastStairScanBlockZ);
            if (dx + dy + dz < 2) {
                return;
            }
        }
        lastStairScanBlockX = blockX;
        lastStairScanBlockY = blockY;
        lastStairScanBlockZ = blockZ;

        excludedStairBlocks.clear();
        detectedStaircases = List.of();
        protectedLadderChains.clear();
        protectedLadderPositions.clear();
        ceilingCullPositions.clear();

        if (mc.level == null || mc.player == null) {
            currentSpaceEnclosed = false;
            return;
        }

        // 自然木ログ検出（設定時のみ）。空間探索より先に実行し、cullingCache更新前に完了させる。
        boolean protectTree = Config.isProtectNaturalTreeLogs();
        boolean treeOcclude = protectTree && Config.isTreeOccludeEnabled();
        if (protectTree) {
            int treeRadiusH = this.cachedCylinderRadiusHorizontal + 2;
            NaturalTreeDetector.scan(mc.level, blockX, blockY, blockZ, treeRadiusH);
            // 視線遮蔽半透明化用に自然木ログ位置を収集
            protectedTreeLogPositions.clear();
            protectedTreeLogPositions.addAll(NaturalTreeDetector.getNaturalTreeLogs());
            if (treeOcclude) {
                // 原木位置を(x,z)カラムでグルーピングして幹リストを構築
                buildProtectedTreeTrunks();
            } else {
                protectedTreeTrunks.clear();
                occludedTreeTrunkColumns.clear();
            }
        } else {
            NaturalTreeDetector.clearCache();
            protectedTreeLogPositions.clear();
            protectedTreeTrunks.clear();
            occludedTreeTrunkColumns.clear();
        }

        BlockPos seed = mc.player.blockPosition();
        currentSpaceResult = SpaceProbe.probe(mc.level, seed);
        currentSpaceEnclosed = currentSpaceResult.isEnclosed();

        // 足元Y（足元ブロック = eyeY-1 の床 = eyeY-2）。playerY は eyeY のブロック中心。
        // update() で playerY = floor(eyeY)+0.5。足元床ブロック = floor(eyeY)-1。
        int playerFeetY = blockY - 1;

        // 天井カリング位置の更新: 屋内と判定された場合、shell セルのうち
        // 直下(y-1)が空気セルであるブロックを天井とみなして収集する。
        // isBlockCulled で保護判定後に一律カリング(alpha=0)するための位置集合。
        // 外れている/非屋外時はクリアして何もしない。
        // この処理は階段除外設定やハシゴ設定に依存せず常時実行（屋内天井は誰にでも見えるべきでない）。
        updateCeilingCullPositions();

        // ハシゴチェーン収集（ハシゴ半透明化設定時のみ）。空間認識に依存せず常にスキャン。
        if (Config.isLadderOccludeEnabled()) {
            scanProtectedLadderChains(mc.level, blockX, blockY, blockZ, playerFeetY);
        }

        // 階段除外は設定時のみ実行
        if (!Config.isStaircaseExclusionEnabled()) {
            return;
        }

        // 階段検出自体は独立スキャンで行うが、保護は囲まれた空間が検出されている場合のみ適用
        if (!currentSpaceEnclosed) {
            return;
        }

        List<Staircase> staircases = StairAnalyzer.detect(mc.level, seed,
                com.topdownview.state.SpaceDebugState.STAIR_SCAN_RADIUS,
                com.topdownview.state.SpaceDebugState.MIN_STAIRCASE_STEPS);
        if (staircases.isEmpty()) {
            return;
        }

        int exclusionHeight = Config.getStaircaseExclusionHeight();
        int minY = playerFeetY;
        int maxY = playerFeetY + exclusionHeight;

        RoomFloodFill.Result roomResult = currentSpaceResult != null ? currentSpaceResult.getRoomResult() : null;
        LongSet airCells = roomResult != null ? roomResult.getAirCells() : null;

        List<Staircase> detected = new ArrayList<>();
        for (Staircase stair : staircases) {
            // 天井の階段などを誤検出・除外しないよう、階段の最下段がプレイヤーの足元+1以下から始まるもののみに限定
            if (stair.getBottomPos().getY() <= playerFeetY + 1) {
                boolean anyStepInRange = false;
                for (BlockPos step : stair.getSteps()) {
                    if (step.getY() >= minY && step.getY() <= maxY) {
                        if (isIndoorStairStep(airCells, step)) {
                            excludedStairBlocks.add(step.immutable());
                            anyStepInRange = true;
                        }
                    }
                }
                // 視線遮蔽半透明化用にシーケンス全体を保持（範囲内の段が1つでもあれば）
                if (anyStepInRange) {
                    detected.add(stair);
                }
            }
        }
        detectedStaircases = detected;
    }

    /**
     * NaturalTreeDetector が収集した原木位置を (x,z) カラムでグルーピングし、
     * {@link #protectedTreeTrunks} を構築する。
     */
    private void buildProtectedTreeTrunks() {
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns.clear();
        Set<Long> logPositions = NaturalTreeDetector.getNaturalTreeLogs();
        if (logPositions.isEmpty()) {
            return;
        }

        Map<Long, int[]> columns = new HashMap<>();
        for (long posLong : logPositions) {
            int x = BlockPos.getX(posLong);
            int z = BlockPos.getZ(posLong);
            int y = BlockPos.getY(posLong);
            long columnKey = BlockPos.asLong(x, 0, z);
            int[] range = columns.get(columnKey);
            if (range == null) {
                range = new int[]{y, y};
                columns.put(columnKey, range);
            } else {
                if (y < range[0]) range[0] = y;
                if (y > range[1]) range[1] = y;
            }
        }

        for (Map.Entry<Long, int[]> entry : columns.entrySet()) {
            long columnKey = entry.getKey();
            int[] range = entry.getValue();
            protectedTreeTrunks.add(new ProtectedTreeTrunk(
                    BlockPos.getX(columnKey), BlockPos.getZ(columnKey),
                    range[0], range[1]));
        }
    }

    /**
     * 各フレームで呼ばれる木の幹の視線遮蔽事前計算。
     * 各幹のいずれかの原木がカメラ→プレイヤーの視線を遮る場合、
     * その幹のカラムキーを {@link #occludedTreeTrunkColumns} に追加する。
     * この結果は {@link #isBlockCulled(BlockPos, BlockGetter)} で使用される。
     */
    private void updateTreeTrunkOcclusion() {
        occludedTreeTrunkColumns.clear();
        if (!Config.isTreeOccludeEnabled() || protectedTreeTrunks.isEmpty()) {
            return;
        }

        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;
        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
            boolean anyOccluding = false;
            for (int y = trunk.bottomY; y <= trunk.topY; y++) {
                mutablePos.set(trunk.x, y, trunk.z);
                long posLong = mutablePos.asLong();
                if (!protectedTreeLogPositions.contains(posLong)) {
                    continue;
                }
                if (isStairOccludingView(mutablePos, cX, cY, cZ, pX, pY, pZ)) {
                    anyOccluding = true;
                    break;
                }
            }
            if (anyOccluding) {
                occludedTreeTrunkColumns.add(BlockPos.asLong(trunk.x, 0, trunk.z));
            }
        }
    }

    /**
     * 屋内空間の shell セルから天井ブロック（直下が空気セル）を抽出し、
     * {@link #ceilingCullPositions} を構築する。
     *
     * <p>定義: shell セル (x, y, z) について、(x, y-1, z) が {@code airCells} に含まれる場合を
     * 天井ブロックとみなす。これにより部屋内部の空気頭上を覆う1ブロック厚の天井が特定される。
     * 床ブロック（直上が空気セル）や壁ブロック（横方向のみ空気セルに隣接）は含まれない。
     *
     * <p>非屋内（{@code currentSpaceEnclosed == false}）の場合は空集合となる。
     */
    private void updateCeilingCullPositions() {
        ceilingCullPositions.clear();
        if (!currentSpaceEnclosed || currentSpaceResult == null) {
            return;
        }
        RoomFloodFill.Result roomResult = currentSpaceResult.getRoomResult();
        if (roomResult == null || !roomResult.isEnclosed()) {
            return;
        }
        LongSet airCells = roomResult.getAirCells();
        LongSet shellCells = roomResult.getShellCells();
        if (airCells.isEmpty() || shellCells.isEmpty()) {
            return;
        }
        // shell セルは固体ブロック。直下が空気セル = プレイヤー頭上を覆う天井。
        // LongOpenHashSet のイテレータで packed long を直接取得し BlockPos 生成を回避する。
        LongIterator it = shellCells.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            long below = BlockPos.asLong(x, y - 1, z);
            if (airCells.contains(below)) {
                ceilingCullPositions.add(packed);
            }
        }
    }

    /**
     * プレイヤー周辺の保護対象ハシゴチェーンをスキャンし、protectedLadderChains と
     * protectedLadderPositions に収集する。
     * 保護対象 = 3個以上連続するハシゴチェーンで、最下段がプレイヤー足元〜足元+1 の範囲。
     * 支え壁（FACING方向の隣接ブロック）も収集する。
     */
    private void scanProtectedLadderChains(net.minecraft.world.level.Level level,
            int blockX, int blockY, int blockZ, int playerFeetY) {
        int radius = com.topdownview.state.SpaceDebugState.STAIR_SCAN_RADIUS;
        int minY = playerFeetY;
        int maxY = playerFeetY + 1;
        int levelMinY = level.getMinBuildHeight();
        int levelMaxY = level.getMaxBuildHeight() - 1;

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        Set<Long> scannedColumns = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = blockX + dx;
                int z = blockZ + dz;
                long columnKey = BlockPos.asLong(x, 0, z);
                if (scannedColumns.contains(columnKey)) {
                    continue;
                }

                // 足元Y〜足元Y+1 の範囲でハシゴを探す
                for (int checkY = Math.max(minY, levelMinY); checkY <= Math.min(maxY, levelMaxY); checkY++) {
                    checkPos.set(x, checkY, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (!state.is(Blocks.LADDER)) {
                        continue;
                    }

                    // チェーン全体を取得
                    int chainLength = LadderHelper.getChainLengthPublic(checkPos, level);
                    if (chainLength < 3) {
                        continue;
                    }
                    int chainBottomY = LadderHelper.getChainBottomY(checkPos, level);
                    if (chainBottomY < playerFeetY || chainBottomY > playerFeetY + 1) {
                        continue;
                    }

                    scannedColumns.add(columnKey);

                    int chainTopY = chainBottomY + chainLength - 1;
                    Direction facing = state.getValue(LadderBlock.FACING);
                    ProtectedLadderChain chain = new ProtectedLadderChain(
                            x, z, chainBottomY, chainTopY, facing);
                    protectedLadderChains.add(chain);

                    // ハシゴ自身 + 支え壁を protectedLadderPositions に登録
                    for (int y = chainBottomY; y <= chainTopY; y++) {
                        protectedLadderPositions.add(new BlockPos(x, y, z));
                        protectedLadderPositions.add(new BlockPos(chain.wallX, y, chain.wallZ));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 指定ブロックが階段除外リストに含まれるか。
     * Y範囲チェックは updateStairExclusion で行うため、ここではSetの包含のみ。
     */
    private boolean isExcludedStairBlock(BlockPos pos, int playerFeetY) {
        if (excludedStairBlocks.isEmpty()) {
            return false;
        }
        return excludedStairBlocks.contains(pos);
    }

    /**
     * 指定された階段ステップが室内歩行用階段か判定する。
     * ステップの頭上空間（step+1 または step+2）が部屋内部空気セル集合（airCells）に含まれている場合のみ室内とみなす。
     * 屋根の階段（屋根裏部屋等の屋根）や屋外の階段を除外するための判定。
     */
    private boolean isIndoorStairStep(LongSet airCells, BlockPos step) {
        if (airCells == null || airCells.isEmpty()) {
            return false;
        }
        int x = step.getX();
        int y = step.getY();
        int z = step.getZ();
        long posAbove1 = BlockPos.asLong(x, y + 1, z);
        long posAbove2 = BlockPos.asLong(x, y + 2, z);
        return airCells.contains(posAbove1) || airCells.contains(posAbove2);
    }

    private void updateEntityCulling(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (mc.level == null || mc.player == null) {
            return;
        }

        if (!contextValid) {
            return;
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        int playerFeetBlockY = (int) Math.floor(mc.player.getY());

        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Player && entity == mc.player) {
                    continue;
                }

                if (entity instanceof Cullable) {
                    Cullable cullable = (Cullable) entity;

                    if (!isCullableEntityType(entity)) {
                        cullable.topdownview_setCulled(false);
                        continue;
                    }

                    boolean isMob = entity instanceof Mob;
                    boolean shouldCull;

                    if (isMob) {
                        shouldCull = shouldCullMob(entity, mc, playerFeetBlockY);
                    } else {
                        shouldCull = shouldCullDecorativeEntity(entity, pX, pY, pZ, cX, cY, cZ);
                    }

                    cullable.topdownview_setCulled(shouldCull);
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // エンティティリストが別スレッドで変更された - 次フレームで再試行
            LOGGER.debug("[TopDownView] Entity list modified concurrently during culling update, will retry next frame", e);
        }
    }

    private boolean shouldCullMob(Entity entity, Minecraft mc, int playerFeetBlockY) {
        int entityBlockY = entity.getBlockY();

        // 1. プレイヤーと同一高さ以下、またはワールドがない場合はカリングしない
        if (entityBlockY <= playerFeetBlockY + 1 || mc.level == null) {
            return false;
        }

        int ex = entity.getBlockX();
        int ez = entity.getBlockZ();

        // 2. 接地チェックおよび足元ブロックのカリング判定
        boolean grounded = false;
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            entityGroundedPos.set(ex, entityBlockY - yOffset, ez);
            if (!mc.level.getBlockState(entityGroundedPos).isAir()) {
                grounded = true;
                // 足元の接地ブロックがカリング（消去）されている場合、Mobもカリング（非表示）
                if (isBlockCulled(entityGroundedPos, mc.level)) {
                    return true;
                }
                break;
            }
        }

        // 接地していない（空中に浮かんでいる等の）Mobはカリングしない
        if (!grounded) {
            return false;
        }

        // 3. Mobとプレイヤーの間の高さ（playerFeetBlockY + 1 〜 entityBlockY - 1）の軸線上に
        //    カリングされている天井/屋根ブロックが存在するかチェック
        for (int y = playerFeetBlockY + 1; y < entityBlockY; y++) {
            entityGroundedPos.set(ex, y, ez);
            if (isBlockCulled(entityGroundedPos, mc.level)) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldCullDecorativeEntity(Entity entity, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        Vec3 entityPos = entity.position();
        double ex = entityPos.x;
        double ey = entityPos.y;
        double ez = entityPos.z;

        double dx = ex - pX;
        double dy = ey - pY;
        double dz = ez - pZ;
        double distToPlayerSq = dx * dx + dy * dy + dz * dz;

        if (distToPlayerSq <= ENTITY_PROTECTION_RADIUS_SQ) {
            return false;
        }

        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                ex, ey, ez,
                pX, pY, pZ,
                cX, cY, cZ);

        if (normalizedDistSq < 0) {
            return false;
        }

        return normalizedDistSq <= 1.0;
    }



    private boolean isCullableEntityType(Entity entity) {
        if (entity instanceof Mob) {
            return Config.isMobCullingEnabled();
        }
        return entity instanceof ItemFrame
            || entity instanceof GlowItemFrame
            || entity instanceof ArmorStand
            || entity instanceof Painting;
    }

    public void reset() {
        cullingCache.clear();
        fadeCache.clear();
        excludedStairBlocks.clear();
        detectedStaircases = List.of();
        protectedLadderChains.clear();
        protectedLadderPositions.clear();
        ceilingCullPositions.clear();
        currentSpaceEnclosed = false;
        currentSpaceResult = null;
        LadderHelper.clearCache();
        resetLastBlockCoords();
        contextValid = false;
        playerX = 0.0;
        playerY = 0.0;
        playerZ = 0.0;
        cameraX = 0.0;
        cameraY = 0.0;
        cameraZ = 0.0;
    }

    public int getCulledBlockCount() {
        return cullingCache.getCulledCount();
    }

    public int getCacheSize() {
        return cullingCache.size();
    }

    public float getFadeAlpha(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            return 1.0f;
        }

        if (ModState.STATUS.isMiningMode()) {
            return 1.0f;
        }

        long posLong = pos.asLong();
        Float cached = fadeCache.getFadeAlpha(posLong);
        if (cached != null) {
            return cached;
        }

        if (Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(pos, playerX, playerY, playerZ)) {
            if (level != null && !isProtectedBlock(pos, level.getBlockState(pos), playerY, level)) {
                float alpha = (float) Config.getPlayerNearTranslucencyAlpha();
                fadeCache.putFadeAlpha(posLong, alpha);
                return alpha;
            }
        }

        if (!Config.isFadeEnabled()) {
            return 1.0f;
        }

        BlockState state = level.getBlockState(pos);
        float alpha = calculateFadeAlpha(pos, level, state,
                playerX, playerY, playerZ, cameraX, cameraY, cameraZ);
        fadeCache.putFadeAlpha(posLong, alpha);

        return alpha;
    }

    public boolean isHittableFadeBlock(BlockPos pos, BlockGetter level) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()
                || (!Config.isFadeEnabled() && !Config.isPlayerNearTranslucencyEnabled()
                        && !Config.isStaircaseOccludeEnabled() && !Config.isLadderOccludeEnabled()
                        && !Config.isTreeOccludeEnabled())) {
            return false;
        }

        if (ModState.STATUS.isMiningMode()) {
            return false;
        }

        if (level == null) {
            return false;
        }

        // 屋内検出天井ブロックは isBlockCulled で一律カリング(alpha=0相当)されているため、
        // これを「フェード半透明ヒット対象」とするとカメラ〜プレイヤー間のアイコン完全透過用途で
        // 見えない天井がレイキャストを遮ってしまう。alpha=0 < fadeBlockHitThreshold と同義で非ヒットとする。
        if (!ceilingCullPositions.isEmpty() && ceilingCullPositions.contains(pos.asLong())) {
            return false;
        }

        float alpha = getFadeAlpha(pos, level);
        return alpha < 1.0f && alpha > this.cachedFadeBlockHitThreshold;
    }

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocks(BlockGetter level) {
        boolean fadeEnabled = Config.isFadeEnabled();
        boolean stairOccludeEnabled = Config.isStaircaseExclusionEnabled() && Config.isStaircaseOccludeEnabled();
        boolean ladderOccludeEnabled = Config.isLadderOccludeEnabled();
        boolean treeOccludeEnabled = Config.isTreeOccludeEnabled();
        boolean playerNearTranslucencyEnabled = Config.isPlayerNearTranslucencyEnabled();

        if (!ModState.STATUS.isEnabled() || ModState.STATUS.isMiningMode()
                || (!fadeEnabled && !stairOccludeEnabled && !ladderOccludeEnabled && !treeOccludeEnabled && !playerNearTranslucencyEnabled)) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        if (level == null || !contextValid) {
            fadeCache.clearFadeBlocks();
            return fadeCache.getFadeBlocksCache();
        }

        double pX = this.playerX;
        double pY = this.playerY;
        double pZ = this.playerZ;
        double cX = this.cameraX;
        double cY = this.cameraY;
        double cZ = this.cameraZ;

        // プレイヤー/カメラのブロック座標が変化した時のみフェードブロックを再構築。
        // プレイヤー静止時は毎tickの3重ループ全走査を回避（地下での主要な負荷源）。
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            int pBX = (int) Math.floor(pX);
            int pBY = (int) Math.floor(pY);
            int pBZ = (int) Math.floor(pZ);
            int cBX = (int) Math.floor(cX);
            int cBY = (int) Math.floor(cY);
            int cBZ = (int) Math.floor(cZ);
            if (pBX == lastFadePBlockX && pBY == lastFadePBlockY && pBZ == lastFadePBlockZ
                    && cBX == lastFadeCBlockX && cBY == lastFadeCBlockY && cBZ == lastFadeCBlockZ) {
                return fadeCache.getFadeBlocksCache();
            }
            lastFadePBlockX = pBX;
            lastFadePBlockY = pBY;
            lastFadePBlockZ = pBZ;
            lastFadeCBlockX = cBX;
            lastFadeCBlockY = cBY;
            lastFadeCBlockZ = cBZ;
        }

        fadeCache.clearFadeBlocks();

        // 階段視線遮蔽ブロック収集を先に行う（優先度高、フェード有無に関わらず動作）
        if (stairOccludeEnabled) {
            collectStairOcclusionBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        // ハシゴ視線遮蔽ブロック収集（フェード有無に関わらず動作）
        if (ladderOccludeEnabled) {
            collectLadderOcclusionBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        // 自然木視線遮蔽ブロック収集（フェード有無に関わらず動作）
        if (treeOccludeEnabled) {
            collectTreeOcclusionBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        // フェードブロック収集（フェードまたはプレイヤー周囲半透明化が有効時のみ）
        if ((fadeEnabled || playerNearTranslucencyEnabled) && !fadeCache.isFadeBlocksFull()) {
            collectFadeBlocks(level, pX, pY, pZ, cX, cY, cZ);
        }

        return fadeCache.getFadeBlocksCache();
    }

    private void collectFadeBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        int radiusH = this.cachedCylinderRadiusHorizontal;
        int radiusV = this.cachedCylinderRadiusVertical;
        int margin = 2;

        int minX = (int) Math.floor(Math.min(pX, cX)) - radiusH - margin;
        int maxX = (int) Math.floor(Math.max(pX, cX)) + radiusH + margin;
        int minY = (int) Math.floor(Math.min(pY, cY)) - 1;
        int maxY = (int) Math.floor(Math.max(pY, cY)) + radiusV + margin;
        int minZ = (int) Math.floor(Math.min(pZ, cZ)) - radiusH - margin;
        int maxZ = (int) Math.floor(Math.max(pZ, cZ)) + radiusH + margin;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    boolean fadeEnabled = Config.isFadeEnabled();
                    boolean isNearTarget = Config.isPlayerNearTranslucencyEnabled() && isPlayerNearBlock(mutablePos, pX, pY, pZ);

                    // 1. 重い getBlockState を呼ぶ前に、数学的なアルファ値を先に計算して判定する
                    double normalizedDistSq = 0.0;
                    float tempAlpha = 1.0f;
                    if (fadeEnabled) {
                        normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                                x + 0.5, y + 0.5, z + 0.5,
                                pX, pY, pZ, cX, cY, cZ);
                        double pyramidFactor = PyramidProtectionCalc.calculateProtectionFactor(
                                mutablePos, pX, pY, pZ, cX, cZ);

                        float cylinderAlpha;
                        if (normalizedDistSq < 0 || normalizedDistSq > 1.0) {
                            cylinderAlpha = 1.0f;
                        } else if (normalizedDistSq <= this.cachedFadeStart) {
                            cylinderAlpha = (float) this.cachedFadeNearAlpha;
                        } else {
                            double t = (normalizedDistSq - this.cachedFadeStart) / (1.0 - this.cachedFadeStart);
                            cylinderAlpha = (float) (this.cachedFadeNearAlpha + t * (1.0 - this.cachedFadeNearAlpha));
                        }
                        tempAlpha = (float) Math.max(cylinderAlpha, pyramidFactor);
                    }

                    // フェード対象ブロック、または境界マージン内のブロックか判定
                    boolean isTarget = false;
                    float finalAlpha = tempAlpha;

                    if (isNearTarget) {
                        isTarget = true;
                        finalAlpha = (float) Config.getPlayerNearTranslucencyAlpha();
                    } else if (fadeEnabled) {
                        if (tempAlpha > 0.0f && tempAlpha < 1.0f) {
                            isTarget = true;
                        } else if (tempAlpha >= 1.0f) {
                            // カリング境界のすぐ外側: メッシュ再構築遅延による点滅防止用安全マージン
                            if (normalizedDistSq > 1.0 && normalizedDistSq <= 1.5) {
                                isTarget = true;
                                finalAlpha = 1.0f;
                            }
                        }
                    }

                    if (!isTarget) {
                        continue;
                    }

                    // 2. フェード対象の場合のみ、重い getBlockState を呼び出す
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    // 3. 保護対象ブロック（プレイヤー足元、Trapdoor、インタラクト可能など）はフェードさせない
                    if (isProtectedBlock(mutablePos, state, pY, level)) {
                        continue;
                    }

                    // 3a. 屋内検出天井ブロックは isBlockCulled で一律カリング(alpha=0)されるため、
                    // フェードキャッシュに登録して半透明描画すると「一律削除」の意図と矛盾する。
                    // ここでスキップし、チャンクメッシュ再構築で非表示にする。
                    if (!ceilingCullPositions.isEmpty()
                            && ceilingCullPositions.contains(mutablePos.asLong())) {
                        continue;
                    }

                    // 3b. ハシゴ視線遮蔽半透明化対象ブロックは collectLadderOcclusionBlocks で処理されるためスキップ
                    // （collectFadeBlocks によるフェードアルファ上書きを防ぐ）
                    if (Config.isLadderOccludeEnabled() && !protectedLadderPositions.isEmpty()
                            && protectedLadderPositions.contains(mutablePos)) {
                        continue;
                    }

                    // 3c. 階段視線遮蔽半透明化対象ブロックは collectStairOcclusionBlocks で処理されるためスキップ
                    if (Config.isStaircaseOccludeEnabled() && !excludedStairBlocks.isEmpty()
                            && excludedStairBlocks.contains(mutablePos)) {
                        continue;
                    }

                    // 3d. 自然木視線遮蔽半透明化対象ブロック（視線遮蔽幹に属する原木）は
                    // collectTreeOcclusionBlocks で処理されるためスキップ
                    if (Config.isTreeOccludeEnabled() && !occludedTreeTrunkColumns.isEmpty()
                            && protectedTreeLogPositions.contains(mutablePos.asLong())) {
                        long columnKey = BlockPos.asLong(mutablePos.getX(), 0, mutablePos.getZ());
                        if (occludedTreeTrunkColumns.contains(columnKey)) {
                            continue;
                        }
                    }

                    // 4. 葉ブロックのFASTグラフィックス設定の処理
                    if (finalAlpha < 1.0f && state.is(net.minecraft.tags.BlockTags.LEAVES) &&
                            net.minecraft.client.Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FAST) {
                        continue;
                    }

                    // 5. キャッシュへ追加
                    fadeCache.putFadeBlock(mutablePos.asLong(), finalAlpha);

                    if (fadeCache.isFadeBlocksFull()) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 階段視線遮蔽ブロック収集。
     * 階段シーケンス単位で視線遮蔽判定を行い、シーケンス内のいずれかのブロックが視線を遮る場合、
     * そのシーケンス全体（範囲内の段）を occludeAlpha で半透明化する。
     * 視線を遮らないシーケンスは 1.0（不透明）で fadeCache に追加し、チャンクメッシュ再構築を不要化する。
     */
    private void collectStairOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        if (detectedStaircases.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getStaircaseOccludeAlpha();

        for (Staircase stair : detectedStaircases) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            // シーケンス内のいずれかのブロック（範囲内）が視線を遮るか判定
            boolean anyOccluding = false;
            for (BlockPos step : stair.getSteps()) {
                if (!excludedStairBlocks.contains(step)) {
                    continue;
                }
                if (isStairOccludingView(step, cX, cY, cZ, pX, pY, pZ)) {
                    anyOccluding = true;
                    break;
                }
            }
            // シーケンス全体（範囲内の段）を同じアルファで登録
            float alpha = anyOccluding ? occludeAlpha : 1.0f;
            for (BlockPos step : stair.getSteps()) {
                if (fadeCache.isFadeBlocksFull()) {
                    return;
                }
                if (!excludedStairBlocks.contains(step)) {
                    continue;
                }
                BlockState state = level.getBlockState(step);
                if (state.isAir()) {
                    continue;
                }
                fadeCache.putFadeBlock(step.asLong(), alpha);
            }
        }
    }

    /**
     * ハシゴ視線遮蔽ブロック収集。
     * ハシゴチェーン単位で視線遮蔽判定を行い、チェーン内のいずれかのハシゴが視線を遮る場合、
     * そのチェーン全体（ハシゴ自身＋支え壁）を ladderOccludeAlpha で半透明化する。
     */
    private void collectLadderOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        if (protectedLadderChains.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getLadderOccludeAlpha();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (ProtectedLadderChain chain : protectedLadderChains) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            // チェーン内のいずれかのハシゴまたは支え壁が視線を遮るか判定
            boolean anyOccluding = false;
            for (int y = chain.bottomY; y <= chain.topY; y++) {
                mutablePos.set(chain.x, y, chain.z);
                if (isStairOccludingView(mutablePos, cX, cY, cZ, pX, pY, pZ)) {
                    anyOccluding = true;
                    break;
                }
            }
            if (!anyOccluding) {
                for (int y = chain.bottomY; y <= chain.topY; y++) {
                    mutablePos.set(chain.wallX, y, chain.wallZ);
                    if (isStairOccludingView(mutablePos, cX, cY, cZ, pX, pY, pZ)) {
                        anyOccluding = true;
                        break;
                    }
                }
            }
            // チェーン全体を同じアルファで登録（遮蔽時は occludeAlpha、非遮蔽時は 1.0 で不透明）。
            // 非遮蔽時も alpha=1.0 で translucent 経路描画することでチャンクメッシュ再構築を不要化。
            float alpha = anyOccluding ? occludeAlpha : 1.0f;
            // チェーン全体（ハシゴ自身＋支え壁）をフェードキャッシュに追加
            for (int y = chain.bottomY; y <= chain.topY; y++) {
                if (fadeCache.isFadeBlocksFull()) {
                    return;
                }
                // ハシゴ自身
                mutablePos.set(chain.x, y, chain.z);
                BlockState ladderState = level.getBlockState(mutablePos);
                if (!ladderState.isAir()) {
                    fadeCache.putFadeBlock(mutablePos.asLong(), alpha);
                }
                // 支え壁
                mutablePos.set(chain.wallX, y, chain.wallZ);
                BlockState wallState = level.getBlockState(mutablePos);
                if (!wallState.isAir() && wallState.getFluidState().isEmpty()) {
                    fadeCache.putFadeBlock(mutablePos.asLong(), alpha);
                }
            }
        }
    }

    /**
     * 自然木視線遮蔽ブロック収集。
     * 保護対象の自然木原木位置ごとに視線遮蔽判定を行い、視線を遮る場合は
     * treeOccludeAlpha で半透明化する。遮らない場合は 1.0（不透明）で fadeCache に追加し、
     * チャンクメッシュ再構築を不要化する。
     */
    private void collectTreeOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        if (occludedTreeTrunkColumns.isEmpty() || protectedTreeTrunks.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getTreeOccludeAlpha();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
            long columnKey = BlockPos.asLong(trunk.x, 0, trunk.z);
            if (!occludedTreeTrunkColumns.contains(columnKey)) {
                continue;
            }
            for (int y = trunk.bottomY; y <= trunk.topY; y++) {
                if (fadeCache.isFadeBlocksFull()) {
                    return;
                }
                mutablePos.set(trunk.x, y, trunk.z);
                long posLong = mutablePos.asLong();
                if (!protectedTreeLogPositions.contains(posLong)) {
                    continue;
                }
                BlockState state = level.getBlockState(mutablePos);
                if (state.isAir()) {
                    continue;
                }
                fadeCache.putFadeBlock(posLong, occludeAlpha);
            }
        }
    }

    /**
     * 視線遮蔽判定: カメラ→プレイヤーの視線レイがブロックの拡張AABBを通過するか。
     * スラブ法によるレイ-AABB交差判定。カメラ〜プレイヤー間の範囲のみ判定。
     * AABB は各面に 0.5 ブロックのマージンを持ち、実効幅 2.0 ブロックで判定する。
     */
    private boolean isStairOccludingView(BlockPos pos, double cX, double cY, double cZ, double pX, double pY, double pZ) {
        double minX = pos.getX() - 0.5;
        double minY = pos.getY() - 0.5;
        double minZ = pos.getZ() - 0.5;
        double maxX = pos.getX() + 1.5;
        double maxY = pos.getY() + 1.5;
        double maxZ = pos.getZ() + 1.5;

        double dirX = pX - cX;
        double dirY = pY - cY;
        double dirZ = pZ - cZ;
        double rayLengthSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (rayLengthSq < 1.0E-12) {
            // カメラとプレイヤーが同一位置: 視線なし
            return false;
        }

        // t[0]=tmin, t[1]=tmax。カメラ〜プレイヤー間の範囲のみ判定
        double[] t = {0.0, 1.0};

        if (!slabIntersect(cX, dirX, minX, maxX, t)) return false;
        if (!slabIntersect(cY, dirY, minY, maxY, t)) return false;
        if (!slabIntersect(cZ, dirZ, minZ, maxZ, t)) return false;

        // 拡張AABBの退出点がプレイヤー位置(t=1.0)より手前の場合のみ遮蔽と判定。
        // 退出点≈1.0は拡張AABBがプレイヤー位置を含んでいるだけ（隣接）なので除外。
        return t[1] < 0.999;
    }

    /**
     * スラブ法による1軸のレイ-AABB交差判定。
     * t[0]=tmin, t[1]=tmax を直接更新する（呼び出し側で配列を再利用）。
     * レイが軸に平行な場合は原点がスラブ内にあるかのみ判定。
     */
    private static boolean slabIntersect(double origin, double dir, double min, double max, double[] t) {
        if (Math.abs(dir) < 1.0E-9) {
            // レイが軸に平行: 原点がスラブ内にあるか
            return origin >= min && origin <= max;
        }
        double invDir = 1.0 / dir;
        double t1 = (min - origin) * invDir;
        double t2 = (max - origin) * invDir;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        t[0] = Math.max(t[0], t1);
        t[1] = Math.min(t[1], t2);
        return t[0] <= t[1];
    }

    public it.unimi.dsi.fastutil.longs.Long2FloatMap getFadeBlocksCache() {
        return fadeCache.getFadeBlocksCache();
    }

    public boolean isEntityCulled(Entity entity) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            return false;
        }
        if (entity instanceof Cullable) {
            return ((Cullable) entity).topdownview_isCulled();
        }
        return false;
    }

    /**
     * 指定されたブロック位置がカリング境界のすぐ外側（シリンダー正規化距離の二乗が 1.0 から 1.5 の間）にあるかを判定します。
     * メッシュの再構築遅延によるブロックの一瞬の消失（点滅）を防ぐための安全マージンとして使用します。
     */
    private boolean isNearCullingBoundary(BlockPos pos, double pX, double pY, double pZ,
            double cX, double cY, double cZ) {
        double normalizedDistSq = CylinderCalculator.getNormalizedDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                pX, pY, pZ, cX, cY, cZ);
        // シリンダー外（normalizedDistSq > 1.0）だが、境界の近く（1.5以内）のブロック
        return normalizedDistSq > 1.0 && normalizedDistSq <= 1.5;
    }

    /**
     * 保護対象ハシゴチェーンの情報。
     * ハシゴは同一(x,z)で上下に連続する。支え壁はハシゴのFACINGと逆方向（壁側）の隣接ブロック。
     */
    private static final class ProtectedLadderChain {
        final int x;
        final int z;
        final int bottomY;
        final int topY;
        // 支え壁の(x,z)。FACINGの逆方向（=壁がある方向）に1ブロック隣接。
        final int wallX;
        final int wallZ;

        ProtectedLadderChain(int x, int z, int bottomY, int topY, Direction facing) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
            // LadderBlock.FACING はハシゴが「張り付いている壁」と反対方向を指す。
            // よって壁は FACING の逆方向にある。
            this.wallX = x - facing.getStepX();
            this.wallZ = z - facing.getStepZ();
        }
    }

    /**
     * 保護対象の木の幹情報。同一 (x,z) カラム内の連続する原木で構成される。
     * 視線遮蔽判定時に幹単位で処理される。
     */
    private static final class ProtectedTreeTrunk {
        final int x;
        final int z;
        final int bottomY;
        final int topY;

        ProtectedTreeTrunk(int x, int z, int bottomY, int topY) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
        }
    }
}