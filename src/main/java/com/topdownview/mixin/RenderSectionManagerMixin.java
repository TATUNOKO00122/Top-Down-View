package com.topdownview.mixin;

import com.topdownview.culling.CullingManager;
import com.topdownview.state.ModState;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * Embeddium(Sodium)用 RenderSectionManager Mixin
 * トップダウンビューでのチャンク欠け・高さ欠け（Occlusion Culling）を回避する
 *
 * 注意: トップダウンビューではカメラが壁・天井・地下に埋まるケースが頻発する。
 * CameraMixin で getBlockPosition() をプレイヤー位置に偽装しているため、
 * 「カメラ位置の実際の埋まり」を正確に判定できず、条件付き無効化は
 * カメラ埋め込み時のチャンク欠け（奈落が見える現象）を招く。
 * よって有効時は常時オクルージョンCullingを無効化する。
 */
@SuppressWarnings("all")
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.class, remap = false)
public class RenderSectionManagerMixin {

    /** スケジュール直後にまだ rebuildLists へ載っていない/投入されていない猶予フレーム。 */
    private static final int BATCH_GRACE_FRAMES = 2;

    /** バッチのセクション結果。全セクションが構築済みになるまで保持し、1フレームでまとめて戻す。 */
    @Unique private final ArrayList<ChunkBuildOutput> heldResults = new ArrayList<>();
    @Unique private final LongOpenHashSet heldSections = new LongOpenHashSet();

    @Shadow private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;

    @Shadow private RenderSection getRenderSection(int x, int y, int z) {
        throw new AssertionError();
    }

    /**
     * カメラが壁や地面の中に埋まった際に、そこから見える範囲がないと判定され
     * 周囲や別のセクションのチャンク描画がごっそり欠けるバグを修正する。
     * トップダウンビューが有効な間は、EmbeddiumのOcclusion Culling自体を無効化する。
     */
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void onShouldUseOcclusionCulling(net.minecraft.client.Camera camera, boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 保留時間の計測。結果が無いフレームでは {@code processChunkBuildResults} が呼ばれないため、
     * 必ず呼ばれるここで進める。上限超過時は CullingManager 側が保留を強制解放する。
     */
    @Inject(method = "uploadChunks", at = @At("HEAD"))
    private void onUploadChunks(CallbackInfo ci) {
        CullingManager.tickBatchHold();
    }

    /**
     * カリング断面がセクション境界を跨ぐため、セクションが完了順に別フレームでアップロードされると
     * 継ぎ目に1フレームの穴が残る。CullingManager のバッチに属するセクションの結果だけを取り出して
     * 保留し、バッチの全セクションが構築済みになったら同一フレームでまとめてアップロードする。
     * 無関係なセクション(新規チャンクロード等)は保留しない。
     */
    @Inject(method = "processChunkBuildResults", at = @At("HEAD"))
    private void onProcessChunkBuildResults(ArrayList<ChunkBuildOutput> results, CallbackInfo ci) {
        if (CullingManager.hasPendingBatch()) {
            for (Iterator<ChunkBuildOutput> it = results.iterator(); it.hasNext();) {
                ChunkBuildOutput result = it.next();
                RenderSection section = result.render;
                int sx = section.getChunkX();
                int sy = section.getChunkY();
                int sz = section.getChunkZ();
                if (CullingManager.isBatchSection(sx, sy, sz)) {
                    heldResults.add(result);
                    heldSections.add(SectionPos.asLong(sx, sy, sz));
                    it.remove();
                }
            }

            if (CullingManager.shouldHoldUpload(isBatchComplete())) {
                return;
            }

            results.addAll(heldResults);
            heldResults.clear();
            heldSections.clear();
            CullingManager.commitBatch();
            return;
        }

        if (!heldResults.isEmpty()) {
            results.addAll(heldResults);
            heldResults.clear();
            heldSections.clear();
        }
    }

    /**
     * バッチの全セクションが構築済みか。
     *
     * <p>{@code rebuildLists} の空きだけでは不十分: {@code scheduleRebuild} は {@code pendingUpdate} を
     * 立てるだけで、リストへの追加は {@code uploadChunks} の後に走る {@code update()} の仕事なので、
     * スケジュール直後は空に見える。そこで {@link #BATCH_GRACE_FRAMES} の猶予を置いたうえで、
     * 予算超過でまだ未投入のセクション(リストに残っている)を未完了、投入済みで未処理の
     * セクション({@code getBuildCancellationToken()} が非 null)を未完了として扱う。
     * 保留済み({@code heldSections})は処理を止めているためトークンが残るが、構築完了なので除外する。
     */
    private boolean isBatchComplete() {
        if (CullingManager.getBatchFrames() < BATCH_GRACE_FRAMES) {
            return false;
        }
        if (isNotEmpty(ChunkUpdateType.REBUILD) || isNotEmpty(ChunkUpdateType.IMPORTANT_REBUILD)) {
            return false;
        }
        LongIterator it = CullingManager.getBatchSections().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (heldSections.contains(key)) {
                continue;
            }
            RenderSection section = getRenderSection(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
            if (section == null || section.isDisposed()) {
                continue;
            }
            if (section.getBuildCancellationToken() != null) {
                return false;
            }
        }
        return true;
    }

    private boolean isNotEmpty(ChunkUpdateType type) {
        ArrayDeque<RenderSection> queue = rebuildLists.get(type);
        return queue != null && !queue.isEmpty();
    }
}
