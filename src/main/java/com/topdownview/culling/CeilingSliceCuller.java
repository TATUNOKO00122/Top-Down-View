package com.topdownview.culling;

import com.mojang.logging.LogUtils;
import com.topdownview.culling.geometry.BlockChangeBox;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * 屋内用の天井スライスカリング。
 *
 * <p>「今いる階」の空気セル (プレイヤーが属する部屋のうち、立ち位置以上の高さのもの) から
 * 天井高さを求め、検出された建物の水平バウンディングボックス内で、その高さ以上の非空気ブロックを
 * まとめてカリングする。天井以外のブロック (壁・家具・上階) も同じ高さ以上なら消す水平スライス。
 *
 * <p>母集団を「今いる階」に限ることで、橋の下や地下へ続く道のような下の階の列が天井候補に
 * 混ざらず、デッキやトンネル天井が天井として選ばれたり、階を跨いで床が消えたりしない。
 *
 * <p>カリングは各列の地表高さ (WORLD_SURFACE) まで行い、それ以上は空気なので打ち切る。
 * チャンクビルドワーカーから読まれるため、集合は volatile 参照ごと差し替える。
 */
public final class CeilingSliceCuller {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int NO_CEILING = Integer.MIN_VALUE;

    /** 天井候補を探し始める足元からの高さ。プレイヤー(1.8)の頭の上を空ける。 */
    private static final int HEAD_ROOM = 2;

    /** 1列あたりの天井候補の探索上限ブロック数。見つからなければその列は母集団に入れない。 */
    private static final int MAX_CEILING_SCAN = 32;

    /** 検出範囲は空気セルの AABB なので、外壁の頂部も含めるため壁厚分マージンする。 */
    private static final int RANGE_MARGIN = 1;

    /**
     * 1回の update の時間予算。地下のように天井の上までブロックで埋まった空間では
     * 「天井Y→地表」の走査量が跳ね上がるため、超えたらその空間では処理を諦める。
     */
    private static final long TIME_BUDGET_NANOS = 8_000_000L;

    /** 時間チェックの間隔(セル数)。nanoTime の呼び出しを間引く。 */
    private static final int TIME_CHECK_INTERVAL = 512;

    /** 予算超過後の再試行待ちの初期値。連続で超過するたび倍に伸ばす。 */
    private static final long COOLDOWN_BASE_NANOS = 2_000_000_000L;

    /** 再試行待ちの上限。 */
    private static final long COOLDOWN_MAX_NANOS = 8_000_000_000L;

    /** 諦めた空間のログ出力の最短間隔。 */
    private static final long SKIP_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private volatile LongOpenHashSet slicePositions = new LongOpenHashSet();
    private volatile long generation = 0;

    /** デバッグ用: 直近の update で選ばれた天井 Y (NO_CEILING なら無効) と集計列数。 */
    private volatile int lastCeilingY = NO_CEILING;
    private volatile int lastColumnCount = 0;

    // ==================== 作業量ガード(ティック/描画スレッドのみ) ====================
    private long updateStartNanos;
    private int scanCount;
    private boolean overBudget;
    /** 予算超過後の再試行待ち。連続で超過するたび倍に伸ばす。 */
    private long cooldownNanos = COOLDOWN_BASE_NANOS;
    /** この時刻までは update を再試行しない。 */
    private long nextRetryNanos;
    private long lastSkipLogNanos;

    /** 前回の再構築以降に追加/削除されたセルの範囲。差分再構築に使う(ティック/描画スレッドのみ)。 */
    private final BlockChangeBox pendingChange = new BlockChangeBox();

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public void clearCache() {
        // 空間を離れた/屋外に出たので、作業量ガードのバックオフもやり直す。
        cooldownNanos = COOLDOWN_BASE_NANOS;
        nextRetryNanos = 0L;
        // 既に空ならカリング結果は変わらないため、世代を進めない(無駄な再構築を避ける)。
        if (slicePositions.isEmpty()) {
            return;
        }
        LongIterator removed = slicePositions.iterator();
        while (removed.hasNext()) {
            pendingChange.includeCell(removed.nextLong());
        }
        slicePositions = new LongOpenHashSet();
        generation++;
    }

    /** 差分再構築のために蓄積した変更範囲。消費側で {@link #clearPendingChange()} を呼ぶ。 */
    public BlockChangeBox getPendingChange() {
        return pendingChange;
    }

    public void clearPendingChange() {
        pendingChange.reset();
    }

