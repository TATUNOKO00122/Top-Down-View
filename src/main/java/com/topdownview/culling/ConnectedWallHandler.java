package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.client.InteractableBlocks;
import com.topdownview.culling.geometry.CylinderCalculator;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.LongPredicate;

/**
 * 部屋の角などの死角で発生するV字の壁残りを解消するため、
 * カリングされた手前の壁ブロックから隣接する壁ブロックへカリングを連鎖伝播させるハンドラ。
 */
public final class ConnectedWallHandler {

    /** BFS 連鎖で新規に追加できる壁の上限。シードは含めない(シードは走査帯で上限付き)。 */
    private static final int MAX_CHAIN_BLOCKS = 96;
    /** 公開集合全体の上限。柱展開(各メンバーの上方向ストリップ)を含む。 */
    private static final int MAX_TOTAL_BLOCKS = 1024;
    private static final int QUEUE_CAPACITY = 1024;

    /**
     * 保持の欠席しきい値(ティック)。候補から連続でこの数だけ外れたメンバーを解放する。
     * 8 ティック ≈ 0.4 秒: 境界の揺れ(1〜2 ティックの当落)は吸収しつつ、離脱に素早く追従する。
     */
    private static final int RELEASE_ABSENT_TICKS = 8;

    /**
     * 連鎖のカメラ側境界のヒステリシス幅。生の half-space 判定はカメラ回転で境界線が回り、
     * 境界上のメンバーが毎回解放/再連鎖されてちらつく。dot がこの幅を超えて「明確に反対側」
     * になったメンバーだけを解放し、帯の内側は現状維持する。
     */
    private static final double CHAIN_BOUND_MARGIN = 0.75;

    /** Y 平滑化パスの反復数。隣接列への Y レベル伝播が収束するのに十分な回数。 */
    private static final int Y_LEVEL_PASSES = 3;

    /** XZ 列キーのパック ((x << 32) | z)。Y 平滑化の「この列は連鎖済みか」判定に使う。 */
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // 水平4方向 + 上方向 (下方向は床保護のため除外)
    private static final int[] DX = {1, -1, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1};
    private static final int[] DZ = {0, 0, 1, -1, 0};

    /**
     * 連鎖カリング集合。volatile 差し替えで公開し、チャンク構築ワーカーが
     * 再構築中の集合を二重読み/同時変更なしに参照できるようにする。
     */
    private volatile LongOpenHashSet connectedCulledPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    /** 毎ティック差し替える二つの公開バッファ(再利用、アロケーションなし)。
     *  published 集合が A のとき B に組み立てて差し替える。 */
    private final LongOpenHashSet publishBufferA = new LongOpenHashSet(MAX_TOTAL_BLOCKS);
    private final LongOpenHashSet publishBufferB = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    private final long[] queue = new long[QUEUE_CAPACITY];
    private final int[] depthQueue = new int[QUEUE_CAPACITY];

    /** 候補から欠席しているティック数(前回メンバーごと)。しきい値を超えたら解放。 */
    private final Long2ByteOpenHashMap absentTicks = new Long2ByteOpenHashMap();

    /** Y 平滑化パス用の「連鎖済み XZ 列」スクラッチ集合。 */
    private final LongOpenHashSet columnScratch = new LongOpenHashSet(256);

    /** 集合の世代番号。値が変わればカリング結果が変わり得る(再構築/走査ゲートのトリガ)。 */
    private volatile long generation;

