package com.topdownview.state;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * ブロック配置方向手動指定の状態
 *
 * プレイヤーの視線に依存せず、ブロックの向き（facing）を
 * 手動で指定する機能の状態を管理する。
 *
 * サーバー側で TopDownView がロードされている場合のみ有効。
 * クライアントは現在の指定向きを hitX にエンコードして送信し、
 * サーバー側 Mixin が復号して BlockState を差し替える。
 *
 * 絶対方向基準（世界の北基準）で動作する。
 */
public final class PlacementRotationState {

    public static final PlacementRotationState INSTANCE = new PlacementRotationState();

    // 水平方向サイクル順序: NORTH → EAST → SOUTH → WEST → NORTH
    private static final Direction[] HORIZONTAL_CYCLE = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private volatile Direction currentFacing = null;
    private volatile boolean enabled = false;

    private PlacementRotationState() {}

    // ==================== Getters ====================

    public Direction getCurrentFacing() {
        return currentFacing;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 配置方向オーバーライドが有効か（enabled かつ currentFacing が null でない）
     */
    public boolean hasOverride() {
        return enabled && currentFacing != null;
    }

    // ==================== Setters ====================

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            currentFacing = null;
        }
    }

    public void setCurrentFacing(Direction facing) {
        if (facing == null) {
            currentFacing = null;
        } else {
            currentFacing = facing;
        }
    }

    public void clearFacing() {
        currentFacing = null;
    }

    // ==================== 操作メソッド ====================

    /**
     * 上下方向をトグルする。
     * UP → DOWN → UP の順で切り替え。
     * 現在が水平方向の場合は UP に設定。
     */
    public void toggleVertical() {
        if (!enabled) return;
        if (currentFacing == Direction.UP) {
            currentFacing = Direction.DOWN;
        } else if (currentFacing == Direction.DOWN) {
            currentFacing = Direction.UP;
        } else {
            currentFacing = Direction.UP;
        }
    }

    /**
     * 水平方向を時計回りにサイクルする。
     * NORTH → EAST → SOUTH → WEST → NORTH
     * 現在が上下方向の場合は NORTH にリセット。
     */
    public void cycleHorizontalClockwise() {
        if (!enabled) return;
        if (currentFacing == null || currentFacing.getAxis() == Direction.Axis.Y) {
            currentFacing = Direction.NORTH;
            return;
        }
        int idx = currentFacing.get2DDataValue();
        if (idx < 0) {
            currentFacing = Direction.NORTH;
            return;
        }
        int next = Mth.positiveModulo(idx + 1, 4);
        currentFacing = HORIZONTAL_CYCLE[next];
    }

    // ==================== リセット ====================

    public void reset() {
        currentFacing = null;
        enabled = false;
    }
}
