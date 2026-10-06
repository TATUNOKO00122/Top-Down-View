package com.topdownview.config;

import com.topdownview.util.MathUtil;

/**
 * ブロック/モブのカリングおよび透過表示に関する設定項目を保持する構成クラス。
 */
public final class CullingConfig {

    /** 旧方式: 円柱カリングのみ。 */
    public static final int CULLING_MODE_CYLINDER = 0;
    /** 新方式: 覆いカリング + 手前の壁コリドー(カメラ側で限定した円柱)。 */
    public static final int CULLING_MODE_COVER_CORRIDOR = 1;

    private int cylinderRadiusHorizontal = 5;
    private int cylinderRadiusVertical = 5;
    private int cylinderForwardShift = 1;
    private int miningCylinderRadius = 5;
    private int miningCylinderForwardShift = 0;
    private boolean mobCullingEnabled = true;
    private boolean itemCullingEnabled = true;
    private boolean mobTranslucencyEnabled = false;
    private double mobTranslucencyAlpha = 0.5;
    private boolean trapdoorTranslucencyEnabled = false;
    private double trapdoorTransparency = 0.3;
    private boolean fadeEnabled = true;
    private double fadeFlashDuration = 0.3;
    private boolean disableFadeIndoors = false;
    private boolean disableFadeBuried = true;
    private boolean mobConeCullingEnabled = false;
    private double mobConeHalfAngle = 30.0;
    private double mobConeFadeAngle = 10.0;
    private double mobNearRadius = 3.0;
    private double mobFogEnd = 12.0;
    private boolean staircaseExclusionEnabled = true;
    private int staircaseExclusionHeight = 5;
    private int viewWedgeHalfAngle = 60;
    private boolean cameraSideClipWedge = true;
    private boolean coverCullingEnabled = true;
    private int coverCullingRadius = 5;
    private boolean coverCullingViewshedEnabled = true;
    private boolean coverCullingSolidsOutdoors = false;
    private int cullingMode = CULLING_MODE_COVER_CORRIDOR;
    private boolean indoorCeilingCullingEnabled = true;
    private boolean protectInteractablesOutdoors = true;
    private boolean ignoreLeavesInRaycast = false;
    private boolean protectNaturalTreeLogs = false;
    private boolean translucentFluid = true;
    private double fluidAlpha = 0.35;

    private boolean connectedWallCullingEnabled = true;
    private int connectedWallMaxDistance = 3;

    public boolean isConnectedWallCullingEnabled() { return connectedWallCullingEnabled; }
    public void setConnectedWallCullingEnabled(boolean value) { this.connectedWallCullingEnabled = value; }

    public int getConnectedWallMaxDistance() { return connectedWallMaxDistance; }
    public void setConnectedWallMaxDistance(int value) { this.connectedWallMaxDistance = MathUtil.clamp(value, 1, 6); }

    public int getCylinderRadiusHorizontal() { return cylinderRadiusHorizontal; }
    public void setCylinderRadiusHorizontal(int value) { this.cylinderRadiusHorizontal = MathUtil.clamp(value, 1, 10); }

    public int getCylinderRadiusVertical() { return cylinderRadiusVertical; }
    public void setCylinderRadiusVertical(int value) { this.cylinderRadiusVertical = MathUtil.clamp(value, 1, 10); }

    public int getCylinderForwardShift() { return cylinderForwardShift; }
    public void setCylinderForwardShift(int value) { this.cylinderForwardShift = MathUtil.clamp(value, 0, 10); }

    public int getMiningCylinderRadius() { return miningCylinderRadius; }
    public void setMiningCylinderRadius(int value) { this.miningCylinderRadius = MathUtil.clamp(value, 1, 16); }

    public int getMiningCylinderForwardShift() { return miningCylinderForwardShift; }
    public void setMiningCylinderForwardShift(int value) { this.miningCylinderForwardShift = MathUtil.clamp(value, 0, 10); }

    public boolean isMobCullingEnabled() { return mobCullingEnabled; }
    public void setMobCullingEnabled(boolean value) { this.mobCullingEnabled = value; }

    public boolean isItemCullingEnabled() { return itemCullingEnabled; }
    public void setItemCullingEnabled(boolean value) { this.itemCullingEnabled = value; }

    public boolean isMobTranslucencyEnabled() { return mobTranslucencyEnabled; }
    public void setMobTranslucencyEnabled(boolean value) { this.mobTranslucencyEnabled = value; }

    public double getMobTranslucencyAlpha() { return mobTranslucencyAlpha; }
    public void setMobTranslucencyAlpha(double value) { this.mobTranslucencyAlpha = MathUtil.clamp(value, 0.0, 1.0); }

    public boolean isTrapdoorTranslucencyEnabled() { return trapdoorTranslucencyEnabled; }
    public void setTrapdoorTranslucencyEnabled(boolean value) { this.trapdoorTranslucencyEnabled = value; }

    public double getTrapdoorTransparency() { return trapdoorTransparency; }
    public void setTrapdoorTransparency(double value) { this.trapdoorTransparency = MathUtil.clamp(value, 0.0, 1.0); }

    public boolean isFadeEnabled() { return fadeEnabled; }
    public void setFadeEnabled(boolean value) { this.fadeEnabled = value; }

    /** 消失/復元フェードの秒数。0 = 即時切替(遷移なし)。 */
    public double getFadeFlashDuration() { return fadeFlashDuration; }
    public void setFadeFlashDuration(double value) { this.fadeFlashDuration = MathUtil.clamp(value, 0.0, 1.0); }

    public boolean isDisableFadeIndoors() { return disableFadeIndoors; }
    public void setDisableFadeIndoors(boolean value) { this.disableFadeIndoors = value; }

