package com.topdownview.spatial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;

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
 *   <li>center 周辺の立方体（2*radius+1 辺）をスキャンし、候補を収集</li>
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
     * 指定中心位置の周辺から階段を検出する。空間探索結果に依存しない。
     *
     * @param level    ワールド（StairBlock判定・歩可行性判定に使用）
     * @param center   スキャン中心位置（通常はプレイヤー位置）
     * @param radius   スキャン半径（center を中心とした立方体の辺 = 2*radius+1）
     * @param minSteps 階段として認定する最小段数（3以上を推奨）
     * @return 検出された階段リスト（重複ブロックなし、長い順）
     */
    public static List<Staircase> detect(BlockGetter level, BlockPos center, int radius, int minSteps) {
        if (level == null || center == null || radius < 1 || minSteps < 1) {
            return List.of();
        }

        // center 周辺の立方体をスキャンし、階段の段となりうるブロックを収集。
        // 候補 = 固体ブロック かつ 上が非固体（歩行可能な段）。
        Set<BlockPos> candidates = new HashSet<>();
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos aboveMut = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    mut.set(x, y, z);
                    aboveMut.set(x, y + 1, z);
                    if (WallAnalyzer.isSolid(level, mut) && !WallAnalyzer.isSolid(level, aboveMut)) {
                        candidates.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (candidates.size() < minSteps) {
            return List.of();
        }

        // StairBlock をマーク
        Set<BlockPos> stairBlocks = new HashSet<>();
        for (BlockPos p : candidates) {
            if (isStairBlock(level, p)) {
                stairBlocks.add(p);
            }
        }

        // 全候補・全方向の最大シーケンスを収集
        // 重複排除：(最下段, 方向) のペアで一意にする
        Set<Long> seenSequences = new HashSet<>();
        List<StairSeq> sequences = new ArrayList<>();

        for (BlockPos start : candidates) {
            for (Direction dir : HORIZONTAL) {
                BlockPos bottom = findBottom(level, candidates, start, dir);
                long key = sequenceKey(bottom, dir);
                if (!seenSequences.add(key)) continue;

                List<BlockPos> seq = extendUp(level, candidates, bottom, dir);
                if (seq.size() >= minSteps) {
                    sequences.add(new StairSeq(seq, dir));
                }
            }
        }

        // 長い順にソートしてブロック重複を排除しながら採用
        sequences.sort((a, b) -> Integer.compare(b.steps.size(), a.steps.size()));

        Set<BlockPos> used = new HashSet<>();
        List<Staircase> result = new ArrayList<>();
        for (StairSeq ss : sequences) {
            // 重複チェック
            boolean overlap = false;
            for (BlockPos p : ss.steps) {
                if (used.contains(p)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            used.addAll(ss.steps);

            boolean hasStairs = false;
            for (BlockPos p : ss.steps) {
                if (stairBlocks.contains(p)) {
                    hasStairs = true;
                    break;
                }
            }
            result.add(new Staircase(ss.steps, ss.dir, hasStairs));
        }

        return result;
    }

    /**
     * 指定ブロックが階段の段として歩可能か。
     * 候補（固体）であり、かつ上が非固体（頭上に空間がある）であること。
     */
    private static boolean isWalkableStep(BlockGetter level, Set<BlockPos> candidates, BlockPos pos) {
        if (!candidates.contains(pos)) return false;
        return !WallAnalyzer.isSolid(level, pos.above());
    }

    /**
     * 指定方向の最下段を見つける。
     * 逆方向・1下 に「上が非固体の候補」が続く限り下る。
     */
    private static BlockPos findBottom(BlockGetter level, Set<BlockPos> candidates, BlockPos pos, Direction dir) {
        BlockPos cur = pos;
        while (true) {
            BlockPos next = cur.relative(dir.getOpposite()).below();
            if (!isWalkableStep(level, candidates, next)) break;
            cur = next;
        }
        return cur;
    }

    /**
     * 最下段から指定方向に上昇してシーケンスを構成。
     * 正方向・1上 に「上が非固体の候補」が続く限り上る。
     * 各段の上が非固体でなければ階段の段として成立しないため打ち切る。
     */
    private static List<BlockPos> extendUp(BlockGetter level, Set<BlockPos> candidates, BlockPos bottom, Direction dir) {
        List<BlockPos> seq = new ArrayList<>();
        BlockPos cur = bottom;
        while (isWalkableStep(level, candidates, cur)) {
            seq.add(cur);
            cur = cur.relative(dir).above();
        }
        return seq;
    }

    /** (最下段, 方向) → long のハッシュキー生成 */
    private static long sequenceKey(BlockPos bottom, Direction dir) {
        return (bottom.asLong() << 3) | dir.get3DDataValue();
    }

    /** バニラの階段ブロック(StairBlock)かどうか */
    private static boolean isStairBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof StairBlock;
    }

    /** 内部用シーケンスホルダー */
    private static final class StairSeq {
        final List<BlockPos> steps;
        final Direction dir;

        StairSeq(List<BlockPos> steps, Direction dir) {
            this.steps = steps;
            this.dir = dir;
        }
    }
}
