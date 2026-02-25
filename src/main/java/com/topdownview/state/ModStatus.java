package com.topdownview.state;

/**
 * MOD有効状態管理
 * トップダウンビューの有効/無効状態を管理
 */
public final class ModStatus {

    // シングルトンインスタンス
    public static final ModStatus INSTANCE = new ModStatus();

    private boolean enabled = false;

    private ModStatus() {}

    // ==================== Getters ====================

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== Setters ====================

    public void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        enabled = false;
    }
}