    /** カメラが地形に埋没している間、遷移フェードを抑制する。 */
    public boolean isDisableFadeBuried() { return disableFadeBuried; }
    public void setDisableFadeBuried(boolean value) { this.disableFadeBuried = value; }

    public boolean isMobConeCullingEnabled() { return mobConeCullingEnabled; }
    public void setMobConeCullingEnabled(boolean value) { this.mobConeCullingEnabled = value; }

    public double getMobConeHalfAngle() { return mobConeHalfAngle; }
    public void setMobConeHalfAngle(double value) { this.mobConeHalfAngle = MathUtil.clamp(value, 10.0, 90.0); }

    public double getMobConeFadeAngle() { return mobConeFadeAngle; }
    public void setMobConeFadeAngle(double value) { this.mobConeFadeAngle = MathUtil.clamp(value, 0.0, 90.0); }

    public double getMobNearRadius() { return mobNearRadius; }
    public void setMobNearRadius(double value) { this.mobNearRadius = MathUtil.clamp(value, 0.0, 20.0); }

    public double getMobFogEnd() { return mobFogEnd; }
    public void setMobFogEnd(double value) { this.mobFogEnd = MathUtil.clamp(value, 1.0, 50.0); }

    public boolean isStaircaseExclusionEnabled() { return staircaseExclusionEnabled; }
    public void setStaircaseExclusionEnabled(boolean value) { this.staircaseExclusionEnabled = value; }

    public int getStaircaseExclusionHeight() { return staircaseExclusionHeight; }
    public void setStaircaseExclusionHeight(int value) { this.staircaseExclusionHeight = MathUtil.clamp(value, 1, 10); }

    public int getViewWedgeHalfAngle() { return viewWedgeHalfAngle; }
    public void setViewWedgeHalfAngle(int value) { this.viewWedgeHalfAngle = MathUtil.clamp(value, 10, 90); }

    /** true = 旧来の扇形(コーン)、false = 角度制限なしの水平半空間クリップ。 */
    public boolean isCameraSideClipWedge() { return cameraSideClipWedge; }
    public void setCameraSideClipWedge(boolean value) { this.cameraSideClipWedge = value; }

    public boolean isCoverCullingEnabled() { return coverCullingEnabled; }
    public void setCoverCullingEnabled(boolean value) { this.coverCullingEnabled = value; }

    public int getCoverCullingRadius() { return coverCullingRadius; }
    public void setCoverCullingRadius(int value) { this.coverCullingRadius = MathUtil.clamp(value, 4, 24); }

    public boolean isCoverCullingViewshedEnabled() { return coverCullingViewshedEnabled; }
    public void setCoverCullingViewshedEnabled(boolean value) { this.coverCullingViewshedEnabled = value; }

    public boolean isCoverCullingSolidsOutdoors() { return coverCullingSolidsOutdoors; }
    public void setCoverCullingSolidsOutdoors(boolean value) { this.coverCullingSolidsOutdoors = value; }

    public int getCullingMode() { return cullingMode; }
    public void setCullingMode(int value) { this.cullingMode = MathUtil.clamp(value, CULLING_MODE_CYLINDER, CULLING_MODE_COVER_CORRIDOR); }

    public boolean isIndoorCeilingCullingEnabled() { return indoorCeilingCullingEnabled; }
    public void setIndoorCeilingCullingEnabled(boolean value) { this.indoorCeilingCullingEnabled = value; }

    public boolean isProtectInteractablesOutdoors() { return protectInteractablesOutdoors; }
    public void setProtectInteractablesOutdoors(boolean value) { this.protectInteractablesOutdoors = value; }

    public boolean isIgnoreLeavesInRaycast() { return ignoreLeavesInRaycast; }
    public void setIgnoreLeavesInRaycast(boolean value) { this.ignoreLeavesInRaycast = value; }

    public boolean isProtectNaturalTreeLogs() { return protectNaturalTreeLogs; }
    public void setProtectNaturalTreeLogs(boolean value) { this.protectNaturalTreeLogs = value; }

    public boolean isTranslucentFluid() { return translucentFluid; }
    public void setTranslucentFluid(boolean value) { this.translucentFluid = value; }

    public double getFluidAlpha() { return fluidAlpha; }
    public void setFluidAlpha(double value) { this.fluidAlpha = MathUtil.clamp(value, 0.05, 1.0); }

    private boolean undergroundCullingEnabled = false;
    private int undergroundCullingStartDistance = 4;
    private int undergroundCullingKeepDepth = 16;

    public boolean isUndergroundCullingEnabled() { return undergroundCullingEnabled; }
    public void setUndergroundCullingEnabled(boolean value) { this.undergroundCullingEnabled = value; }

    public int getUndergroundCullingStartDistance() { return undergroundCullingStartDistance; }
    public void setUndergroundCullingStartDistance(int value) { this.undergroundCullingStartDistance = MathUtil.clamp(value, 1, 16); }

    public int getUndergroundCullingKeepDepth() { return undergroundCullingKeepDepth; }
    public void setUndergroundCullingKeepDepth(int value) { this.undergroundCullingKeepDepth = MathUtil.clamp(value, 4, 64); }

    private boolean dollhouseEnabled = false;
    private double dollhouseExteriorBrightness = 0.0;

    public boolean isDollhouseEnabled() { return dollhouseEnabled; }
    public void setDollhouseEnabled(boolean value) { this.dollhouseEnabled = value; }

    public double getDollhouseExteriorBrightness() { return dollhouseExteriorBrightness; }
    public void setDollhouseExteriorBrightness(double value) { this.dollhouseExteriorBrightness = MathUtil.clamp(value, 0.0, 1.0); }

}
