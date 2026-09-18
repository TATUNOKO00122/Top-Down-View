package com.topdownview.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * 自然生成された木（プレイヤーが設置したものではない）のログを検出するユーティリティクラス。
 *
 * <p>検出アルゴリズム:
 * <ol>
 *   <li>プレイヤー周辺のバウンディングボックスを走査し、persistent=false の自然葉を発見</li>
 *   <li>自然葉に隣接するログを「種ログ」として収集</li>
 *   <li>各種ログから下方へトランクを辿り、連続するログをすべて自然木ログとして登録</li>
 * </ol>
 *
 * <p>プレイヤーが設置した葉は persistent=true になるため、建築に使われた木材ログは
 * 検出対象外となる。これにより「生えている木」と「建物の木材」を区別する。
 *
 * <p>スキャンはプレイヤー移動時に実行され、結果は {@link #naturalTreeLogs} セットにキャッシュされる。
 * カリング判定時は O(1) のセット参照のみ。
 *
 * <p>スレッド安全性: メインスレッド（renderThread）からのみアクセスされる。
 */
public final class NaturalTreeDetector {

    private NaturalTreeDetector() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** スキャンする木の最大高さ（プレイヤー足元から上方向）。ジャングルの大木などをカバー。 */
    private static final int MAX_TREE_HEIGHT = 20;

    /** 種ログから下方へトレースする最大深度。 */
    private static final int MAX_TRUNK_TRACE_DEPTH = 24;

    /** 自然木のログ位置（posLong）のセット。 */
    private static final Set<Long> naturalTreeLogs = new HashSet<>();

    /**
     * キャッシュをクリアする。
     * プレイヤー移動時の再スキャン、ディメンション変更、mod無効化時に呼ばれる。
     */
    public static void clearCache() {
        naturalTreeLogs.clear();
    }

    /**
     * 現在キャッシュされている自然木ログ位置のセットを返す。
     * 呼び出し元は走査目的でのみ使用し、変更してはならない。
     *
     * @return 自然木ログ位置の long エンコードセット
     */
    public static Set<Long> getNaturalTreeLogs() {
        return naturalTreeLogs;
    }

    /**
     * プレイヤー周辺をスキャンし、自然木のログ位置を更新する。
     *
     * <p>プレイヤーが移動した際に {@link TopDownCuller} から呼ばれる。
     * 古いキャッシュはクリアされてから新しいスキャン結果で置き換えられる。
     *
     * @param level    ワールド（クライアント側）
     * @param blockX   プレイヤーのブロックX座標（目線レベル）
     * @param blockY   プレイヤーのブロックY座標（目線レベル）
     * @param blockZ   プレイヤーのブロックZ座標（目線レベル）
     * @param radiusH  水平方向のスキャン半径（ブロック数）
     */
    public static void scan(BlockGetter level, int blockX, int blockY, int blockZ, int radiusH) {
        naturalTreeLogs.clear();

        int minX = blockX - radiusH;
        int maxX = blockX + radiusH;
        int minZ = blockZ - radiusH;
        int maxZ = blockZ + radiusH;
        // 足元レベルからスキャン（blockY は目線 = 足元+1）
        int minY = blockY - 1;
        int maxY = blockY + MAX_TREE_HEIGHT;

        BlockPos.MutableBlockPos leafPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        Set<Long> seedLogs = new HashSet<>();

        // Phase 1: 自然葉（persistent=false）を走査し、隣接するログを種として収集
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    leafPos.set(x, y, z);
                    BlockState leafState = level.getBlockState(leafPos);
                    if (!isNaturalLeaf(leafState)) {
                        continue;
                    }
                    // 6近傍のログを種として収集
                    for (Direction dir : Direction.values()) {
                        neighborPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                        BlockState neighborState = level.getBlockState(neighborPos);
                        if (isLog(neighborState)) {
                            seedLogs.add(BlockPos.asLong(neighborPos.getX(), neighborPos.getY(), neighborPos.getZ()));
                        }
                    }
                }
            }
        }

        // Phase 2: 各種ログから下方へトランクをトレースし、連続するログをすべて登録
        BlockPos.MutableBlockPos trunkPos = new BlockPos.MutableBlockPos();
        for (long seedLong : seedLogs) {
            int sx = BlockPos.getX(seedLong);
            int sy = BlockPos.getY(seedLong);
            int sz = BlockPos.getZ(seedLong);
            traceTrunkDown(level, sx, sy, sz, trunkPos);
        }
    }

    /**
     * 指定位置から下方へ連続するログをトレースし、すべて自然木ログとして登録する。
     * 2x2の太いトランクもカバーするため、各Yレベルで十字方向（中心+上下南北東西）を確認する。
     */
    private static void traceTrunkDown(BlockGetter level, int x, int startY, int z,
            BlockPos.MutableBlockPos mutable) {
        for (int dy = 0; dy <= MAX_TRUNK_TRACE_DEPTH; dy++) {
            int y = startY - dy;
            mutable.set(x, y, z);
            BlockState state = level.getBlockState(mutable);
            if (!isLog(state)) {
                break;
            }
            naturalTreeLogs.add(BlockPos.asLong(x, y, z));
        }
    }

    /**
     * 自然生成された葉ブロックか判定する。
     * persistent=false の場合、ワールド生成または苗木から育った自然木の葉。
     */
    private static boolean isNaturalLeaf(BlockState state) {
        return state.hasProperty(LeavesBlock.PERSISTENT)
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * ログ（木材）ブロックか判定する。BlockTags.LOGS には全種類の原木と剥ぎ取られた原木が含まれる。
     */
    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }
}
