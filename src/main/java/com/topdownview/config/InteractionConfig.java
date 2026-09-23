package com.topdownview.config;

import com.topdownview.util.MathUtil;

/**
 * プレイヤー操作、移動、インタラクション、連携機能に関する設定項目を保持する構成クラス。
 */
public final class InteractionConfig {

    private boolean miningModeEnabled = false;
    private boolean clickToMoveEnabled = false;
    private boolean stepAssistEnabled = true;
    private double sprintDistanceThreshold = 5.0;
    private boolean autoAlignToMovementEnabled = false;
    private int autoAlignAngleThreshold = 45;
    private int autoAlignCooldownTicks = 30;
    private int stableDirectionAngle = 15;
    private int stableDirectionTicks = 20;
    private double autoAlignAnimationSpeed = 0.1;
    private boolean autoAlignAnimationAcceleration = false;
    private boolean rangeIndicatorEnabled = false;
    private boolean destinationHighlightEnabled = true;
    private double rangeEmptyHand = 3.0;
    private double rangeSword = 3.0;
    private double rangeAxe = 3.0;
    private double rangePickaxe = 3.0;
    private double rangeShovel = 3.0;
    private double rangeOther = 3.0;
    private boolean defaultEnabled = true;
    private boolean targetGlowEnabled = true;
    private boolean targetLockEnabled = true;
    private int targetLockDuration = 120;
    private double targetHitboxExpansion = 1.0;
    private boolean screenReachEnabled = false;
    private double reachDistance = 10.0;
    private double serverReachDistance = 10.0;
    private double syncedServerReach = -1.0;
    private double manualPickupDistance = 4.0;
    private double syncedManualPickupDistance = -1.0;
    private int signHoverDisplayMode = 2;
    private double signHoverScale = 0.5;
    private int droppedItemLabelMode = 1;
    private double droppedItemLabelScale = 1.0;
    private int droppedItemLabelBackground = 0;
    private int droppedItemLabelGap = 1;
    private boolean manualItemPickup = false;
    private boolean showInteractionPrompt = false;
    private double interactionPromptScale = 0.8;
    private boolean interactionPromptShadow = false;
    private boolean showSpatialPrompt = false;
    private double spatialPromptRadius = 8.0;
    private boolean spatialPromptAllBlocks = false;
    private boolean performanceMonitorEnabled = false;

    public boolean isMiningModeEnabled() { return miningModeEnabled; }
    public void setMiningModeEnabled(boolean value) { this.miningModeEnabled = value; }

    public boolean isClickToMoveEnabled() { return clickToMoveEnabled; }
    public void setClickToMoveEnabled(boolean value) { this.clickToMoveEnabled = value; }

    public boolean isStepAssistEnabled() { return stepAssistEnabled; }
    public void setStepAssistEnabled(boolean value) { this.stepAssistEnabled = value; }

    public double getSprintDistanceThreshold() { return sprintDistanceThreshold; }
    public void setSprintDistanceThreshold(double value) { this.sprintDistanceThreshold = MathUtil.clamp(value, 1.0, 50.0); }

    public boolean isAutoAlignToMovementEnabled() { return autoAlignToMovementEnabled; }
    public void setAutoAlignToMovementEnabled(boolean value) { this.autoAlignToMovementEnabled = value; }

    public int getAutoAlignAngleThreshold() { return autoAlignAngleThreshold; }
    public void setAutoAlignAngleThreshold(int value) { this.autoAlignAngleThreshold = MathUtil.clamp(value, 0, 90); }

    public int getAutoAlignCooldownTicks() { return autoAlignCooldownTicks; }
    public void setAutoAlignCooldownTicks(int value) { this.autoAlignCooldownTicks = MathUtil.clamp(value, 0, 100); }

    public int getStableDirectionAngle() { return stableDirectionAngle; }
    public void setStableDirectionAngle(int value) { this.stableDirectionAngle = MathUtil.clamp(value, 5, 60); }

    public int getStableDirectionTicks() { return stableDirectionTicks; }
    public void setStableDirectionTicks(int value) { this.stableDirectionTicks = MathUtil.clamp(value, 5, 60); }

    public double getAutoAlignAnimationSpeed() { return autoAlignAnimationSpeed; }
    public void setAutoAlignAnimationSpeed(double value) { this.autoAlignAnimationSpeed = MathUtil.clamp(value, 0.01, 0.19); }

    public boolean isAutoAlignAnimationAcceleration() { return autoAlignAnimationAcceleration; }
    public void setAutoAlignAnimationAcceleration(boolean value) { this.autoAlignAnimationAcceleration = value; }

    public boolean isRangeIndicatorEnabled() { return rangeIndicatorEnabled; }
    public void setRangeIndicatorEnabled(boolean value) { this.rangeIndicatorEnabled = value; }

    public boolean isDestinationHighlightEnabled() { return destinationHighlightEnabled; }
    public void setDestinationHighlightEnabled(boolean value) { this.destinationHighlightEnabled = value; }

    public double getRangeEmptyHand() { return rangeEmptyHand; }
    public void setRangeEmptyHand(double value) { this.rangeEmptyHand = MathUtil.clamp(value, 1.0, 10.0); }

    public double getRangeSword() { return rangeSword; }
    public void setRangeSword(double value) { this.rangeSword = MathUtil.clamp(value, 1.0, 10.0); }

    public double getRangeAxe() { return rangeAxe; }
    public void setRangeAxe(double value) { this.rangeAxe = MathUtil.clamp(value, 1.0, 10.0); }

    public double getRangePickaxe() { return rangePickaxe; }
    public void setRangePickaxe(double value) { this.rangePickaxe = MathUtil.clamp(value, 1.0, 10.0); }

