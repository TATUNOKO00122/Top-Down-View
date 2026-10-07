package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.culling.FadeTransitionController;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.util.PerfMonitor;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;

/**
 * 消失/復元フラッシュ描画(RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS)
 *
 * <p>ブロックが消える瞬間(α1→0)と戻る瞬間(α0→1)に半透明ゴーストでフラッシュする。
 * 位置ごとに α を保持し目標値へ時間比例で動かすため、境界移動で消失/復元が交互に
 * 起きても α が飛ばず点滅しない。カリング判定には一切介入しない(判定は生のまま)。
 *
 * <ul>
 *   <li>消失: カリング集合の位置。開始時 α=1 から 0 へ。</li>
 *   <li>復元: 集合から外れた位置。α=0 から 1 へ。メッシュ再構築が戻るまでの穴を覆い、
 *       メッシュが戻れば ポリゴンオフセット(奥)で隠れて見えない。</li>
 * </ul>
 */
public final class TranslucentBlockRenderer {

    private static final RandomSource RANDOM = RandomSource.create();

    private static final long INVALID = Long.MIN_VALUE;

    /** これ以下になったゴーストは描画を止める(完全透明)。 */
    private static final float ALPHA_EPSILON = 0.02f;

    /**
     * ゴーストを描画する最大距離(プレイヤー基準)。
     *
     * <p>メッシュ専用ホールドもこの距離に合わせる。遠方までブロックを保留すると、覆うゴーストが
     * 無いまま穴だけが残り、視点移動で境界が掃引して「穴が奥から波状に来る」ように見える。
     * 範囲外の復元はホールドせず即座にメッシュへ戻す。
     */
    public static final double GHOST_RENDER_DISTANCE = 24.0;

    /** 位置ごとの現在のゴーストα。遷移方向が変わっても連続させる(点滅防止)。 */
    private static final Long2FloatOpenHashMap GHOST_ALPHA = new Long2FloatOpenHashMap();

    /** 今フレームに描画中のゴースト位置(isHittableFadeBlock / レイキャスト用)。 */
    private static final LongOpenHashSet GHOST_VISIBLE = new LongOpenHashSet();

    /** 今フレーム処理した位置。使われなくなった α を掃除するための作業用。 */
    private static final LongOpenHashSet SEEN = new LongOpenHashSet();

    /** 消失フェード候補位置の作業用セット(再利用)。 */
    private static final LongOpenHashSet FADE_CANDIDATES = new LongOpenHashSet();
    /** 今フレームに近接半透明表示されているブロック位置の集合（近接ブロック同士の面カリング用）。 */
    private static final LongOpenHashSet NEAR_BLOCKS = new LongOpenHashSet();
    private static final NearBlockGetter NEAR_BLOCK_GETTER = new NearBlockGetter();

    private static long lastFrameNanos;

    // 描画スレッド専用。ラッパーをフレーム毎に生成しないよう再利用する。
    private static final AlphaVertexConsumer ALPHA_CONSUMER = new AlphaVertexConsumer();
    private static final BlockPos.MutableBlockPos FADE_POS = new BlockPos.MutableBlockPos();

    /** 1フレーム内にテッセレートするゴーストブロックの最大数。 */
    public static final int MAX_GHOST_RENDER_COUNT = 250;

    /** 今フレーム描画対象の最上面ゴースト位置(作業用バッファ)。 */
    private static final LongArrayList TOP_GHOSTS = new LongArrayList(MAX_GHOST_RENDER_COUNT);
    /** 今フレーム描画対象の隠れている下層ゴースト位置(作業用バッファ)。 */
    private static final LongArrayList COVERED_GHOSTS = new LongArrayList(MAX_GHOST_RENDER_COUNT);
    private static final BlockPos.MutableBlockPos FADE_POS_ABOVE = new BlockPos.MutableBlockPos();

    /** 今フレームにテッセレートしたゴースト数(ラムダ内から加算するための再利用ホルダー)。 */
    private static final int[] GHOST_COUNT = new int[1];

    private TranslucentBlockRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 次元変更時に遷移フェードの状態を破棄する。 */
    public static void clearTransitionState() {
        GHOST_ALPHA.clear();
        GHOST_VISIBLE.clear();
        SEEN.clear();
        FADE_CANDIDATES.clear();
        NEAR_BLOCKS.clear();
        TOP_GHOSTS.clear();
        COVERED_GHOSTS.clear();
        NEAR_BLOCK_GETTER.set(null, null);
        lastFrameNanos = 0L;
    }