    /**
     * 解放された(連鎖から外れた)メンバーの受け渡し。連鎖メンバーは遷移フェードの対象外
     * (即時カリング)のため、解放はこの経路で開示(reveal)され、メッシュへ即戻る。
     */
    private final LongOpenHashSet droppedPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);

    private final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    public ConnectedWallHandler() {
    }

    public void clearCache() {
        connectedCulledPositions = new LongOpenHashSet(MAX_TOTAL_BLOCKS);
        absentTicks.clear();
        droppedPositions.clear();
    }

    public boolean isConnectedCulled(long posLong) {
        return connectedCulledPositions.contains(posLong);
    }

    /** デバッグHUD用: 現在連鎖カリングが有効なブロック数。 */
    public int getChainCount() {
        return connectedCulledPositions.size();
    }

    /** 集合の世代番号。値が変わればカリング結果が変わり得る。 */
    public long getGeneration() {
        return generation;
    }

    /** 解放された(連鎖から外れた)メンバーの受け渡し。メインスレッド専用。 */
    public void takeDroppedPositions(LongOpenHashSet out) {
        out.addAll(droppedPositions);
        droppedPositions.clear();
    }

    /**
     * プレイヤー周辺の手前壁から連鎖カリング対象のブロック集合を更新する。
     *
     * <p>シードは幾何近似(円柱/クリップ)ではなく<b>判定そのもの</b>({@code isCulled})で
     * 認定する。近似だと保護やクリップで見送られる壁がシード化され、次判定との食い違いで
     * 連鎖が当落し、カリング/復元の点滅になる。
     *
     * <p>前回の集合は毎ティック全クリアせず<b>保持マージ</b>する: 現在の候補(BFS結果)に加え、
     * 円柱内でまだ壁である前回メンバーは欠席しきい値(≈0.4秒)まで保持する。全クリア方式では
     * シード帯の出入れで連鎖メンバーの当落が当ティックごとに反転し、判定が
     * カリング→復元→カリングの往復で遷移フェードが毎回始まり直す(消失/復元の点滅)。
     */
    public void update(Level level, double playerX, double playerY, double playerZ,
                       double cameraX, double cameraY, double cameraZ,
                       double viewDirX, double viewDirZ,
                       LongSet seedSource, LongPredicate isSeedable) {
        if (!Config.isConnectedWallCullingEnabled() || level == null) {
            clearCache();
            return;
        }

        int pBlockX = (int) Math.floor(playerX);
        int pFeetY = (int) Math.floor(playerY);
        int pBlockZ = (int) Math.floor(playerZ);
        int maxSteps = Config.getConnectedWallMaxDistance();
        // Y上限はカメラ高さ(カメラより上のブロックはカリングしない)。カメラが足元近くの
        // 場合(屋内低天井など)でもシード帯までは保証する。連鎖は壁(solid)が続く限り上へ
        // 伝播し、空気に到達した列で止まる(BFS は空気を通らない)。
        int maxChainY = Math.max((int) Math.floor(cameraY), pFeetY + 2);

        int head = 0;
        int tail = 0;

        // 組み立て先を今ティックの公開バッファへ。前回の公開集合と別インスタンスにする。
        LongOpenHashSet published = connectedCulledPositions;
        LongOpenHashSet candidate = (published == publishBufferA) ? publishBufferB : publishBufferA;
        candidate.clear();

        // 1. シード探索: 走査収集合(扇カリング等で実際にカリングされたブロック)から、
        //    連鎖の届く範囲・Y帯・シード可能判定を通ったものを起点にする。追加走査は発生しない。
        //    シードはキューに積むだけで公開集合には入れない: シードは円柱カリングで既に消えて
        //    いるブロックであり、公開集合に加えると上限を圧迫して保持メンバーを追い出し、
        //    連鎖境界の当落がそのまま点滅として出力される。
        int maxHorizDistFromPlayer = maxSteps + 2;
        for (long posLong : seedSource) {
            int nx = BlockPos.getX(posLong);
            int ny = BlockPos.getY(posLong);
            int nz = BlockPos.getZ(posLong);

            // 床・地面の保護 (プレイヤー足元未満は巻き込まない) / カメラより上は対象外
            if (ny < pFeetY || ny > maxChainY) continue;
            // 連鎖の届く水平範囲外のシードは意味がない (BFS の距離フィルタと同じ条件)
            if (Math.abs(nx - pBlockX) > maxHorizDistFromPlayer
                    || Math.abs(nz - pBlockZ) > maxHorizDistFromPlayer) {
                continue;
            }
            // 天井スライス等、連鎖に馴染まない集合の除外
            if (!isSeedable.test(posLong)) continue;

            if (tail < QUEUE_CAPACITY) {
                queue[tail] = posLong;
                depthQueue[tail] = 0;
                tail++;
            }
        }

        // 2. BFS 連鎖展開: シードから隣接する壁ブロックを探索 (連鎖追加だけに上限を掛ける)
        int chainBase = candidate.size();
        while (head < tail && candidate.size() - chainBase < MAX_CHAIN_BLOCKS) {
            long currentPosLong = queue[head];
            int currentDepth = depthQueue[head];
            head++;

            if (currentDepth >= maxSteps) continue;

            tempPos.set(currentPosLong);

            for (int i = 0; i < 5; i++) {
                int nx = tempPos.getX() + DX[i];
                int ny = tempPos.getY() + DY[i];
                int nz = tempPos.getZ() + DZ[i];

                // 床・地面の保護 (プレイヤー足元未満は巻き込まない)
                if (ny < pFeetY) continue;
                // カメラより上はカリングしない
                if (ny > maxChainY) continue;

                // カメラ側半空間に限定する。プレイヤーの反対側(クリップが保護する領域)へは
                // 連鎖を渡さない。扇/半空間どちらのモードでも、連鎖の役割は「カメラ側の
                // 角の裏を回り込む」ことであり、遠側の壁を消すことではない。
                if (OcclusionCalculator.isBeyondPlayerHorizontally(
                        nx + 0.5, nz + 0.5, playerX, playerZ, viewDirX, viewDirZ)) {
                    continue;
                }

                // プレイヤーからの距離制限 (遠方の壁への延焼を防止)
                if (Math.abs(nx - pBlockX) > maxHorizDistFromPlayer
                        || Math.abs(nz - pBlockZ) > maxHorizDistFromPlayer) {
                    continue;
                }

                // 深さ(手数)は水平移動のみで消費する。上方向の登攀は Y 上限(カメラ高さ)が
                // 支配するため、デプスを消費すると壁の上部まで連鎖が届かなくなる。
                int nextDepth = (i == 4) ? currentDepth : currentDepth + 1;

                neighborPos.set(nx, ny, nz);
                long neighborLong = neighborPos.asLong();
                // シード自体/既に追加済みの連鎖メンバーはスキップ
                if (candidate.contains(neighborLong) || seedSource.contains(neighborLong)) continue;

                if (!WallAnalyzer.isSolid(level, neighborPos)) continue;

                BlockState state = level.getBlockState(neighborPos);
                if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                candidate.add(neighborLong);

                if (tail < QUEUE_CAPACITY) {
                    queue[tail] = neighborLong;
                    depthQueue[tail] = nextDepth;
                    tail++;
                }
            }
        }

        // 3. Y 平滑化: 横に隣接する連鎖列どうしで Y レベルを合わせる。列ごとの壁高の差で
        //    上端が凸凹になるのを防ぎ、カリング境界を長方形に整える。壁(solid)が続く高さだけ
        //    を埋めるため、壁の無い高さ(空気)は埋めない。
        for (int pass = 0; pass < Y_LEVEL_PASSES; pass++) {
            if (candidate.size() >= MAX_TOTAL_BLOCKS) {
                break;
            }
            boolean changed = false;
            columnScratch.clear();
            for (long posLong : candidate) {
                columnScratch.add(packXZ(BlockPos.getX(posLong), BlockPos.getZ(posLong)));
            }
            for (long posLong : candidate.toLongArray()) {
                int x = BlockPos.getX(posLong);
                int y = BlockPos.getY(posLong);
                int z = BlockPos.getZ(posLong);
                for (int i = 0; i < 4; i++) {
                    int nx = x + DX[i];
                    int nz = z + DZ[i];
                    // 隣接列が連鎖済みでないなら、その列へは伸ばさない (BFS が範囲を決める)
                    if (!columnScratch.contains(packXZ(nx, nz))) continue;
                    if (y > maxChainY) continue;

                    neighborPos.set(nx, y, nz);
                    long neighborLong = neighborPos.asLong();
                    // シード(円柱カリング済み)を連鎖メンバーにすると、除外→シード喪失→
                    // 解放→再収集→再連鎖の自己振動になるため絶対に追加しない
                    if (candidate.contains(neighborLong) || seedSource.contains(neighborLong)) continue;
                    if (!WallAnalyzer.isSolid(level, neighborPos)) continue;
                    BlockState state = level.getBlockState(neighborPos);
                    if (InteractableBlocks.isInteractable(state, level, neighborPos)) continue;

                    candidate.add(neighborLong);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        // 4. 保持マージ(欠席ティック制): 前回メンバーで candidate に現れないものは欠席数を進め、
        // しきい値(≈0.4秒)を超えたら解放する。距離ベースの保持は離れた後も連鎖を引きずるため、
        // 欠席ベースは「境界の揺れで 1〜2 ティックだけ外れた」場合のみ維持する。
        for (long prevLong : connectedCulledPositions) {
            if (candidate.contains(prevLong)) {
                absentTicks.remove(prevLong);
                continue;
            }
            // シード(円柱カリング済み)は保持せず円柱に返す。保持すると除外→シード喪失→
            // 解放→再収集の自己振動ループに入る。
            if (seedSource.contains(prevLong)) {
                absentTicks.remove(prevLong);
                continue;
            }
            int nx = BlockPos.getX(prevLong);
            int ny = BlockPos.getY(prevLong);
            int nz = BlockPos.getZ(prevLong);

            // 円柱が既に離した位置は即解放する。保持は「シード喪失による連鎖の当落の揺れ」
            // のみを吸収するための機構で、円柱の復元を引き延ばす権利はない(穴が開き続ける)。
            double normDistSq = CylinderCalculator.getNormalizedDistanceSq(
                    nx + 0.5, ny + 0.5, nz + 0.5);
            boolean beyondPlayer = OcclusionCalculator.isBeyondPlayerHorizontally(
                    nx + 0.5, nz + 0.5, playerX, playerZ, viewDirX, viewDirZ);
            if (normDistSq < 0.0 || normDistSq > 1.0 || beyondPlayer) {
                absentTicks.remove(prevLong);
                continue;
            }
            // 壁としてまだ有効で、インタラクション保護対象でもないメンバーのみが保持対象。
            // (固形でない=世界が変わった / 保護対象=消してはならない) 即座に解放。
            neighborPos.set(nx, ny, nz);
            boolean solid = WallAnalyzer.isSolid(level, neighborPos);
            boolean interactable;
            if (solid) {
                BlockState state = level.getBlockState(neighborPos);
                interactable = InteractableBlocks.isInteractable(state, level, neighborPos);
            } else {
                interactable = false;
            }
            if (!solid || interactable) {
                absentTicks.remove(prevLong);
                continue;
            }

            int absent = absentTicks.get(prevLong) + 1;
            if (absent > RELEASE_ABSENT_TICKS) {
                absentTicks.remove(prevLong);
                continue;
            }
            absentTicks.put(prevLong, (byte) absent);

            if (candidate.size() >= MAX_TOTAL_BLOCKS) {
                continue;
            }
            candidate.add(prevLong);
        }

        connectedCulledPositions = candidate;

        // 解放メンバーを記録する(開示経路でメッシュへ即戻す)。追加は記録しない
        // (追加は判定側の通常カリングとして即時反映される)。
        if (membershipChanged(published, candidate)) {
            for (long prevLong : published) {
                if (!candidate.contains(prevLong)) {
                    droppedPositions.add(prevLong);
                }
            }
            generation++;
        }
    }

    private static boolean membershipChanged(LongOpenHashSet previous, LongOpenHashSet next) {
        if (previous.size() != next.size()) {
            return true;
        }
        for (long posLong : previous) {
            if (!next.contains(posLong)) {
                return true;
            }
        }
        return false;
    }
}
