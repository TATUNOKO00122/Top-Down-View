package com.topdownview.state;

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

    /**
     * 全状態をリセット
     */
    public static void resetAll() {
        CAMERA.reset();
        TIME.reset();
        STATUS.reset();
    }
}