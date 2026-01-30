package com.topdownview.state;

import com.topdownview.client.MountSteeringController;

/**
 * MOD状態管理のファサード
 * 分割された状態クラスへのアクセスを統一
 * 直接フィールドアクセスでパフォーマンスを最適化
 */
public final class ModState {

    private ModState() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    // 各状態への直接参照を提供
    public static final CameraState CAMERA = CameraState.INSTANCE;
    public static final TimeState TIME = TimeState.INSTANCE;
    public static final ModStatus STATUS = ModStatus.INSTANCE;
    public static final ClickToMoveState CLICK_TO_MOVE = ClickToMoveState.INSTANCE;
    public static final TargetHighlightState TARGET_HIGHLIGHT = TargetHighlightState.INSTANCE;
    public static final DestinationHighlightState DESTINATION_HIGHLIGHT = DestinationHighlightState.INSTANCE;
    public static final PlayerRotationState PLAYER_ROTATION = PlayerRotationState.INSTANCE;
    public static final TargetLockState TARGET_LOCK = TargetLockState.INSTANCE;
    public static final SpaceDebugState SPACE_DEBUG = SpaceDebugState.INSTANCE;
    public static final PlacementRotationState PLACEMENT_ROTATION = PlacementRotationState.INSTANCE;

    /**
     * 全状態をリセット
     */
    public static void resetAll() {
        CAMERA.reset();
        TIME.reset();
        STATUS.reset();
        CLICK_TO_MOVE.reset();
        TARGET_HIGHLIGHT.reset();
        DESTINATION_HIGHLIGHT.reset();
        PLAYER_ROTATION.reset();
        TARGET_LOCK.reset();
        SPACE_DEBUG.reset();
        PLACEMENT_ROTATION.reset();
        MountSteeringController.reset();
    }
}