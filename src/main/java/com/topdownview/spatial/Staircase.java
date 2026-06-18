package com.topdownview.spatial;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 検出された階段データ。
 *
 * <p>空間内で階段状に配置されたブロック列を表す。
 * 各段は前の段から水平方向に1ブロック・垂直方向に+1ブロックの位置にある。
 * 不変オブジェクト。
 */
public final class Staircase {

    private final List<BlockPos> steps;
    private final Direction ascendDirection;
    private final boolean containsStairBlocks;

    public Staircase(List<BlockPos> steps, Direction ascendDirection, boolean containsStairBlocks) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        this.ascendDirection = Objects.requireNonNull(ascendDirection, "ascendDirection");
        this.containsStairBlocks = containsStairBlocks;
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
    }

    /** 段を下から上へ順序付けたリスト */
    public List<BlockPos> getSteps() {
        return steps;
    }

    /** 段数 */
    public int getStepCount() {
        return steps.size();
    }

    /** 上昇方向（下から上へ進む水平方向） */
    public Direction getAscendDirection() {
        return ascendDirection;
    }

    /** バニラの階段ブロック(StairBlock)が含まれるか */
    public boolean containsStairBlocks() {
        return containsStairBlocks;
    }

    /** 最下段の位置 */
    public BlockPos getBottomPos() {
        return steps.get(0);
    }

    /** 最上段の位置 */
    public BlockPos getTopPos() {
        return steps.get(steps.size() - 1);
    }

    @Override
    public String toString() {
        return "Staircase{steps=" + steps.size() + ", dir=" + ascendDirection
                + ", stairBlocks=" + containsStairBlocks
                + ", bottom=" + getBottomPos() + "}";
    }
}
