package com.topdownview.config;

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
    private boolean mobTranslucencyEnabled = false;
    private double mobTranslucencyAlpha = 0.5;
    private boolean trapdoorTranslucencyEnabled = false;
    private double trapdoorTransparency = 0.3;
    private boolean fadeEnabled = false;
    private double fadeBlockHitThreshold = 0.5;
    private double fadeStart = 0.7;
    private double fadeNearAlpha = 0.0;
    private double fadeSmoothingHalfLife = 0.14;
    private boolean disableFadeIndoors = true;
    private boolean playerNearTranslucencyEnabled = true;
    private double playerNearTranslucencyAlpha = 0.6;
    private int playerNearTranslucencyRangeHorizontal = 2;
    private int playerNearTranslucencyRangeVertical = 2;
    private boolean playerNearTranslucencyHittable = true;
    private boolean mobConeCullingEnabled = false;
    private double mobConeHalfAngle = 30.0;
    private double mobConeFadeAngle = 10.0;
    private double mobNearRadius = 3.0;
    private double mobFogEnd = 12.0;
    private boolean staircaseExclusionEnabled = true;
    private int staircaseExclusionHeight = 2;
    private boolean staircaseOccludeEnabled = true;
    private double staircaseOccludeAlpha = 0.4;
    private boolean ladderOccludeEnabled = true;
    private double ladderOccludeAlpha = 0.4;
    private boolean treeOccludeEnabled = true;
    private double treeOccludeAlpha = 0.4;
    private int viewWedgeHalfAngle = 60;
    private int coverCullingRadius = 10;
    private boolean coverCullingViewshedEnabled = true;
    private int cullingMode = CULLING_MODE_COVER_CORRIDOR;
    private boolean indoorCeilingCullingEnabled = true;
    private boolean ignoreLeavesInRaycast = false;
    private boolean protectNaturalTreeLogs = false;
    private boolean translucentFluid = true;
    private double fluidAlpha = 0.35;

    public int getCylinderRadiusHorizontal() { return cylinderRadiusHorizontal; }
    public void setCylinderRadiusHorizontal(int value) { this.cylinderRadiusHorizontal = clamp(value, 1, 10); }

    public int getCylinderRadiusVertical() { return cylinderRadiusVertical; }
    public void setCylinderRadiusVertical(int value) { this.cylinderRadiusVertical = clamp(value, 1, 10); }

    public int getCylinderForwardShift() { return cylinderForwardShift; }
    public void setCylinderForwardShift(int value) { this.cylinderForwardShift = clamp(value, 0, 10); }

    public int getMiningCylinderRadius() { return miningCylinderRadius; }
    public void setMiningCylinderRadius(int value) { this.miningCylinderRadius = clamp(value, 1, 16); }

    public int getMiningCylinderForwardShift() { return miningCylinderForwardShift; }
    public void setMiningCylinderForwardShift(int value) { this.miningCylinderForwardShift = clamp(value, 0, 10); }

    public boolean isMobCullingEnabled() { return mobCullingEnabled; }
    public void setMobCullingEnabled(boolean value) { this.mobCullingEnabled = value; }

    public boolean isMobTranslucencyEnabled() { return mobTranslucencyEnabled; }
    public void setMobTranslucencyEnabled(boolean value) { this.mobTranslucencyEnabled = value; }

    public double getMobTranslucencyAlpha() { return mobTranslucencyAlpha; }
    public void setMobTranslucencyAlpha(double value) { this.mobTranslucencyAlpha = clamp(value, 0.0, 1.0); }

    public boolean isTrapdoorTranslucencyEnabled() { return trapdoorTranslucencyEnabled; }
    public void setTrapdoorTranslucencyEnabled(boolean value) { this.trapdoorTranslucencyEnabled = value; }

    public double getTrapdoorTransparency() { return trapdoorTransparency; }
    public void setTrapdoorTransparency(double value) { this.trapdoorTransparency = clamp(value, 0.0, 1.0); }

    public boolean isFadeEnabled() { return fadeEnabled; }
    public void setFadeEnabled(boolean value) { this.fadeEnabled = value; }

    public double getFadeBlockHitThreshold() { return fadeBlockHitThreshold; }
    public void setFadeBlockHitThreshold(double value) { this.fadeBlockHitThreshold = clamp(value, 0.0, 1.0); }

    public double getFadeStart() { return fadeStart; }
    public void setFadeStart(double value) { this.fadeStart = clamp(value, 0.0, 0.9); }

    public double getFadeNearAlpha() { return fadeNearAlpha; }
    public void setFadeNearAlpha(double value) { this.fadeNearAlpha = clamp(value, 0.0, 1.0); }

    public double getFadeSmoothingHalfLife() { return fadeSmoothingHalfLife; }
    public void setFadeSmoothingHalfLife(double value) { this.fadeSmoothingHalfLife = clamp(value, 0.0, 1.0); }

    public boolean isDisableFadeIndoors() { return disableFadeIndoors; }
    public void setDisableFadeIndoors(boolean value) { this.disableFadeIndoors = value; }

    public boolean isPlayerNearTranslucencyEnabled() { return playerNearTranslucencyEnabled; }
    public void setPlayerNearTranslucencyEnabled(boolean value) { this.playerNearTranslucencyEnabled = value; }

    public double getPlayerNearTranslucencyAlpha() { return playerNearTranslucencyAlpha; }
    public void setPlayerNearTranslucencyAlpha(double value) { this.playerNearTranslucencyAlpha = clamp(value, 0.0, 1.0); }

    public int getPlayerNearTranslucencyRangeHorizontal() { return playerNearTranslucencyRangeHorizontal; }
    public void setPlayerNearTranslucencyRangeHorizontal(int value) { this.playerNearTranslucencyRangeHorizontal = clamp(value, 1, 5); }

    public int getPlayerNearTranslucencyRangeVertical() { return playerNearTranslucencyRangeVertical; }
    public void setPlayerNearTranslucencyRangeVertical(int value) { this.playerNearTranslucencyRangeVertical = clamp(value, 1, 5); }

    public boolean isPlayerNearTranslucencyHittable() { return playerNearTranslucencyHittable; }
    public void setPlayerNearTranslucencyHittable(boolean value) { this.playerNearTranslucencyHittable = value; }

    public boolean isMobConeCullingEnabled() { return mobConeCullingEnabled; }
    public void setMobConeCullingEnabled(boolean value) { this.mobConeCullingEnabled = value; }

    public double getMobConeHalfAngle() { return mobConeHalfAngle; }
    public void setMobConeHalfAngle(double value) { this.mobConeHalfAngle = clamp(value, 10.0, 90.0); }

    public double getMobConeFadeAngle() { return mobConeFadeAngle; }
    public void setMobConeFadeAngle(double value) { this.mobConeFadeAngle = clamp(value, 0.0, 90.0); }

    public double getMobNearRadius() { return mobNearRadius; }
    public void setMobNearRadius(double value) { this.mobNearRadius = clamp(value, 0.0, 20.0); }

    public double getMobFogEnd() { return mobFogEnd; }
    public void setMobFogEnd(double value) { this.mobFogEnd = clamp(value, 1.0, 50.0); }

    public boolean isStaircaseExclusionEnabled() { return staircaseExclusionEnabled; }
    public void setStaircaseExclusionEnabled(boolean value) { this.staircaseExclusionEnabled = value; }

    public int getStaircaseExclusionHeight() { return staircaseExclusionHeight; }
    public void setStaircaseExclusionHeight(int value) { this.staircaseExclusionHeight = clamp(value, 1, 10); }

    public boolean isStaircaseOccludeEnabled() { return staircaseOccludeEnabled; }
    public void setStaircaseOccludeEnabled(boolean value) { this.staircaseOccludeEnabled = value; }

    public double getStaircaseOccludeAlpha() { return staircaseOccludeAlpha; }
    public void setStaircaseOccludeAlpha(double value) { this.staircaseOccludeAlpha = clamp(value, 0.0, 1.0); }

    public boolean isLadderOccludeEnabled() { return ladderOccludeEnabled; }
    public void setLadderOccludeEnabled(boolean value) { this.ladderOccludeEnabled = value; }

    public double getLadderOccludeAlpha() { return ladderOccludeAlpha; }
    public void setLadderOccludeAlpha(double value) { this.ladderOccludeAlpha = clamp(value, 0.0, 1.0); }

    public boolean isTreeOccludeEnabled() { return treeOccludeEnabled; }
    public void setTreeOccludeEnabled(boolean value) { this.treeOccludeEnabled = value; }

    public double getTreeOccludeAlpha() { return treeOccludeAlpha; }
    public void setTreeOccludeAlpha(double value) { this.treeOccludeAlpha = clamp(value, 0.0, 1.0); }

    public int getViewWedgeHalfAngle() { return viewWedgeHalfAngle; }
    public void setViewWedgeHalfAngle(int value) { this.viewWedgeHalfAngle = clamp(value, 10, 90); }

    public int getCoverCullingRadius() { return coverCullingRadius; }
    public void setCoverCullingRadius(int value) { this.coverCullingRadius = clamp(value, 4, 24); }

    public boolean isCoverCullingViewshedEnabled() { return coverCullingViewshedEnabled; }
    public void setCoverCullingViewshedEnabled(boolean value) { this.coverCullingViewshedEnabled = value; }

    public int getCullingMode() { return cullingMode; }
    public void setCullingMode(int value) { this.cullingMode = clamp(value, CULLING_MODE_CYLINDER, CULLING_MODE_COVER_CORRIDOR); }

    public boolean isIndoorCeilingCullingEnabled() { return indoorCeilingCullingEnabled; }
    public void setIndoorCeilingCullingEnabled(boolean value) { this.indoorCeilingCullingEnabled = value; }

    public boolean isIgnoreLeavesInRaycast() { return ignoreLeavesInRaycast; }
    public void setIgnoreLeavesInRaycast(boolean value) { this.ignoreLeavesInRaycast = value; }

    public boolean isProtectNaturalTreeLogs() { return protectNaturalTreeLogs; }
    public void setProtectNaturalTreeLogs(boolean value) { this.protectNaturalTreeLogs = value; }

    public boolean isTranslucentFluid() { return translucentFluid; }
    public void setTranslucentFluid(boolean value) { this.translucentFluid = value; }

    public double getFluidAlpha() { return fluidAlpha; }
    public void setFluidAlpha(double value) { this.fluidAlpha = clamp(value, 0.05, 1.0); }

    private boolean undergroundCullingEnabled = true;
    private int undergroundCullingStartDistance = 4;
    private int undergroundCullingKeepDepth = 16;

    public boolean isUndergroundCullingEnabled() { return undergroundCullingEnabled; }
    public void setUndergroundCullingEnabled(boolean value) { this.undergroundCullingEnabled = value; }

    public int getUndergroundCullingStartDistance() { return undergroundCullingStartDistance; }
    public void setUndergroundCullingStartDistance(int value) { this.undergroundCullingStartDistance = clamp(value, 1, 16); }

    public int getUndergroundCullingKeepDepth() { return undergroundCullingKeepDepth; }
    public void setUndergroundCullingKeepDepth(int value) { this.undergroundCullingKeepDepth = clamp(value, 4, 64); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