    public boolean isCeilingSliceBlock(long posLong) {
        LongOpenHashSet set = slicePositions;
        return !set.isEmpty() && set.contains(posLong);
    }

    /** 天井スライス集合の世代番号。値が変わればカリング結果が変わった可能性がある。 */
    public long getGeneration() {
        return generation;
    }

    /**
     * 「今いる階」の空気セルから天井高さを求め、建物の検出範囲内を一括でカリングする。
     *
     * @param level        ワールド
     * @param minPos       建物の検出最小座標 (カリング範囲)
     * @param maxPos       建物の検出最大座標 (カリング範囲)
     * @param floorCells   プレイヤーがいる部屋の空気セル (packed long)。天井候補の集計に使う。
     * @param playerLevelY プレイヤーの立ち位置 (支えている地面の1つ上)。これ未満のセルは集計しない。
     */
    public void update(LevelReader level, BlockPos minPos, BlockPos maxPos, LongSet floorCells,
            int playerLevelY) {
        if (level == null || minPos == null || maxPos == null) {
            apply(new LongOpenHashSet());
            return;
        }
        // 予算超過で諦めた直後はしばらく再試行しない(密な空間での毎probe再スキャンを避ける)。
        if (System.nanoTime() < nextRetryNanos) {
            return;
        }

        updateStartNanos = System.nanoTime();
        scanCount = 0;
        overBudget = false;
        LongOpenHashSet next = new LongOpenHashSet();

        int ceilingY = findDominantCeilingY(level, floorCells, playerLevelY);
        if (overBudget) {
            abort(minPos, maxPos);
            apply(next);
            return;
        }
        if (ceilingY == NO_CEILING) {
            cooldownNanos = COOLDOWN_BASE_NANOS;
            apply(next);
            return;
        }
        int minX = minPos.getX() - RANGE_MARGIN;
        int minZ = minPos.getZ() - RANGE_MARGIN;
        int maxX = maxPos.getX() + RANGE_MARGIN;
        int maxZ = maxPos.getZ() + RANGE_MARGIN;
        int maxBuildHeight = level.getMaxBuildHeight() - 1;

        boolean over = false;
        cullLoop:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // 天井より上を全部消す。列の地表高さまでで打ち切る (その上は空気)。
                int top = Math.min(maxBuildHeight, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                if (top < ceilingY) {
                    top = ceilingY;
                }
                for (int y = ceilingY; y <= top; y++) {
                    if (budgetExceeded()) {
                        over = true;
                        break cullLoop;
                    }
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    next.add(mutablePos.asLong());
                }
            }
        }
        if (over) {
            // 途中結果は破棄する。集合を空にするとこの空間では天井スライスが無効になる。
            abort(minPos, maxPos);
            next.clear();
            apply(next);
            return;
        }
        // 予算内で完了したのでバックオフを初期値に戻す。
        cooldownNanos = COOLDOWN_BASE_NANOS;
        apply(next);
    }

    /**
     * 時間予算の超過を検出する。全セルで nanoTime を呼ばないよう {@link #TIME_CHECK_INTERVAL}
     * セルごとに判定する。超過後は常に true を返す。
     */
    private boolean budgetExceeded() {
        if (overBudget) {
            return true;
        }
        if ((scanCount++ & (TIME_CHECK_INTERVAL - 1)) == 0
                && System.nanoTime() - updateStartNanos > TIME_BUDGET_NANOS) {
            overBudget = true;
        }
        return overBudget;
    }

    /**
     * 処理量が予算を超えた空間を諦める。集合は空のまま(この空間では天井スライス無効)にして、
     * 再試行までクールダウンを置く。連続で超過するたび待ち時間を倍に伸ばす。
     */
    private void abort(BlockPos minPos, BlockPos maxPos) {
        long now = System.nanoTime();
        long applied = cooldownNanos;
        nextRetryNanos = now + applied;
        cooldownNanos = Math.min(cooldownNanos * 2, COOLDOWN_MAX_NANOS);
        if (now - lastSkipLogNanos >= SKIP_LOG_INTERVAL_NANOS) {
            lastSkipLogNanos = now;
            LOGGER.info("[TopDownView] Ceiling slice paused: work >{}ms (scanned={}, space {}x{}, retry in {}s)",
                    TIME_BUDGET_NANOS / 1.0E6, scanCount, maxPos.getX() - minPos.getX() + 1,
                    maxPos.getZ() - minPos.getZ() + 1, applied / 1.0E9);
        }
    }

    /** デバッグ用: 作業量ガードのクールダウン中か。 */
    public boolean isCoolingDown() {
        return System.nanoTime() < nextRetryNanos;
    }

    /**
     * 「今いる階」の列ごとに「頭上の最初の固体ブロック」を天井候補とし、最も多い Y を返す。
     *
     * <p>候補を「列の最上部空気+1」ではなく「立ち位置の頭より上で最初に当たる固体」にするのが要点。
     * 最上部空気基準だと、上に開口や吹き抜けがある列は候補が屋根より上へ、逆に立ち位置レベルの
     * 空気しか無い列(階段の踊り場など)は候補が足元+1へ引っ張られ、実際の天井を外す。頭上基準なら
     * どの列も「その列で今いる階を覆う最初の面」になり、天井を正しく選べる。
     *
     * <p>立ち位置より下のセルは列として採用しない。橋の下の地面や地下へ続く道は立ち位置より下なので
     * 母集団から外れる。最頻値にするのは、部屋ごとに天井高が違っても建物全体で1枚のスライスに
     * まとめるため。最頻値が同数の場合は低い方 (より多くの視界を開く方) を選ぶ。
     */
    private int findDominantCeilingY(LevelReader level, LongSet floorCells, int playerLevelY) {
        if (floorCells == null || floorCells.isEmpty()) {
            lastCeilingY = NO_CEILING;
            lastColumnCount = 0;
            return NO_CEILING;
        }
        // 立ち位置レベルの空気がある列だけを対象にする。
        LongOpenHashSet columns = new LongOpenHashSet();
        for (long cell : floorCells) {
            if (budgetExceeded()) {
                lastCeilingY = NO_CEILING;
                lastColumnCount = columns.size();
                return NO_CEILING;
            }
            if (BlockPos.getY(cell) < playerLevelY) {
                continue;
            }
            columns.add(BlockPos.asLong(BlockPos.getX(cell), 0, BlockPos.getZ(cell)));
        }
        if (columns.isEmpty()) {
            lastCeilingY = NO_CEILING;
            lastColumnCount = 0;
            return NO_CEILING;
        }

        int startY = playerLevelY + HEAD_ROOM;
        int maxY = Math.min(level.getMaxBuildHeight() - 1, startY + MAX_CEILING_SCAN);
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (long column : columns) {
            int x = BlockPos.getX(column);
            int z = BlockPos.getZ(column);
            for (int y = startY; y <= maxY; y++) {
                if (budgetExceeded()) {
                    lastCeilingY = NO_CEILING;
                    lastColumnCount = columns.size();
                    return NO_CEILING;
                }
                mutablePos.set(x, y, z);
                if (!level.getBlockState(mutablePos).isAir()) {
                    counts.addTo(y, 1);
                    break;
                }
            }
        }

        int bestY = NO_CEILING;
        int bestCount = 0;
        for (var entry : counts.long2IntEntrySet()) {
            int y = (int) entry.getLongKey();
            int count = entry.getIntValue();
            if (count > bestCount || (count == bestCount && y < bestY)) {
                bestCount = count;
                bestY = y;
            }
        }
        lastCeilingY = bestY;
        lastColumnCount = columns.size();
        return bestY;
    }

    /** デバッグ用: 直近の update で選ばれた天井 Y。無効なら {@link Integer#MIN_VALUE}。 */
    public int getLastCeilingY() {
        return lastCeilingY;
    }

    /** デバッグ用: 直近の update で集計した列数 (母集団の広さ)。 */
    public int getLastColumnCount() {
        return lastColumnCount;
    }

    private void apply(LongOpenHashSet next) {
        // 集合が同一ならカリング結果は変わらない。世代を進める(=チャンク再構築を誘発する)と
        // 歩行中に毎probe再構築が走ってしまうため、変化したときだけ差し替える。
        if (next.equals(slicePositions)) {
            return;
        }
        // 追加/削除されたセルだけを再構築対象として記録する(探索キャッシュ全域を避ける)。
        LongOpenHashSet previous = slicePositions;
        LongIterator added = next.iterator();
        while (added.hasNext()) {
            long cell = added.nextLong();
            if (!previous.contains(cell)) {
                pendingChange.includeCell(cell);
            }
        }
        LongIterator removed = previous.iterator();
        while (removed.hasNext()) {
            long cell = removed.nextLong();
            if (!next.contains(cell)) {
                pendingChange.includeCell(cell);
            }
        }
        slicePositions = next;
        generation++;
    }
}