    public double getRangeShovel() { return rangeShovel; }
    public void setRangeShovel(double value) { this.rangeShovel = MathUtil.clamp(value, 1.0, 10.0); }

    public double getRangeOther() { return rangeOther; }
    public void setRangeOther(double value) { this.rangeOther = MathUtil.clamp(value, 1.0, 10.0); }

    public boolean isDefaultEnabled() { return defaultEnabled; }
    public void setDefaultEnabled(boolean value) { this.defaultEnabled = value; }

    public boolean isTargetGlowEnabled() { return targetGlowEnabled; }
    public void setTargetGlowEnabled(boolean value) { this.targetGlowEnabled = value; }

    public boolean isTargetLockEnabled() { return targetLockEnabled; }
    public void setTargetLockEnabled(boolean value) { this.targetLockEnabled = value; }

    public int getTargetLockDuration() { return targetLockDuration; }
    public void setTargetLockDuration(int value) { this.targetLockDuration = MathUtil.clamp(value, 0, 600); }

    public double getTargetHitboxExpansion() { return targetHitboxExpansion; }
    public void setTargetHitboxExpansion(double value) { this.targetHitboxExpansion = MathUtil.clamp(value, 0.0, 5.0); }

    public boolean isScreenReachEnabled() { return screenReachEnabled; }
    public void setScreenReachEnabled(boolean value) { this.screenReachEnabled = value; }

    public double getReachDistance() { return reachDistance; }
    public void setReachDistance(double value) { this.reachDistance = MathUtil.clamp(value, 1.0, 100.0); }

    public double getServerReachDistance() { return serverReachDistance; }
    public void setServerReachDistance(double value) { this.serverReachDistance = MathUtil.clamp(value, 1.0, 100.0); }

    public double getEffectiveReachDistance() {
        return syncedServerReach >= 0 ? syncedServerReach : reachDistance;
    }
    public void setSyncedServerReach(double value) { syncedServerReach = value; }
    public void clearSyncedServerReach() { syncedServerReach = -1.0; }
    public boolean hasSyncedServerReach() { return syncedServerReach >= 0; }

    public double getManualPickupDistance() { return manualPickupDistance; }
    public void setManualPickupDistance(double value) { this.manualPickupDistance = MathUtil.clamp(value, 1.0, 10.0); }

    /** 手動取得の有効距離。サーバー同期値があればそれを優先する。 */
    public double getEffectiveManualPickupDistance() {
        return syncedManualPickupDistance >= 0 ? syncedManualPickupDistance : manualPickupDistance;
    }
    public void setSyncedManualPickupDistance(double value) { syncedManualPickupDistance = value; }
    public void clearSyncedManualPickupDistance() { syncedManualPickupDistance = -1.0; }
    public boolean hasSyncedManualPickupDistance() { return syncedManualPickupDistance >= 0; }

    public int getSignHoverDisplayMode() { return signHoverDisplayMode; }
    public void setSignHoverDisplayMode(int value) { this.signHoverDisplayMode = MathUtil.clamp(value, 0, 2); }

    public double getSignHoverScale() { return signHoverScale; }
    public void setSignHoverScale(double value) { this.signHoverScale = MathUtil.clamp(value, 0.0, 1.0); }

    public int getDroppedItemLabelMode() { return droppedItemLabelMode; }
    public void setDroppedItemLabelMode(int value) { this.droppedItemLabelMode = MathUtil.clamp(value, 0, 1); }

    public double getDroppedItemLabelScale() { return droppedItemLabelScale; }
    public void setDroppedItemLabelScale(double value) { this.droppedItemLabelScale = MathUtil.clamp(value, 0.0, 1.0); }

    public int getDroppedItemLabelBackground() { return droppedItemLabelBackground; }
    public void setDroppedItemLabelBackground(int value) { this.droppedItemLabelBackground = MathUtil.clamp(value, 0, 1); }

    public int getDroppedItemLabelGap() { return droppedItemLabelGap; }
    public void setDroppedItemLabelGap(int value) { this.droppedItemLabelGap = MathUtil.clamp(value, 0, 5); }

    public boolean isManualItemPickup() { return manualItemPickup; }
    public void setManualItemPickup(boolean value) { this.manualItemPickup = value; }

    public boolean isShowInteractionPrompt() { return showInteractionPrompt; }
    public void setShowInteractionPrompt(boolean value) { this.showInteractionPrompt = value; }

    public double getInteractionPromptScale() { return interactionPromptScale; }
    public void setInteractionPromptScale(double value) { this.interactionPromptScale = MathUtil.clamp(value, 0.0, 1.0); }

    public boolean isInteractionPromptShadow() { return interactionPromptShadow; }
    public void setInteractionPromptShadow(boolean value) { this.interactionPromptShadow = value; }

    public boolean isShowSpatialPrompt() { return showSpatialPrompt; }
    public void setShowSpatialPrompt(boolean value) { this.showSpatialPrompt = value; }

    public double getSpatialPromptRadius() { return spatialPromptRadius; }
    public void setSpatialPromptRadius(double value) { this.spatialPromptRadius = MathUtil.clamp(value, 1.0, 16.0); }

    public boolean isSpatialPromptAllBlocks() { return spatialPromptAllBlocks; }
    public void setSpatialPromptAllBlocks(boolean value) { this.spatialPromptAllBlocks = value; }

    public boolean isPerformanceMonitorEnabled() { return performanceMonitorEnabled; }
    public void setPerformanceMonitorEnabled(boolean value) { this.performanceMonitorEnabled = value; }

}
