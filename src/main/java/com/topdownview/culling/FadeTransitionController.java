package com.topdownview.culling;

import com.topdownview.Config;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * カリング集合の差分から消失/復元の「フラッシュ」開始時刻を記録するトラッカー。
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
 * 正しさは生のメッシュ判定に完全に任せる。このクラスは判定への入力を一切遅らせない。
 *
 * <p>メッシュ構築だけが参照する「復元ホールド」({@code meshHoldUntil})を持つ。復元の瞬間、
 * 判定は復元済みだがメッシュはまだ旧状態(カリング)で、置き換えまで数十msかかる。その間
 * メッシュに戻さず穴を保つことで、復元ゴーストの α0→1 が見える。レイキャスト等の
 * ゲームプレイ判定はこのホールドを見ない。
 *
 * <p>フラッシュは開始時刻を一度だけ記録し、tick が期限で除去する(時刻を延長しない)。
 * よってフラッシュが残り続けることは原理的にない。走査/差分/掃除はメインスレッド専用。
 */
public final class FadeTransitionController {

    private static final long INVALID = Long.MIN_VALUE;

    private final java.util.function.LongConsumer revealSink;

    /**
     * 復元が確定した位置を受け取るシンク。蓄積先(復元開示ボックス)を差分トラッカーが
     * 知る必要はないのでコールバックで分離する。
     */
    public FadeTransitionController(java.util.function.LongConsumer revealSink) {
        this.revealSink = revealSink;
    }

    /** 復元フラッシュの残光。この間 α=1 で穴を覆うだけ(判定は不変)。 */
    private static final long RESTORE_LINGER_MS = 200L;

    /**
     * ゴースト→実ブロックの引き継ぎ猶予。メッシュホールド解除で再構築が走ってから実際に
     * ブロックがメッシュへ戻るまでにはスケジュール待ちとワーカー+バッチ確定が挟まる。
     * ゴーストを残光+この猶予まで残し、その間に実ブロックを戻すことで、消え際に一瞬見える
     * 穴を無くす。
     */
    private static final long RESTORE_HANDOFF_MS = 300L;

    /** 前回走査でカリングされていた集合(差分の基準)。 */
    private LongOpenHashSet previousCulled = new LongOpenHashSet();

    /** 消失フラッシュ中(pos→開始ms)。描画側が完走時に除去する。メインスレッド専用。 */
    private final Long2LongOpenHashMap fadeOutStarts = new Long2LongOpenHashMap();

    /** 復元フラッシュ中(pos→開始ms)。描画側が参照し、tick が期限除去する。メインスレッド専用。 */
    private final Long2LongOpenHashMap restoreStarts = new Long2LongOpenHashMap();

    /** 直近で復元した(pos→時刻ms)。集合境界の揺れで再フラッシュするのを抑える。 */
    private final Long2LongOpenHashMap recentlyRestored = new Long2LongOpenHashMap();

    /**
     * メッシュ専用ホールド(pos→期限ms)。復元フラッシュの間、メッシュにブロックを戻さず
     * 穴を保つことで、ゴーストの α0→1 を目視できるようにする。判定本体(レイキャスト・
     * エンティティ・空間走査)には使わない。ワーカーが読むため不変スナップショットで公開する。
     */
    private final Long2LongOpenHashMap meshHoldUntil = new Long2LongOpenHashMap();

    /**
     * 各復元位置のホールド失効時刻。復元帳簿を閉じてよい条件(確定バッチが失効後に
     * スケジュールされたか)の判定に使う。ホールド本体(meshHoldUntil)は tick が除去するが、
     * 失効時刻は帳簿が閉じるまで保持する。
     */
    private final Long2LongOpenHashMap meshHoldExpiry = new Long2LongOpenHashMap();
    private volatile LongOpenHashSet meshHoldView = new LongOpenHashSet();
    private boolean meshHoldDirty = false;

    /** メッシュ専用ホールドの解除(メッシュ復帰)が未処理。CullingManager の再構築トリガ。 */
    private boolean meshHoldRebuildPending = false;

    /** 初回の集合取り込みはイベントを出さず基準にするだけ。有効化直後に全カリングが点滅するのを防ぐ。 */
    private boolean baselineSeeded = false;

