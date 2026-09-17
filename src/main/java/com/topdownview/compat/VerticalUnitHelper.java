package com.topdownview.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 上下方向に連結した複数ブロックを1単位として扱うための補助クラス。
 *
 * <p>カリングはブロック単位で行われるため、1つの構造の一部だけがカリングされて見た目が
 * 欠けることがある。基準位置をその構造の最下段に揃え、上下で保護判定が食い違わないようにする。
 * 保護はYが低いほど効くため、最下段基準にすると「一部でも保護されていれば全体を表示」になる。
 */
public final class VerticalUnitHelper {

    /** FastPaintings の絵画ブロック（未導入時は null）。 */
    private static final Class<?> FAST_PAINTINGS_PAINTING_BLOCK =
            loadClass("net.mehvahdjukaar.fastpaintings.PaintingBlock");

    /** FastPaintings の絵画は最大5段。チャンク未読込で切れても足りる上限。 */
    private static final int MAX_PAINTING_HEIGHT = 8;

    private static final ThreadLocal<BlockPos.MutableBlockPos> ANCHOR =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCAN =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private VerticalUnitHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 保護判定に使う基準Y（その構造の最下段）を返す。 */
    public static int getUnitAnchorY(BlockState state, BlockPos pos, BlockGetter level) {
        return getUnitAnchorPos(state, pos, level).getY();
    }

    /**
     * 保護・カリング判定に使う基準位置を返す。2段ブロックは下段、FastPaintings の絵画は
     * master（最上段）の列の最下段に揃える。それ以外はそのままの位置。
     *
     * <p>戻り値は ThreadLocal の可変ブロック。呼び出し側で即座に読み出し、保持しないこと。
     * チャンク構築ワーカーからも呼ばれるため、位置ごとに新しいインスタンスは作らない。
     */
    public static BlockPos getUnitAnchorPos(BlockState state, BlockPos pos, BlockGetter level) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return ANCHOR.get().set(pos.getX(), pos.getY() - 1, pos.getZ());
        }
        if (isFastPaintingsPainting(state)) {
            return getPaintingAnchorPos(state, pos, level);
        }
        return ANCHOR.get().set(pos);
    }

    /**
     * 絵画は master が最上段で y_offset が下向きに増える。全タイルが同じ master に解決する
     * ことを利用し、master の列を最下段まで下ろした位置を単位の基準にする。
     */
    private static BlockPos getPaintingAnchorPos(BlockState state, BlockPos pos, BlockGetter level) {
        IntegerProperty yOffset = getPaintingOffset(state, "y_offset");
        IntegerProperty xOffset = getPaintingOffset(state, "x_offset");
        int down = yOffset == null ? 0 : state.getValue(yOffset);
        int right = xOffset == null ? 0 : state.getValue(xOffset);
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        Direction side = facing.getClockWise();
        int masterX = pos.getX() + side.getStepX() * right;
        int masterZ = pos.getZ() + side.getStepZ() * right;
        int masterY = pos.getY() + down;

        BlockPos.MutableBlockPos scan = SCAN.get();
        int bottomY = masterY;
        for (int i = 0; i < MAX_PAINTING_HEIGHT; i++) {
            BlockState below = level.getBlockState(scan.set(masterX, bottomY - 1, masterZ));
            if (!FAST_PAINTINGS_PAINTING_BLOCK.isInstance(below.getBlock())) {
                break;
            }
            bottomY--;
        }
        return ANCHOR.get().set(masterX, bottomY, masterZ);
    }

    private static boolean isFastPaintingsPainting(BlockState state) {
        return FAST_PAINTINGS_PAINTING_BLOCK != null && FAST_PAINTINGS_PAINTING_BLOCK.isInstance(state.getBlock());
    }

    private static IntegerProperty getPaintingOffset(BlockState state, String name) {
        if (!isFastPaintingsPainting(state)) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && name.equals(property.getName())) {
                return integerProperty;
            }
        }
        return null;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
