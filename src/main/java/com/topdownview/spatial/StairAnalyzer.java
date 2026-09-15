package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 階段検出エンジン。
 *
 * <p>プレイヤー周辺の指定半径内をスキャンし、階段状の配置を検出する。
 * 空間探索（SpaceRegion）に依存せず独立して動作する。
 * 階段 = 各段が前の段から水平1ブロック・垂直+1ブロックの位置にあり、
 * かつ各段の上が非固体（空気）である minSteps 段以上のブロック列。
 *
 * <p>検出対象：
 * <ul>
 *   <li>バニラの StairBlock（階段ブロック）</li>
 *   <li>通常の固体ブロックが階段状に配置されたもの</li>
 * </ul>
 * ブロック種類は問わず、空間的な階段パターンで判定する。
 *
 * <p>「上が非固体」制約により、壁の対角線チェーンを誤検出しない：
 * 壁の対角線上のブロックは上が壁で塞がれている（歩けない）ため階段として除外される。
 * 本物の階段は各段の上が空気（歩ける面）なので検出される。
 *
 * <p>アルゴリズム：
 * <ol>
 *   <li>center 周辺の直方体（XZ は 2*radius+1、Y は scanMinY..scanMaxY）をスキャンし、候補を収集</li>
 *     <ul>
 *       <li>候補 = 固体ブロック かつ 上が非固体（歩行可能な段）</li>
 *     </ul>
 *   <li>各候補・各水平方向について、最下段から最上段までの最大シーケンスを構成</li>
 *     <ul>
 *       <li>findBottom: 逆方向・1下 に「上が非固体の候補」が続く限り下る</li>
 *       <li>extendUp: 正方向・1上 に「上が非固体の候補」が続く限り上る</li>
 *     </ul>
 *   </li>
 *   <li>minSteps 段以上のシーケンスを長い順に採用（ブロック重複なし）</li>
 * </ol>
 *
 * <p>平坦な床・真っ直ぐな壁・壁の対角線は階段パターンにならないため誤検出しない。
 */
public final class StairAnalyzer {

    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    private StairAnalyzer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 走査Y下限。最下段がプレイヤー基準より下にある階段でも minSteps 段を数えられるだけの
     * 余裕を残しつつ、無関係な下層を走査しない。
     */
    public static int scanMinY(int feetY, int minSteps) {
        return feetY - (minSteps - 1);
    }

    /**
     * 走査Y上限。対象とすべき段の最大高さ (feetY + exclusionHeight) と、
     * 最下段が feetY+1 にある階段でも minSteps 段を数えられる高さの大きい方を取る。
     */
    public static int scanMaxY(int feetY, int exclusionHeight, int minSteps) {
        return feetY + Math.max(exclusionHeight, minSteps);
    }

