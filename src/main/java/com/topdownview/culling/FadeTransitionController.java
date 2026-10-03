package com.topdownview.culling;

import com.topdownview.Config;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * カリング集合の差分から消失/復元の「フラッシュ」を検出するトラッカー。
 *
 * <p>走査が集めた「今カリングされている位置」集合を前回と比較し、
 * <ul>
 *   <li>新規カリング → 消失フラッシュ開始を登録(描画側が α1→0)。</li>
 *   <li>集合から外れた → 復元フラッシュ開始を登録(描画側が α0→1)。
 *       メッシュ再構築が戻るまでの穴をゴーストで覆い、ポリゴンオフセットで
 *       メッシュが戻れば奥に隠れる。</li>
 * </ul>
 *
 * <p>カリング判定 ({@code TopDownCuller.isBlockCulled}) には一切介入しない。カリングの
 * 正しさは生のメッシュ判定に完全に任せる。以前の復元ホールド方式(判定を true に保持)は、
 * 復元フラッシュ中の再カリングで消失/復元が競合しα=1のゴーストが残る不具合の元凶だった。
 *
 * <p>フラッシュは開始時刻を一度だけ記録し、描画側が完走時に除去する(時刻は延長しない)。
 * よってフラッシュが残り続けることは原理的にない。走査/差分/掃除はメインスレッド専用。
 */
public final class FadeTransitionController {

    private static final long INVALID = Long.MIN_VALUE;

    /** 集合上限(ハンドラ側と共有)。 */
    private static final int MAX_POSITIONS = 4000;

    /** 復元フラッシュの残光。ホールドではないので、この間 α=1 で穴を覆うだけ(判定は不変)。 */
    private static final long RESTORE_LINGER_MS = 200L;

    /** 前回走査でカリングされていた集合(差分の基準)。 */
    private LongOpenHashSet previousCulled = new LongOpenHashSet();

    /** 消失フラッシュ中(pos→開始ms)。描画側が完走時に除去する。メインスレッド専用。 */
    private final Long2LongOpenHashMap fadeOutStarts = new Long2LongOpenHashMap();

    /** 復元フラッシュ中(pos→開始ms)。描画側が参照し、tick が期限除去する。メインスレッド専用。 */
    private final Long2LongOpenHashMap restoreStarts = new Long2LongOpenHashMap();

    /** 直近で復元した(pos→開始ms)。集合境界の揺れで再フラッシュするのを抑える。 */
    private final Long2LongOpenHashMap recentlyRestored = new Long2LongOpenHashMap();

    /**
     * メッシュ専用ホールド(pos→期限ms)。復元フラッシュの間、メッシュにブロックを戻さず
     * 穴を保つことで、ゴーストの α0→1 を目視できるようにする。判定本体(レイキャスト・
     * エンティティ・空間走査)には使わない。ワーカーが読むため不変スナップショットで公開する。
     */
    private final Long2LongOpenHashMap meshHoldUntil = new Long2LongOpenHashMap();
    private volatile LongOpenHashSet meshHoldView = new LongOpenHashSet();
    private boolean meshHoldDirty = false;

    /** メッシュ専用ホールドの解除(メッシュ復帰)が未処理。CullingManager の再構築トリガ。 */
    private boolean meshHoldRebuildPending = false;

    /** 初回の集合取り込みはイベントを出さず基準にするだけ。有効化直後に全カリングが点滅するのを防ぐ。 */
    private boolean baselineSeeded = false;

    public void clearCache() {
        previousCulled = new LongOpenHashSet();
        fadeOutStarts.clear();
        restoreStarts.clear();
        recentlyRestored.clear();
        meshHoldUntil.clear();
        meshHoldView = new LongOpenHashSet();
        meshHoldDirty = false;
        meshHoldRebuildPending = false;
        baselineSeeded = false;
    }

    /** 消失フラッシュの開始時刻ms。進行していなければ INVALID。 */
    public long getFadeOutStart(long posLong) {
        return fadeOutStarts.getOrDefault(posLong, INVALID);
    }

    /** 完走した消失フラッシュを除去する(描画スレッド=メインスレッドから)。 */
    public void forgetFadeOut(long posLong) {
        fadeOutStarts.remove(posLong);
    }

    /** メッシュ専用ホールド中か(復元フラッシュの穴を保つ)。ワーカーから呼ばれる。 */
    public boolean isMeshHoldActive(long posLong) {
        return meshHoldView.contains(posLong);
    }

    /** メッシュ専用ホールドの変更をワーカー読み用スナップショットへ反映する。 */
    public void publishMeshHoldView() {
        if (!meshHoldDirty) {
            return;
        }
        meshHoldDirty = false;
        meshHoldView = new LongOpenHashSet(meshHoldUntil.keySet());
    }

    /** メッシュ専用ホールドの解除でメッシュ復帰が必要か。 */
    public boolean isMeshHoldRebuildPending() {
        return meshHoldRebuildPending;
    }

    public void consumeMeshHoldRebuildPending() {
        meshHoldRebuildPending = false;
    }

    /** 復元フラッシュ中の位置を列挙する(描画側の復元ゴースト対象)。 */
    public void forEachActiveRestore(java.util.function.LongConsumer consumer) {
        LongIterator iterator = restoreStarts.keySet().iterator();
        while (iterator.hasNext()) {
            consumer.accept(iterator.nextLong());
        }
    }

