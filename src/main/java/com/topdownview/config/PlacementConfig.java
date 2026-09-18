package com.topdownview.config;

import com.topdownview.util.MathUtil;

/**
 * ブロック配置プレビューに関する設定項目を保持する構成クラス。
 */
public final class PlacementConfig {

    private boolean placementPreviewEnabled = true;
    private double placementTransparency = 0.5;
    private boolean clickPositionPlacementEnabled = true;

    public boolean isPlacementPreviewEnabled() { return placementPreviewEnabled; }
    public void setPlacementPreviewEnabled(boolean value) { this.placementPreviewEnabled = value; }

    public double getPlacementTransparency() { return placementTransparency; }
    public void setPlacementTransparency(double value) { this.placementTransparency = MathUtil.clamp(value, 0.1, 0.9); }

    public boolean isClickPositionPlacementEnabled() { return clickPositionPlacementEnabled; }
    public void setClickPositionPlacementEnabled(boolean value) { this.clickPositionPlacementEnabled = value; }

}
