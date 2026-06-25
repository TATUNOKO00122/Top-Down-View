package com.topdownview.placement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import net.minecraft.world.item.context.BlockPlaceContext;
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
        throw new IllegalStateException("ユーティリティクラス");
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

        return state;
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

    /**
     * ブロックのクリック位置から自動的に設置の向きを算出する。
     *
     * @param context 配置コンテキスト
     * @return 算出された向き（null の場合は適用しない）
     */
    @Nullable
    public static Direction calculateClickPositionFacing(BlockPlaceContext context) {
        net.minecraft.world.phys.Vec3 hitPos = context.getClickLocation();
        net.minecraft.core.BlockPos blockPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        // 側面をクリックした場合は、その反対方向を設置向きとする（壁吸着）
        if (clickedFace.getAxis() != Direction.Axis.Y) {
            return clickedFace.getOpposite();
        }

        // 上面または下面をクリックした場合は、クリック位置がブロック中心から見てどの方向にあるかで判定する
        double centerX = blockPos.getX() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;
        double dx = hitPos.x - centerX;
        double dz = hitPos.z - centerZ;

        double horizDistSqr = dx * dx + dz * dz;
        if (horizDistSqr < 0.001) {
            return null;
        }

        return Direction.getNearest(dx, 0.0, dz);
    }

    /**
     * ブロックがすでにクリックされた面に沿って適切に配向されているか判定する（看板や松明などの壁設置ブロック判定）。
     *
     * @param state 元の BlockState
     * @param clickedFace クリックされた面
     * @return すでにクリック面と一致する方向に配向されている場合 true
     */
    public static boolean isAlreadyAlignedToFace(BlockState state, Direction clickedFace) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING) == clickedFace;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == clickedFace;
        }
        return false;
    }
}