    /**
     * 上空から見てブロックが隠れているか(真上が固体ブロックまたはフェードブロックで覆われているか)を判定する。
     */
    private static boolean isCoveredFromAbove(BlockAndTintGetter level, int bx, int by, int bz, LongSet fadePositions) {
        FADE_POS_ABOVE.set(bx, by + 1, bz);
        BlockState aboveState = level.getBlockState(FADE_POS_ABOVE);
        if (aboveState.isAir()) {
            return false;
        }
        long aboveLong = FADE_POS_ABOVE.asLong();
        if (fadePositions != null && fadePositions.contains(aboveLong)) {
            return true;
        }
        return aboveState.isSolidRender(level, FADE_POS_ABOVE);
    }

    /** 単一ゴーストブロックを描画する。 */
    private static void renderSingleGhost(
            long posLong,
            Minecraft mc,
            PoseStack poseStack,
            BlockRenderDispatcher blockRenderer,
            Vec3 cameraPos,
            boolean nearTransEnabled) {
        float alpha = GHOST_ALPHA.getOrDefault(posLong, 0.0f);
        if (alpha <= ALPHA_EPSILON) {
            return;
        }
        GHOST_VISIBLE.add(posLong);
        FADE_POS.set(BlockPos.getX(posLong), BlockPos.getY(posLong), BlockPos.getZ(posLong));
        boolean isNear = nearTransEnabled && NEAR_BLOCKS.contains(posLong);
        if (isNear) {
            renderFadeBlock(NEAR_BLOCK_GETTER, FADE_POS, poseStack, blockRenderer, ALPHA_CONSUMER, alpha, cameraPos, true);
        } else {
            renderFadeBlock(mc.level, FADE_POS, poseStack, blockRenderer, ALPHA_CONSUMER, alpha, cameraPos, false);
        }
        GHOST_COUNT[0]++;
    }

    /** ゴーストが今描画されている位置か(レイキャストのヒット判定用)。 */
    public static boolean isGhostVisible(long posLong) {
        return GHOST_VISIBLE.contains(posLong);
    }

    public static void renderFadeBlocks(RenderLevelStageEvent event) {
        long tRender = System.nanoTime();
        renderFadeBlocksInternal(event);
        PerfMonitor.FADE_RENDER.add(System.nanoTime() - tRender);
    }