    /** 直近に復元した位置か(集合境界の揺れによる再フラッシュ抑制)。 */
    public boolean isRecentlyRestored(long posLong) {
        return recentlyRestored.containsKey(posLong);
    }

    /**
     * 走査が集めた「今カリングされている」集合を基準と比較する。
     *
     * @param currentCulled 今回の走査で収集した位置。取りこぼしを保持するため追加することがある。
     * @param stillCulled   位置が実際にまだカリング中かを返す生判定(収集漏れの保持用)。
     * @return 新規に消えた(=消失フラッシュを開始した)位置の数
     */
    public int processCullSet(LongSet currentCulled, java.util.function.LongPredicate stillCulled) {
        int flashes = 0;
        long now = System.currentTimeMillis();
        if (!baselineSeeded) {
            previousCulled.addAll(currentCulled);
            baselineSeeded = true;
            return 0;
        }

        // ---- 前回カリングされていたが今回収集されなかった位置(復元) ----
        LongIterator prevIterator = previousCulled.iterator();
        while (prevIterator.hasNext()) {
            long posLong = prevIterator.nextLong();
            if (currentCulled.contains(posLong)) {
                continue;
            }
            // 生判定でまだカリング中なら収集漏れ。収集集合へ戻して保持し、誤った復元を出さない。
            if (stillCulled.test(posLong)) {
                if (currentCulled.size() < MAX_POSITIONS) {
                    currentCulled.add(posLong);
                }
                continue;
            }
            prevIterator.remove();
            fadeOutStarts.remove(posLong);
            restoreStarts.put(posLong, now);
            recentlyRestored.put(posLong, now);
            openMeshHold(posLong, now);
        }

        // ---- 今回新たに収集された位置(消失) ----
        LongIterator currentIterator = currentCulled.iterator();
        while (currentIterator.hasNext()) {
            long posLong = currentIterator.nextLong();
            // 既にカリング中の位置はフラッシュしない(境界が動いても出続けるブロックは再点滅しない)
            if (!previousCulled.add(posLong)) {
                continue;
            }
            if (isRecentlyRestored(posLong)) {
                // 直近に復元した位置の再カリング: 境界の揺れとみなしフラッシュしない
                fadeOutStarts.remove(posLong);
                restoreStarts.remove(posLong);
            } else if (!fadeOutStarts.containsKey(posLong)) {
                fadeOutStarts.put(posLong, now);
                flashes++;
            }
        }

        publishMeshHoldView();
        return flashes;
    }

    /**
     * 天井スライス・覆いなど、カリング側が直接検出した「集合から外れた」位置の復元フラッシュ登録。
     */
    public void registerRestores(LongOpenHashSet positions) {
        if (positions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        LongIterator iterator = positions.iterator();
        while (iterator.hasNext()) {
            long posLong = iterator.nextLong();
            fadeOutStarts.remove(posLong);
            restoreStarts.put(posLong, now);
            recentlyRestored.put(posLong, now);
            openMeshHold(posLong, now);
        }
    }

    private void openMeshHold(long posLong, long now) {
        meshHoldUntil.put(posLong, now + getTransitionMillis() + RESTORE_LINGER_MS);
        meshHoldDirty = true;
    }

    /**
     * 期限切れエントリの掃除。ティックごとにメインスレッドから呼ぶ。
     */
    public void tick() {
        long now = System.currentTimeMillis();
        long transition = getTransitionMillis();

        if (!fadeOutStarts.isEmpty()) {
            LongIterator iterator = fadeOutStarts.keySet().iterator();
            while (iterator.hasNext()) {
                long posLong = iterator.nextLong();
                if (now >= fadeOutStarts.get(posLong) + transition + 1000L) {
                    iterator.remove();
                }
            }
        }
        if (!restoreStarts.isEmpty()) {
            LongIterator iterator = restoreStarts.keySet().iterator();
            while (iterator.hasNext()) {
                long posLong = iterator.nextLong();
                if (now >= restoreStarts.get(posLong) + transition + RESTORE_LINGER_MS) {
                    iterator.remove();
                }
            }
        }
        purgeOlder(recentlyRestored, now, transition * 2);

        // メッシュ専用ホールドの期限切れを除去(期限が来たらメッシュが復帰する)。
        if (!meshHoldUntil.isEmpty()) {
            LongIterator iterator = meshHoldUntil.keySet().iterator();
            while (iterator.hasNext()) {
                long posLong = iterator.nextLong();
                if (now >= meshHoldUntil.get(posLong)) {
                    iterator.remove();
                    meshHoldDirty = true;
                    meshHoldRebuildPending = true;
                }
            }
        }
    }

    private static void purgeOlder(Long2LongOpenHashMap map, long now, long lifetime) {
        if (map.isEmpty()) {
            return;
        }
        LongIterator iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            long posLong = iterator.nextLong();
            if (now >= map.get(posLong) + lifetime) {
                iterator.remove();
            }
        }
    }

    private static long getTransitionMillis() {
        return (long) (Config.getFadeFlashDuration() * 1000.0);
    }
}
