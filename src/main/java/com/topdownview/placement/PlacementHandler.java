package com.topdownview.placement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nullable;

/**
 * ブロック配置方向の適用ロジック
 *
 * 指定された Direction を BlockState のプロパティに反映する。
 * UP/DOWN の場合は以下も同時に操作する:
 *   - HALF プロパティ（階段・トラップドア）: UP=TOP, DOWN=BOTTOM
 *   - SLAB_TYPE プロパティ（ハーフブロック）: UP=TOP, DOWN=BOTTOM
 *
 * 水平方向の場合は DirectionProperty のみ操作する。
 */
public final class PlacementHandler {

    private PlacementHandler() {
        throw new IllegalStateException("ユティリティクラス");
    }

    /**
     * BlockState の向き関連プロパティを指定向きで差し替える。
     *
     * @param state 元の配置状態
     * @param facing 指定向き（null の場合は state をそのまま返す）
     * @return プロパティを差し替えた BlockState
     */
    @Nullable
    public static BlockState applyFacing(@Nullable BlockState state, @Nullable Direction facing) {
        if (state == null || facing == null) {
            return state;
        }

        if (facing.getAxis() == Direction.Axis.Y) {
            return applyVertical(state, facing);
        }
        return applyHorizontal(state, facing);
    }

    /**
     * 垂直方向（UP/DOWN）を適用する。
     * DirectionProperty（UP/DOWN許容）、HALF、SLAB_TYPE を同時に操作。
     */
    private static BlockState applyVertical(BlockState state, Direction facing) {
        boolean changed = false;

        // DirectionProperty で UP/DOWN を許容するもの
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) {
                if (dp.getPossibleValues().contains(facing)) {
                    state = state.setValue(dp, facing);
                    changed = true;
                }
            }
        }

        // HALF プロパティ（階段・トラップドア: top/bottom）
        if (state.hasProperty(BlockStateProperties.HALF)) {
            Half half = (facing == Direction.UP) ? Half.TOP : Half.BOTTOM;
            state = state.setValue(BlockStateProperties.HALF, half);
            changed = true;
        }

        // SLAB_TYPE プロパティ（ハーフブロック: top/bottom/double）
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            // すでにダブルハーフブロック（DOUBLE）になっている場合は、向きの適用をスキップしてダブル状態を維持する
            if (state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
                SlabType slabType = (facing == Direction.UP) ? SlabType.TOP : SlabType.BOTTOM;
                if (BlockStateProperties.SLAB_TYPE.getPossibleValues().contains(slabType)) {
                    state = state.setValue(BlockStateProperties.SLAB_TYPE, slabType);
                    changed = true;
                }
            }
        }

        return changed ? state : state;
    }

    /**
     * 水平方向を適用する。
     * DirectionProperty（水平方向許容）のみ操作。HALF/SLAB_TYPE は維持。
     */
    private static BlockState applyHorizontal(BlockState state, Direction facing) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) {
                if (dp.getPossibleValues().contains(facing)) {
                    return state.setValue(dp, facing);
                }
            }
        }
        return state;
    }
}