    private static void renderFadeBlocksInternal(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        // スクリーン/ポーズ中は描画もα更新も凍結する(時間だけ進んで戻った瞬間に
        // フラッシュが一斉に消えるのを防ぐ)。
        if (mc.screen != null || mc.isPaused()) {
            lastFrameNanos = 0L;
            return;
        }

        TopDownCuller culler = TopDownCuller.getInstance();
        LongOpenHashSet fadePositions = culler.getCollectedFadePositions();
        FadeTransitionController tracker = culler.getFadeController();
        PerfMonitor.recordFadeBlocks(fadePositions.size());

        boolean nearTransEnabled = Config.isPlayerNearTranslucencyEnabled() && !culler.isDisableIndoorNearActive();
        boolean fadeEnabled = Config.isFadeEnabled() && !culler.isDisableIndoorFadeActive();
        float transitionMs = (float) (Config.getFadeFlashDuration() * 1000.0);

        if (!fadeEnabled && !nearTransEnabled) {
            clearTransitionState();
            return;
        }
        if (!nearTransEnabled && transitionMs < 1.0f) {
            clearTransitionState();
            return;
        }

        // フレームレート非依存の変化量。dt をクランプして一時停止復帰での暴れを防ぐ。
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min((System.nanoTime() - lastFrameNanos) / 1.0E9f, 0.1f);
        lastFrameNanos = System.nanoTime();
        float step = transitionMs >= 1.0f ? dt * 1000.0f / transitionMs : 1.0f;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        // カリング側(isBlockCulledForMesh のメッシュホールド距離)と同じ量子化中心にする。基準が
        // ずれるとホールド範囲がゴースト描画範囲をはみ出し、覆うゴーストの無い穴が残る。
        double pX = Math.floor(mc.player.getX()) + 0.5;
        double pY = Math.floor(mc.player.getEyeY()) + 0.5;
        double pZ = Math.floor(mc.player.getZ()) + 0.5;
        double maxDistSq = GHOST_RENDER_DISTANCE * GHOST_RENDER_DISTANCE; // これより遠いフラッシュブロックは描画をスキップ

        VertexConsumer baseConsumer = bufferSource.getBuffer(RenderType.translucent());
        ALPHA_CONSUMER.setDelegate(baseConsumer);

        GHOST_VISIBLE.clear();
        SEEN.clear();
        NEAR_BLOCKS.clear();

        GHOST_COUNT[0] = 0;
        TOP_GHOSTS.clear();
        COVERED_GHOSTS.clear();

        // ==================== カリング集合(消失/継続/近接半透明) ====================
        FADE_CANDIDATES.clear();
        if (fadeEnabled) {
            tracker.forEachActiveFadeOut(FADE_CANDIDATES::add);
        }
        FADE_CANDIDATES.addAll(GHOST_ALPHA.keySet());

        if (nearTransEnabled) {
            int pBX = culler.getCachedPlayerBlockX();
            int pBY = culler.getCachedPlayerFeetY();
            int pBZ = culler.getCachedPlayerBlockZ();
            int rangeH = Config.getPlayerNearTranslucencyRangeHorizontal();
            int rangeV = Config.getPlayerNearTranslucencyRangeVertical();

            for (int dx = -rangeH; dx <= rangeH; dx++) {
                for (int dz = -rangeH; dz <= rangeH; dz++) {
                    for (int dy = 0; dy < rangeV; dy++) {
                        FADE_POS.set(pBX + dx, pBY + dy, pBZ + dz);
                        if (culler.isPlayerNearTranslucencyBlock(FADE_POS, mc.level)) {
                            long posLong = FADE_POS.asLong();
                            FADE_CANDIDATES.add(posLong);
                            NEAR_BLOCKS.add(posLong);
                        }
                    }
                }
            }
        }

        NEAR_BLOCK_GETTER.set(mc.level, NEAR_BLOCKS);

        float targetNearAlpha = (float) Config.getPlayerNearTranslucencyAlpha();

        for (LongIterator iterator = FADE_CANDIDATES.iterator(); iterator.hasNext(); ) {
            long posLong = iterator.nextLong();
            if (fadeEnabled && tracker.isRestoring(posLong)) {
                SEEN.add(posLong);
                continue;
            }

            FADE_POS.set(BlockPos.getX(posLong), BlockPos.getY(posLong), BlockPos.getZ(posLong));
            boolean isNear = nearTransEnabled && NEAR_BLOCKS.contains(posLong);

            if (!isNear && (!fadeEnabled || !fadePositions.contains(posLong))) {
                GHOST_ALPHA.remove(posLong);
                if (fadeEnabled) {
                    tracker.forgetFadeOut(posLong);
                }
                continue;
            }

            long start = fadeEnabled ? tracker.getFadeOutStart(posLong) : INVALID;
            float previous = GHOST_ALPHA.containsKey(posLong) ? GHOST_ALPHA.get(posLong)
                    : (start != INVALID ? 1.0f : 0.0f);

            float targetAlpha = isNear ? targetNearAlpha : 0.0f;
            float alpha = approach(previous, targetAlpha, step);

            if (!isNear && alpha <= ALPHA_EPSILON) {
                GHOST_ALPHA.remove(posLong);
                if (start != INVALID) {
                    tracker.forgetFadeOut(posLong);
                }
                continue;
            }

            SEEN.add(posLong);
            GHOST_ALPHA.put(posLong, alpha);

            int bx = BlockPos.getX(posLong);
            int by = BlockPos.getY(posLong);
            int bz = BlockPos.getZ(posLong);
            double dx = bx + 0.5 - pX;
            double dy = by + 0.5 - pY;
            double dz = bz + 0.5 - pZ;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                continue;
            }

            if (isNear || !isCoveredFromAbove(mc.level, bx, by, bz, fadePositions)) {
                TOP_GHOSTS.add(posLong);
            } else {
                COVERED_GHOSTS.add(posLong);
            }
        }