    /**
     * 指定中心位置の周辺から階段を検出する。空間探索結果に依存しない。
     *
     * @param center   スキャン中心位置の水平基準（通常はプレイヤー位置）
     * @param radius   水平スキャン半径（center を中心とした XZ の片側幅）
     * @param minSteps 階段として認定する最小段数（3以上を推奨）
     * @param scanMinY 走査Y下限（{@link #scanMinY} で算出）
     * @param scanMaxY 走査Y上限（{@link #scanMaxY} で算出）
     * @param blockMap 固体判定キャッシュ。この探索中に構築されたものを再利用する。
     * @return 検出された階段リスト（重複ブロックなし、長い順）
     */
    public static List<Staircase> detect(BlockPos center, int radius, int minSteps,
            int scanMinY, int scanMaxY, BlockMap blockMap) {
        if (center == null || blockMap == null || radius < 1 || minSteps < 1 || scanMinY > scanMaxY) {
            return List.of();
        }

        // center 周辺の直方体をスキャンし、階段の段となりうるブロックを収集。
        // 候補 = 固体ブロック かつ 上が非固体（歩行可能な段）。
        // BlockPosではなくpacked longで保持してアロケーションを避ける。
        LongOpenHashSet candidates = new LongOpenHashSet();
        int cx = center.getX(), cz = center.getZ();
        for (int y = scanMinY; y <= scanMaxY; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (blockMap.isSolid(x, y, z) && !blockMap.isSolid(x, y + 1, z)) {
                        candidates.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }

        if (candidates.size() < minSteps) {
            return List.of();
        }

        // StairBlock をマーク
        LongOpenHashSet stairBlocks = new LongOpenHashSet();
        for (long p : candidates) {
            if (blockMap.isStair(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) {
                stairBlocks.add(p);
            }
        }

        // 全候補・全方向の最大シーケンスを収集
        // 重複排除：(最下段, 方向) のペアで一意にする
        Set<Long> seenSequences = new HashSet<>();
        List<StairSeq> sequences = new ArrayList<>();

        for (long start : candidates) {
            for (Direction dir : HORIZONTAL) {
                long bottom = findBottom(candidates, start, dir, blockMap);
                long key = sequenceKey(bottom, dir);
                if (!seenSequences.add(key)) continue;

                LongList seq = extendUp(candidates, bottom, dir, blockMap);
                if (seq.size() >= minSteps) {
                    sequences.add(new StairSeq(seq, dir));
                }
            }
        }

        // 長い順にソートしてブロック重複を排除しながら採用
        sequences.sort((a, b) -> Integer.compare(b.steps.size(), a.steps.size()));

        LongOpenHashSet used = new LongOpenHashSet();
        List<Staircase> result = new ArrayList<>();
        for (StairSeq ss : sequences) {
            // 重複チェック
            boolean overlap = false;
            for (long p : ss.steps) {
                if (used.contains(p)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            used.addAll(ss.steps);

            boolean hasStairs = false;
            for (long p : ss.steps) {
                if (stairBlocks.contains(p)) {
                    hasStairs = true;
                    break;
                }
            }
            result.add(new Staircase(toBlockPosList(ss.steps), ss.dir, hasStairs));
        }

        return result;
    }

    /** packed long の段リストを BlockPos リストへ変換する（採用された階段のみ。候補全件では行わない）。 */
    private static List<BlockPos> toBlockPosList(LongList steps) {
        List<BlockPos> list = new ArrayList<>(steps.size());
        for (long p : steps) {
            list.add(BlockPos.of(p));
        }
        return list;
    }

    /**
     * 指定ブロックが階段の段として歩可能か。
     * 候補（固体）であり、かつ上が非固体（頭上に空間がある）であること。
     */
    private static boolean isWalkableStep(LongSet candidates, long pos, BlockMap blockMap) {
        if (!candidates.contains(pos)) return false;
        return !blockMap.isSolid(BlockPos.getX(pos), BlockPos.getY(pos) + 1, BlockPos.getZ(pos));
    }

    /**
     * 指定方向の最下段を見つける。
     * 逆方向・1下 に「上が非固体の候補」が続く限り下る。
     */
    private static long findBottom(LongSet candidates, long pos, Direction dir, BlockMap blockMap) {
        long cur = pos;
        while (true) {
            int nx = BlockPos.getX(cur) + dir.getOpposite().getStepX();
            int ny = BlockPos.getY(cur) - 1;
            int nz = BlockPos.getZ(cur) + dir.getOpposite().getStepZ();
            long next = BlockPos.asLong(nx, ny, nz);
            if (!isWalkableStep(candidates, next, blockMap)) break;
            cur = next;
        }
        return cur;
    }

    /**
     * 最下段から指定方向に上昇してシーケンスを構成。
     * 正方向・1上 に「上が非固体の候補」が続く限り上る。
     * 各段の上が非固体でなければ階段の段として成立しないため打ち切る。
     */
    private static LongList extendUp(LongSet candidates, long bottom, Direction dir, BlockMap blockMap) {
        LongList seq = new LongArrayList();
        long cur = bottom;
        while (isWalkableStep(candidates, cur, blockMap)) {
            seq.add(cur);
            int nx = BlockPos.getX(cur) + dir.getStepX();
            int ny = BlockPos.getY(cur) + 1;
            int nz = BlockPos.getZ(cur) + dir.getStepZ();
            cur = BlockPos.asLong(nx, ny, nz);
        }
        return seq;
    }

    /** (最下段, 方向) → long のハッシュキー生成 */
    private static long sequenceKey(long bottom, Direction dir) {
        return (bottom << 3) | dir.get3DDataValue();
    }

    /** 内部用シーケンスホルダー */
    private static final class StairSeq {
        final LongList steps;
        final Direction dir;

        StairSeq(LongList steps, Direction dir) {
            this.steps = steps;
            this.dir = dir;
        }
    }
}
