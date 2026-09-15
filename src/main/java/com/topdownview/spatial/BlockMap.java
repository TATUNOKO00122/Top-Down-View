package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Arrays;

/**
 * プローブ1回分のブロック判定キャッシュ。
 *
 * <p>{@link RoomFloodFill} / {@link BuildingClassifier} / {@link StairAnalyzer} は同じ領域の
 * ブロックを何度も参照する。従来は呼び出しごとに {@code getBlockState} と
 * {@code getCollisionShape} を実行していた。本クラスは起点を中心とした領域を {@code byte[]} に
 * 遅延キャッシュし、各セルの判定を1回に集約する。
 *
 * <p>遅延方式のため実際に触れたセルしか計算しない。領域外の座標はキャッシュせず都度計算する
 * (正しさは不変、性能のみ劣化)。
 *
 * <p>スレッドセーフではありません。単一スレッドで reset → 参照 の順に使ってください。
 */
public final class BlockMap {

    /** 固体 (衝突形状が非空、空気・葉を除く)。{@link WallAnalyzer#isSolid} と等価。 */
    private static final byte FLAG_SOLID = 1;
    /** 覆い (固体かつ非液体)。{@code RoomFloodFill.isCovered} の覆い判定と等価。 */
    private static final byte FLAG_COVER = 2;
    /** バニラ階段ブロック。 */
    private static final byte FLAG_STAIR = 4;

    /** 計算済みマーカー。未計算 (0) と区別する。 */
    private static final byte COMPUTED = (byte) 0x80;

    /** 実フラグのビットマスク。 */
    private static final byte FLAG_MASK = FLAG_SOLID | FLAG_COVER | FLAG_STAIR;

    /** 起点からの XZ 半径。flood/classifier の最大到達を覆う。 */
    public static final int RADIUS_XZ = RoomFloodFill.MAX_RADIUS_XZ + BuildingClassifier.T_MAX + 1;
    /** 起点からの下方半径。 */
    public static final int RADIUS_DOWN = RoomFloodFill.MAX_RADIUS_Y + BuildingClassifier.T_MAX + 1;
    /** 起点からの上方半径。天井覆いスキャン分を含む (seed は起点+2 までずれ得る)。 */
    public static final int RADIUS_UP = RoomFloodFill.CEILING_SCAN_HEIGHT + RoomFloodFill.MAX_RADIUS_Y + 2;

    private static final int SIZE_X = RADIUS_XZ * 2 + 1;
    private static final int SIZE_Y = RADIUS_DOWN + RADIUS_UP + 1;
    private static final int SIZE_Z = SIZE_X;

    private final byte[] flags = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    private BlockGetter level;
    private int originX;
    private int originY;
    private int originZ;

    /**
     * 起点を中心に領域を設定し、キャッシュをクリアする。
     *
     * @param level  ワールド
     * @param center 領域中心 (通常プレイヤー足元ブロック)
     */
    public void reset(BlockGetter level, BlockPos center) {
        this.level = level;
        this.originX = center.getX() - RADIUS_XZ;
        this.originY = center.getY() - RADIUS_DOWN;
        this.originZ = center.getZ() - RADIUS_XZ;
        Arrays.fill(flags, (byte) 0);
    }

    public boolean isSolid(int x, int y, int z) {
        return (flagsAt(x, y, z) & FLAG_SOLID) != 0;
    }

    public boolean isCover(int x, int y, int z) {
        return (flagsAt(x, y, z) & FLAG_COVER) != 0;
    }

    public boolean isStair(int x, int y, int z) {
        return (flagsAt(x, y, z) & FLAG_STAIR) != 0;
    }

    private byte flagsAt(int x, int y, int z) {
        int lx = x - originX;
        int ly = y - originY;
        int lz = z - originZ;
        if (lx < 0 || lx >= SIZE_X || ly < 0 || ly >= SIZE_Y || lz < 0 || lz >= SIZE_Z) {
            return computeFlags(x, y, z);
        }
        int idx = (lx * SIZE_Z + lz) * SIZE_Y + ly;
        byte f = flags[idx];
        if ((f & COMPUTED) == 0) {
            f = (byte) (computeFlags(x, y, z) | COMPUTED);
            flags[idx] = f;
        }
        return (byte) (f & FLAG_MASK);
    }

    private byte computeFlags(int x, int y, int z) {
        mpos.set(x, y, z);
        BlockState state = level.getBlockState(mpos);
        byte f = 0;
        if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
            if (!state.getCollisionShape(level, mpos, CollisionContext.empty()).isEmpty()) {
                f |= FLAG_SOLID;
                if (state.getFluidState().isEmpty()) {
                    f |= FLAG_COVER;
                }
            }
        }
        if (state.getBlock() instanceof StairBlock) {
            f |= FLAG_STAIR;
        }
        return f;
    }
}