        // ==================== 復元フラッシュ(集合から外れた位置) ====================
        if (fadeEnabled) {
            long nowMs = System.currentTimeMillis();
            tracker.forEachActiveRestore(posLong -> {
                if (tracker.isRestoreCompleted(posLong, nowMs)) {
                    return;
                }
                float previous = GHOST_ALPHA.containsKey(posLong) ? GHOST_ALPHA.get(posLong) : 0.0f;
                float alpha = approach(previous, 1.0f, step);
                SEEN.add(posLong);
                GHOST_ALPHA.put(posLong, alpha);

                int bx = BlockPos.getX(posLong);
                int by = BlockPos.getY(posLong);
                int bz = BlockPos.getZ(posLong);
                double dx = bx + 0.5 - pX;
                double dy = by + 0.5 - pY;
                double dz = bz + 0.5 - pZ;
                if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                    return;
                }

                if (!isCoveredFromAbove(mc.level, bx, by, bz, fadePositions)) {
                    TOP_GHOSTS.add(posLong);
                } else {
                    COVERED_GHOSTS.add(posLong);
                }
            });
        }

        // 最上面ブロックを最優先で描画
        int topSize = TOP_GHOSTS.size();
        for (int i = 0; i < topSize && GHOST_COUNT[0] < MAX_GHOST_RENDER_COUNT; i++) {
            renderSingleGhost(TOP_GHOSTS.getLong(i), mc, poseStack, blockRenderer, cameraPos, nearTransEnabled);
        }

        // 上限に余裕があれば、隠れている下層ブロックも順次描画
        int coveredSize = COVERED_GHOSTS.size();
        for (int i = 0; i < coveredSize && GHOST_COUNT[0] < MAX_GHOST_RENDER_COUNT; i++) {
            renderSingleGhost(COVERED_GHOSTS.getLong(i), mc, poseStack, blockRenderer, cameraPos, nearTransEnabled);
        }

        PerfMonitor.recordFadeGhosts(GHOST_COUNT[0]);

        // 使われなくなった α を掃除(無制限な増加を防ぐ)。
        if (!GHOST_ALPHA.isEmpty()) {
            LongIterator iterator = GHOST_ALPHA.keySet().iterator();
            while (iterator.hasNext()) {
                if (!SEEN.contains(iterator.nextLong())) {
                    iterator.remove();
                }
            }
        }

        // ゴーストは実ブロックの手前にわずかにずらして描く。同一テクスチャなので α=1 では
        // 実ブロックと見分けが付かず、α を下げるほど手前のゴーストが背景へ混ざって自然に
        // 消える。奥へずらす方式だと、メッシュがブロックを外すフレームまでゴーストが隠れ、
        // 「カリングが先に見え、次フレームでゴースト」という位相ズレが出ていた。
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -1.0f);
        try {
            bufferSource.endBatch(RenderType.translucent());
        } finally {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        }
    }

    /** 現在値を目標値へ step 分だけ近づける(オーバーシュートしない)。 */
    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static void renderFadeBlock(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack poseStack,
            BlockRenderDispatcher blockRenderer,
            AlphaVertexConsumer alphaConsumer,
            float alpha,
            Vec3 cameraPos,
            boolean checkSides) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        BakedModel model = blockRenderer.getBlockModel(state);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        alphaConsumer.setAlpha(alpha);

        long seed = state.getSeed(pos);
        RANDOM.setSeed(seed);

        ModelData modelData = ModelData.EMPTY;
        if (state.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                modelData = be.getModelData();
            }
        }

        // 近接表示ブロック同士のみ checkSides=true かつ NEAR_BLOCK_GETTER で隣接面をカリング。
        // 通常の消失・復元フェードブロックは checkSides=false で全ポリゴンを描画。
        blockRenderer.getModelRenderer().tesselateBlock(
                level,
                model,
                state,
                pos,
                poseStack,
                alphaConsumer,
                checkSides,
                RANDOM,
                seed,
                OverlayTexture.NO_OVERLAY,
                modelData,
                RenderType.translucent());

        poseStack.popPose();
    }

    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

    /**
     * 近接表示ブロック同士の隣接面カリング用プロキシ。
     * 近接集合内のブロックは実状態を返し(面カリング対象)、集合外は空気として返す。
     * これにより、近接ブロック同士が隣接している面のみがカリングされ、外側の露出面のみが描画される。
     */
    private static final class NearBlockGetter extends DelegatingBlockGetter {

        private LongOpenHashSet nearBlocks;

        NearBlockGetter() {
            super(null);
        }

        void set(BlockAndTintGetter delegate, LongOpenHashSet nearBlocks) {
            setDelegate(delegate);
            this.nearBlocks = nearBlocks;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (nearBlocks != null && nearBlocks.contains(pos.asLong())) {
                return delegate.getBlockState(pos);
            }
            return AIR_STATE;
        }
    }
}
