package com.topdownview.compat;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 上下方向に連結した複数ブロックを1単位として扱うための補助クラス。
 *
 * <p>カリングはブロック単位で行われるため、1つの構造の一部だけがカリングされて見た目が
 * 欠けることがある。基準Yをその構造の最下段に揃え、上下で保護判定が食い違わないようにする。
 */
public final class VerticalUnitHelper {

    /** FastPaintings の絵画ブロック（未導入時は null）。 */
    private static final Class<?> FAST_PAINTINGS_PAINTING_BLOCK =
            loadClass("net.mehvahdjukaar.fastpaintings.PaintingBlock");

    private VerticalUnitHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 保護判定に使う基準Yを返す。上下2段ブロックは下段、FastPaintings の絵画タイルは
     * 最下段(master)のYに揃える。それ以外はそのままのYを返す。
     */
    public static int getUnitAnchorY(BlockState state, int posY) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return posY - 1;
        }
        IntegerProperty yOffset = getFastPaintingsYOffset(state);
        if (yOffset != null) {
            return posY - state.getValue(yOffset);
        }
        return posY;
    }

    private static IntegerProperty getFastPaintingsYOffset(BlockState state) {
        if (FAST_PAINTINGS_PAINTING_BLOCK == null || !FAST_PAINTINGS_PAINTING_BLOCK.isInstance(state.getBlock())) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && "y_offset".equals(property.getName())) {
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