    /**
     * 同時に保持する遷移ゴースト(消失+復元)の上限。復元はメッシュホールドも同じ枠を共有するため、
     * 超過分はフェードせず即時切替にして穴を残さない。密集地の一斉遷移で描画がスパイクするのを防ぐ。
     */
    private static final int MAX_ACTIVE_FLASHES = 512;

    public void clearCache() {
        previousCulled = new LongOpenHashSet();
        fadeOutStarts.clear();
        restoreStarts.clear();
        recentlyRestored.clear();
        meshHoldUntil.clear();
        meshHoldExpiry.clear();
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
    boolean isRecentlyRestored(long posLong) {
        return recentlyRestored.containsKey(posLong);
    }

    /** 復元フラッシュが未完走か(消失ループは減衰を担当せず凍結する)。 */
    public boolean isRestoring(long posLong) {
        return restoreStarts.containsKey(posLong);
    }

    /**
     * 走査が集めた「今カリングされている」集合を基準と比較する。
     *
     * @param currentCulled 今回の走査で収集した位置。取りこぼしを保持するため追加することがある。
     * @param stillCulled   位置が実際にまだカリング中かを返す生判定(収集漏れの保持用)。
     * @param playerX       プレイヤーのブロックX(ゴースト距離フィルタ用)。
     * @param playerY       プレイヤーのブロックY(同上)。
     * @param playerZ       プレイヤーのブロックZ(同上)。
     * @param maxFlashDistSq ゴーストを登録する距離の二乗。描画距離より少し広く取る。
     * @return 新規に消えた(=消失フラッシュを開始した)位置の数
     */
    public int processCullSet(LongSet currentCulled, java.util.function.LongPredicate stillCulled,
            int playerX, int playerY, int playerZ, double maxFlashDistSq) {
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
            // 生判定でまだカリング中なら収集漏れ。上限が来ていても必ず戻す。
            // 落とすとカリング済みブロックが誤って復元扱いになり、次の走査で再点滅する。
            if (stillCulled.test(posLong)) {
                currentCulled.add(posLong);
                continue;
            }
            prevIterator.remove();
            fadeOutStarts.remove(posLong);
            // メッシュ復帰は距離・枠に関係なく必要(カリングで消えた位置を戻す)。
            revealSink.accept(posLong);
            recentlyRestored.put(posLong, now);
            // 遠方はゴーストが見えないのでフェード登録しない(握るべき穴も無い)。枠も消費しない。
            if (!canRegisterFlash() || !isWithinFlashRange(posLong, playerX, playerY, playerZ, maxFlashDistSq)) {
                continue;
            }
            // 揺れで再登録の可能性があるが、帳簿(GHOST_ALPHA)を描画側が毎フレーム継続で
            // 保持するためレンプロの再スタートにはならない。put は purge 期限とホールドの更新。
            restoreStarts.put(posLong, now);
            openMeshHold(posLong, now);
        }

        // ---- 今回新たに収集された位置(消失) ----
        // 消失フラッシュは検出時(走査時)に即開始する。ゴーストは実ブロックがまだメッシュに
        // 残っている間、その背後で減衰する(同一テクスチャなので見えない)。メッシュの除去確定
        // (数フレーム後)の時点でゴーストは α≈0.6〜0.9 になっており、除去フレームで穴を
        // ほぼ覆った状態から減衰が続く=穴が露出しない。確定フレームで開始を刻む方式は、
        // フレーム内の処理順序(アップロードとフェードパスの前後)で1フレームの穴が出るため廃止。
        LongIterator currentIterator = currentCulled.iterator();
        while (currentIterator.hasNext()) {
            long posLong = currentIterator.nextLong();
            // 既にカリング中の位置はフラッシュしない(境界が動いても出続けるブロックは再点滅しない)
            if (!previousCulled.add(posLong)) {
                continue;
            }
            if (isRecentlyRestored(posLong)) {
                // 直近に確定復元した位置の再カリング: 復元帳簿を閉じ、共有 α 帳簿の現在値から
                // そのまま消失フェードへクロスフェードする。帳簿は GHOST_ALPHA が共有のため
                // レンプの再スタート(点滅)にはならず、自然な「見えていたものが溶ける」になる。
                restoreStarts.remove(posLong);
            }
            if (!fadeOutStarts.containsKey(posLong) && canRegisterFlash()
                    && isWithinFlashRange(posLong, playerX, playerY, playerZ, maxFlashDistSq)) {
                fadeOutStarts.put(posLong, now);
                flashes++;
            }
        }

        publishMeshHoldView();
        return flashes;
    }

