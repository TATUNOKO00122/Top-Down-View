package com.topdownview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.culling.FadeTransitionController;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.util.PerfMonitor;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
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

    private static long lastFrameNanos;

    // 描画スレッド専用。ラッパーをフレーム毎に生成しないよう再利用する。
    private static final AlphaVertexConsumer ALPHA_CONSUMER = new AlphaVertexConsumer();
    private static final BlockPos.MutableBlockPos FADE_POS = new BlockPos.MutableBlockPos();

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
        lastFrameNanos = 0L;
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
        // 走査/差分検出は update()(ティック)側で済んでいる。描画はその集合を読むだけ。
        LongOpenHashSet fadePositions = culler.getCollectedFadePositions();
        FadeTransitionController tracker = culler.getFadeController();
        PerfMonitor.recordFadeBlocks(fadePositions.size());

        float transitionMs = (float) (Config.getFadeFlashDuration() * 1000.0);
        if (transitionMs < 1.0f) {
            // フェード時間0 = 遷移なし(即時)。状態もゴーストも破棄する。
            clearTransitionState();
            return;
        }

        // フレームレート非依存の変化量。dt をクランプして一時停止復帰での暴れを防ぐ。
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min((System.nanoTime() - lastFrameNanos) / 1.0E9f, 0.1f);
        lastFrameNanos = System.nanoTime();
        float step = dt * 1000.0f / transitionMs;

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

        GHOST_COUNT[0] = 0;

        // ==================== カリング集合(消失/継続) ====================
        // 全カリング集合(数千個)を回すのではなく、進行中(GHOST_ALPHA)または新規開始(fadeOutStarts)の
        // 遷移位置のみを走査対象とすることで、毎フレームの無駄なハッシュ検索を排除する。
        FADE_CANDIDATES.clear();
        tracker.forEachActiveFadeOut(FADE_CANDIDATES::add);
        FADE_CANDIDATES.addAll(GHOST_ALPHA.keySet());

        for (LongIterator iterator = FADE_CANDIDATES.iterator(); iterator.hasNext(); ) {
            long posLong = iterator.nextLong();
            if (tracker.isRestoring(posLong)) {
                SEEN.add(posLong);
                continue;
            }
            if (!fadePositions.contains(posLong)) {
                GHOST_ALPHA.remove(posLong);
                tracker.forgetFadeOut(posLong);
                continue;
            }
            long start = tracker.getFadeOutStart(posLong);
            float previous = GHOST_ALPHA.containsKey(posLong) ? GHOST_ALPHA.get(posLong)
                    : (start != INVALID ? 1.0f : 0.0f);
            float alpha = approach(previous, 0.0f, step);
            if (alpha <= ALPHA_EPSILON) {
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
            GHOST_VISIBLE.add(posLong);
            FADE_POS.set(bx, by, bz);
            renderFadeBlock(mc.level, FADE_POS, poseStack, blockRenderer, ALPHA_CONSUMER, alpha, cameraPos);
            GHOST_COUNT[0]++;
        }

        // ==================== 復元フラッシュ(集合から外れた位置) ====================
        // メッシュ再構築が戻るまでの穴を α0→1 で覆う。メッシュが戻ればポリゴンオフセットで
        // ゴーストは奥に隠れる。未完走の復元位置は、境界の揺れ等でカリング集合(fadePositions)に
        // 一時的に入っても復元側が描画を継続する(消失側と相互スキップしてお見合い抜けするのを防ぐ)。
        // 消失側と同様、α帳簿は遠方でも毎フレーム進める(接近時に α=1 で即座に穴を覆えるように)。
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
            GHOST_VISIBLE.add(posLong);
            FADE_POS.set(bx, by, bz);
            renderFadeBlock(mc.level, FADE_POS, poseStack, blockRenderer, ALPHA_CONSUMER, alpha, cameraPos);
            GHOST_COUNT[0]++;
        });

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
            Vec3 cameraPos) {
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

        // 実レベルを渡し、面カリングを無効化(checkSides=false)して立体として描く。
        // checkSides=true だとカリング済み隣接ブロックとの境界面がカットされてペラペラになるため。
        blockRenderer.getModelRenderer().tesselateBlock(
                level,
                model,
                state,
                pos,
                poseStack,
                alphaConsumer,
                false,
                RANDOM,
                seed,
                OverlayTexture.NO_OVERLAY,
                modelData,
                RenderType.translucent());

        poseStack.popPose();
    }
}
