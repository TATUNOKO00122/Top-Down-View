package com.topdownview.culling;

import com.topdownview.Config;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

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
 *
 * <p>復元はいったん {@code pendingRestores} に入り、{@link #RESTORE_DEBOUNCE_MS} 経過で確定する。
 * 窓内の再カリングで復元自体を取り消すため、境界の揺れによる消失/復元の高速往復が生じない。
 */
public final class FadeTransitionController {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long INVALID = Long.MIN_VALUE;

    /** 集合上限(ハンドラ側と共有)。 */
    private static final int MAX_POSITIONS = 4000;

    /** 復元フラッシュの残光。ホールドではないので、この間 α=1 で穴を覆うだけ(判定は不変)。 */
    private static final long RESTORE_LINGER_MS = 200L;

    /**
     * ゴースト→実ブロックの引き継ぎ猶予。メッシュホールド解除で再構築が走ってから実際に
     * ブロックがメッシュへ戻るまでにはスケジュール待ち(50ms)とワーカー+バッチ確定が挟まる。
     * ゴーストを残光+この猶予まで残し、その間に実ブロックを戻すことで、消え際に一瞬見える
     * 穴(移動中は境界に沿って流れて見える)を無くす。
     */
    private static final long RESTORE_HANDOFF_MS = 300L;

    /**
     * 復元のデバウンス。検出からこの時間経過した復元だけを確定(フラッシュ/メッシュホールド)する。
     * 窓内に再カリングされたら復元自体を取り消す。カリング境界で消失/復元が高速往復するのを防ぐ
     * (この待ちの間はブロックの見た目もメッシュも変化しない)。
     */
    private static final long RESTORE_DEBOUNCE_MS = 300L;

    /** 境界反転診断ログの最小間隔。 */
    private static final long FLIP_LOG_INTERVAL_MS = 10_000L;

    /** 復元確定待ち(pos→検出ms)。デバウンス中は見た目もメッシュも変化させない。 */
    private final Long2LongOpenHashMap pendingRestores = new Long2LongOpenHashMap();

    /** 境界反転(抑制された再消失)の件数と診断ログ時刻。 */
    private long boundaryFlipCount = 0L;
    private long lastFlipLogMillis = 0L;
    private long lastFlipPosLong = Long.MIN_VALUE;

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
        pendingRestores.clear();
        recentlyRestored.clear();
        meshHoldUntil.clear();
        meshHoldView = new LongOpenHashSet();
        meshHoldDirty = false;
        meshHoldRebuildPending = false;
        baselineSeeded = false;
        boundaryFlipCount = 0L;
        lastFlipLogMillis = 0L;
        lastFlipPosLong = Long.MIN_VALUE;
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

        // ---- 前回カリングされていたが今回収集されなかった位置(復元検出) ----
        LongIterator prevIterator = previousCulled.iterator();
        while (prevIterator.hasNext()) {
            long posLong = prevIterator.nextLong();
            if (currentCulled.contains(posLong)) {
                continue;
            }
            // 生判定でまだカリング中なら収集漏れ。上限が来ていても必ず戻す。
            // 落とすとカリング済みブロックが誤って復元扱いになり、次の走査で再点滅する。
            if (stillCulled.test(posLong)) {
                currentCulled.add(posLong);
                continue;
            }
            // 復元はすぐ確定させずデバウンスへ置く。窓内の再カリングで取り消せる。
            prevIterator.remove();
            fadeOutStarts.remove(posLong);
            deferRestore(posLong, now);
        }

        // ---- 今回新たに収集された位置(消失) ----
        LongIterator currentIterator = currentCulled.iterator();
        while (currentIterator.hasNext()) {
            long posLong = currentIterator.nextLong();
            // 既にカリング中の位置はフラッシュしない(境界が動いても出続けるブロックは再点滅しない)
            if (!previousCulled.add(posLong)) {
                continue;
            }
            if (pendingRestores.containsKey(posLong)) {
                // デバウンス中の復元が取り消された: 消失/復元の往復を無かったことにする。
                // フラッシュもメッシュホールドも未確定なので関係解除だけで済む。
                pendingRestores.remove(posLong);
                restoreStarts.remove(posLong);
                noteBoundaryFlip(posLong, now);
                continue;
            }
            if (isRecentlyRestored(posLong)) {
                // 直近に確定復元した位置の再カリング: 境界の揺れとみなしフラッシュしない
                fadeOutStarts.remove(posLong);
                restoreStarts.remove(posLong);
                noteBoundaryFlip(posLong, now);
            } else if (!fadeOutStarts.containsKey(posLong)) {
                fadeOutStarts.put(posLong, now);
                flashes++;
            }
        }

        publishMeshHoldView();
        return flashes;
    }

    /** 復元をデバウンスへ置く(初回検出時刻のみ記録し窓内で延長しない)。 */
    private void deferRestore(long posLong, long now) {
        if (pendingRestores.containsKey(posLong)) {
            return;
        }
        pendingRestores.put(posLong, now);
        recentlyRestored.put(posLong, now);
    }

    /**
     * 天井スライス・覆いなど、カリング側が直接検出した「集合から外れた」位置の復元登録。
     * 即時確定せずデバウンスへ置き、同じ差分トラッカーの窓で取り消しを共有する。
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
            deferRestore(posLong, now);
        }
    }

    private void openMeshHold(long posLong, long now) {
        meshHoldUntil.put(posLong, now + getTransitionMillis() + RESTORE_LINGER_MS);
        meshHoldDirty = true;
    }

    /**
     * 期限切れエントリの掃除とデバウンスの確定。ティックごとにメインスレッドから呼ぶ。
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
                // 残光 + 引き継ぎ猶予までゴーストを残す。ホールド解除→再構築で実ブロックが
                // 戻るまでの穴を覆い切る(ここを手前で切ると消え際に一瞬穴が見える)。
                if (now >= restoreStarts.get(posLong) + transition + RESTORE_LINGER_MS + RESTORE_HANDOFF_MS) {
                    iterator.remove();
                }
            }
        }

        // デバウンスを確定する(フラッシュ開始とメッシュホールドは確定時刻基準)。
        // publishMeshHoldView は update() 側が tick の直後に呼ぶため、ここでは放置でよい。
        if (!pendingRestores.isEmpty()) {
            LongIterator iterator = pendingRestores.keySet().iterator();
            while (iterator.hasNext()) {
                long posLong = iterator.nextLong();
                if (now < pendingRestores.get(posLong) + RESTORE_DEBOUNCE_MS) {
                    continue;
                }
                iterator.remove();
                if (previousCulled.contains(posLong)) {
                    // 窓内に戻り済み(processCullSet で取り消し済み)の二重確定はしない。
                    continue;
                }
                restoreStarts.put(posLong, now);
                openMeshHold(posLong, now);
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

    /**
     * 集合境界の揺れで消失と復元が往復した証跡を数える。復元自体はデバウンスで無かったことに
     * なるが、往復自体が頻発していないかをはかる診断。スパム防止のため 10 秒間隔でまとめて出す。
     */
    private void noteBoundaryFlip(long posLong, long now) {
        boundaryFlipCount++;
        lastFlipPosLong = posLong;
        if (lastFlipLogMillis == 0L || now - lastFlipLogMillis >= FLIP_LOG_INTERVAL_MS) {
            LOGGER.info("[TopDownView] boundary flip suppressed x{} last at ({}, {}, {})",
                    boundaryFlipCount,
                    BlockPos.getX(lastFlipPosLong), BlockPos.getY(lastFlipPosLong), BlockPos.getZ(lastFlipPosLong));
            boundaryFlipCount = 0L;
            lastFlipLogMillis = now;
        }
    }

    private static long getTransitionMillis() {
        return (long) (Config.getFadeFlashDuration() * 1000.0);
    }
}
