package com.topdownview.culling;

import com.topdownview.culling.geometry.BlockChangeBox;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

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

    private static final int NO_CEILING = Integer.MIN_VALUE;

    /** 天井候補を探し始める足元からの高さ。プレイヤー(1.8)の頭の上を空ける。 */
    private static final int HEAD_ROOM = 2;

    /** 1列あたりの天井候補の探索上限ブロック数。見つからなければその列は母集団に入れない。 */
    private static final int MAX_CEILING_SCAN = 32;

    /** 検出範囲は空気セルの AABB なので、外壁の頂部も含めるため壁厚分マージンする。 */
    private static final int RANGE_MARGIN = 1;

    private volatile LongOpenHashSet slicePositions = new LongOpenHashSet();
    private volatile long generation = 0;

    /** デバッグ用: 直近の update で選ばれた天井 Y (NO_CEILING なら無効) と集計列数。 */
    private volatile int lastCeilingY = NO_CEILING;
    private volatile int lastColumnCount = 0;

    /** 前回の再構築以降に追加/削除されたセルの範囲。差分再構築に使う(ティック/描画スレッドのみ)。 */
    private final BlockChangeBox pendingChange = new BlockChangeBox();

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public void clearCache() {
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
        LongOpenHashSet next = new LongOpenHashSet();
        if (level == null || minPos == null || maxPos == null) {
            apply(next);
            return;
        }

        int ceilingY = findDominantCeilingY(level, floorCells, playerLevelY);
        if (ceilingY == NO_CEILING) {
            apply(next);
            return;
        }
        int minX = minPos.getX() - RANGE_MARGIN;
        int minZ = minPos.getZ() - RANGE_MARGIN;
        int maxX = maxPos.getX() + RANGE_MARGIN;
        int maxZ = maxPos.getZ() + RANGE_MARGIN;
        int maxBuildHeight = level.getMaxBuildHeight() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // 天井より上を全部消す。列の地表高さまでで打ち切る (その上は空気)。
                int top = Math.min(maxBuildHeight, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                if (top < ceilingY) {
                    top = ceilingY;
                }
                for (int y = ceilingY; y <= top; y++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    next.add(mutablePos.asLong());
                }
            }
        }
        apply(next);
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
