package com.topdownview.config;

/**
 * 他Mod（Baritone等）との連携機能に関する設定項目を保持する構成クラス。
 */
public final class IntegrationsConfig {

    private boolean baritoneRenderPath = true;
    private boolean baritoneRenderGoal = true;

    public boolean isBaritoneRenderPath() { return baritoneRenderPath; }
    public void setBaritoneRenderPath(boolean value) { this.baritoneRenderPath = value; }

    public boolean isBaritoneRenderGoal() { return baritoneRenderGoal; }
    public void setBaritoneRenderGoal(boolean value) { this.baritoneRenderGoal = value; }
}
