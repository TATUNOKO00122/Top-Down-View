package com.topdownview.state;

/**
 * MOD有効状態管理
 * トップダウンビューの有効/無効状態を管理
 */
public final class ModStatus {

    // シングルトンインスタンス
    public static final ModStatus INSTANCE = new ModStatus();

    private boolean enabled = false;
    private boolean initialized = false;
    private boolean miningMode = false;

    private ModStatus() {}

    // ==================== Getters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isMiningMode() {
        return miningMode;
    }

    // ==================== Setters ====================

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void setInitialized(boolean value) {
        initialized = value;
    }

    public void setMiningMode(boolean value) {
        miningMode = value;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        enabled = false;
        initialized = false;
        miningMode = false;
    }
}