    /** 保持中の遷移ゴースト数が上限未満か。 */
    private boolean canRegisterFlash() {
        return restoreStarts.size() + fadeOutStarts.size() < MAX_ACTIVE_FLASHES;
    }

    /** ゴースト描画距離内か。遠方は描画されないため登録しない。 */
    private static boolean isWithinFlashRange(long posLong, int playerX, int playerY, int playerZ, double maxDistSq) {
        int dx = BlockPos.getX(posLong) - playerX;
        int dy = BlockPos.getY(posLong) - playerY;
        int dz = BlockPos.getZ(posLong) - playerZ;
        return dx * dx + dy * dy + dz * dz <= maxDistSq;
    }

    /**
     * メッシュ再構築のバッチ確定時に呼ぶ。復元帳簿の締め処理のみを行う: 確定したセクションに
     * 含まれる復元位置のうち、判定が復元済みで、かつ「このバッチがホールド失効後に
     * スケジュールされた」ものだけを閉じる。スケジュール条件が要る理由: ホールドが生きている
     * 間に構築された古いバッチの確定は、そのメッシュに実ブロックが乗っていない。そこで閉じると
     * 帳簿が先に消え、実ブロックが次の確定で来るまでの1フレーム穴が露出する
     * (ゴースト→実ブロックの切り替わりで1フレーム消える)。消失フラッシュの開始は検出時即時のため
     * ここでは扱わない。CullingManager.commitBatch から呼ばれる。
     *
     * @param batchScheduledAtMs この確定バッチをスケジュールした時刻(ms)
     */
    public void onMeshCommit(long now, LongSet committedSections, long batchScheduledAtMs) {
        if (!restoreStarts.isEmpty()) {
            LongIterator iterator = restoreStarts.keySet().iterator();
            while (iterator.hasNext()) {
                long posLong = iterator.nextLong();
                if (previousCulled.contains(posLong)) {
                    continue;
                }
                Long holdExpiry = meshHoldExpiry.get(posLong);
                if (holdExpiry == null || batchScheduledAtMs < holdExpiry) {
                    // ホールドがまだ生きている、またはその失効前にスケジュールされたバッチ:
                    // このメッシュに実ブロックは乗っていない。帳簿を維持してゴーストで穴を覆う。
                    continue;
                }
                int sx = BlockPos.getX(posLong) >> 4;
                int sy = BlockPos.getY(posLong) >> 4;
                int sz = BlockPos.getZ(posLong) >> 4;
                if (committedSections.contains(SectionPos.asLong(sx, sy, sz))) {
                    iterator.remove();
                    meshHoldExpiry.remove(posLong);
                }
            }
        }
    }

    private void openMeshHold(long posLong, long now) {
        long expiry = now + getTransitionMillis() + RESTORE_LINGER_MS;
        meshHoldUntil.put(posLong, expiry);
        // 帳簿をいつ閉じてよいかを判定するため失効時刻を保持する(ホールド本体は tick が除去する)。
        meshHoldExpiry.put(posLong, expiry);
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
                // 残光 + 引き継ぎ猶予までゴーストを残す。ホールド解除→再構築で実ブロックが
                // 戻るまでの穴を覆い切る(ここを手前で切ると消え際に一瞬穴が見える)。
                if (now >= restoreStarts.get(posLong) + transition + RESTORE_LINGER_MS + RESTORE_HANDOFF_MS) {
                    iterator.remove();
                    meshHoldExpiry.remove(posLong);
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
                    // 復元帳簿はここでは閉じない: メッシュの復帰は開示→再構築→バッチ確定の
                    // あと(立ち止まっていると更に遅延する)。ここで閉じるとゴーストが消えて
                    // から実ブロックが戻るまでのフレーム差分が穴として露出する。
                    // 帳簿は onMeshCommit(実ブロックがメッシュに乗った確定時)で閉じる。
                    meshHoldDirty = true;
                    revealSink.accept(posLong);
                }
            }
            if (meshHoldDirty && !meshHoldRebuildPending) {
                // 解除されたブロックはメッシュへ戻す必要がある。CullingManager が
                // 次のスケジュールで覆い半径ボックスの再構築を必ず走らせる。
                meshHoldRebuildPending = true;
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
