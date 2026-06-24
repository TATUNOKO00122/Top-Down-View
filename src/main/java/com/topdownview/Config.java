package com.topdownview;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final List<Runnable> configChangeListeners = new CopyOnWriteArrayList<>();

    public static void registerConfigChangeListener(Runnable listener) {
        configChangeListeners.add(listener);
    }

    private static void notifyConfigChanged() {
        for (Runnable listener : configChangeListeners) {
            listener.run();
        }
    }

    private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_HORIZONTAL = BUILDER
            .defineInRange("cylinderRadiusHorizontal", 5, 1, 10);
    private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_VERTICAL = BUILDER
            .defineInRange("cylinderRadiusVertical", 5, 1, 10);
    private static final ForgeConfigSpec.IntValue CYLINDER_FORWARD_SHIFT = BUILDER
            .defineInRange("cylinderForwardShift", 1, 0, 10);
    private static final ForgeConfigSpec.IntValue MINING_CYLINDER_RADIUS = BUILDER
            .defineInRange("miningCylinderRadius", 5, 1, 16);
    private static final ForgeConfigSpec.IntValue MINING_CYLINDER_FORWARD_SHIFT = BUILDER
            .defineInRange("miningCylinderForwardShift", 0, 0, 10);
    private static final ForgeConfigSpec.BooleanValue MINING_MODE_ENABLED = BUILDER
            .define("miningModeEnabled", false);
    private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
            .define("clickToMoveEnabled", false);
    private static final ForgeConfigSpec.DoubleValue ARRIVAL_THRESHOLD = BUILDER
            .defineInRange("arrivalThreshold", 1.5, 0.5, 5.0);
    private static final ForgeConfigSpec.BooleanValue FORCE_AUTO_JUMP = BUILDER
            .define("forceAutoJump", true);
    private static final ForgeConfigSpec.DoubleValue SPRINT_DISTANCE_THRESHOLD = BUILDER
            .defineInRange("sprintDistanceThreshold", 5.0, 1.0, 50.0);
    private static final ForgeConfigSpec.BooleanValue AUTO_ALIGN_TO_MOVEMENT_ENABLED = BUILDER
            .define("autoAlignToMovementEnabled", false);
    private static final ForgeConfigSpec.IntValue AUTO_ALIGN_ANGLE_THRESHOLD = BUILDER
            .defineInRange("autoAlignAngleThreshold", 45, 0, 90);
    private static final ForgeConfigSpec.IntValue AUTO_ALIGN_COOLDOWN_TICKS = BUILDER
            .defineInRange("autoAlignCooldownTicks", 30, 0, 100);
    private static final ForgeConfigSpec.IntValue STABLE_DIRECTION_ANGLE = BUILDER
            .defineInRange("stableDirectionAngle", 15, 5, 60);
    private static final ForgeConfigSpec.IntValue STABLE_DIRECTION_TICKS = BUILDER
            .defineInRange("stableDirectionTicks", 20, 5, 60);
    private static final ForgeConfigSpec.DoubleValue AUTO_ALIGN_ANIMATION_SPEED = BUILDER
            .defineInRange("autoAlignAnimationSpeed", 0.1, 0.05, 0.5);
    private static final ForgeConfigSpec.BooleanValue MOB_CULLING_ENABLED = BUILDER
            .define("mobCullingEnabled", false);
    private static final ForgeConfigSpec.BooleanValue TRAPDOOR_TRANSLUCENCY_ENABLED = BUILDER
            .define("trapdoorTranslucencyEnabled", false);
    private static final ForgeConfigSpec.DoubleValue TRAPDOOR_TRANSPARENCY = BUILDER
            .defineInRange("trapdoorTransparency", 0.3, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue FADE_ENABLED = BUILDER
            .define("fadeEnabled", true);
    private static final ForgeConfigSpec.DoubleValue FADE_BLOCK_HIT_THRESHOLD = BUILDER
            .defineInRange("fadeBlockHitThreshold", 0.5, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue FADE_START = BUILDER
            .defineInRange("fadeStart", 0.7, 0.0, 0.9);
    private static final ForgeConfigSpec.DoubleValue FADE_NEAR_ALPHA = BUILDER
            .defineInRange("fadeNearAlpha", 0.0, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue RANGE_INDICATOR_ENABLED = BUILDER
            .define("rangeIndicatorEnabled", false);
    private static final ForgeConfigSpec.BooleanValue DESTINATION_HIGHLIGHT_ENABLED = BUILDER
            .define("destinationHighlightEnabled", true);
    private static final ForgeConfigSpec.DoubleValue RANGE_EMPTY_HAND = BUILDER
            .defineInRange("rangeEmptyHand", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue RANGE_SWORD = BUILDER
            .defineInRange("rangeSword", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue RANGE_AXE = BUILDER
            .defineInRange("rangeAxe", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue RANGE_PICKAXE = BUILDER
            .defineInRange("rangePickaxe", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue RANGE_SHOVEL = BUILDER
            .defineInRange("rangeShovel", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue RANGE_OTHER = BUILDER
            .defineInRange("rangeOther", 3.0, 1.0, 10.0);
    private static final ForgeConfigSpec.BooleanValue DEFAULT_ENABLED = BUILDER
            .define("defaultEnabled", true);
    private static final ForgeConfigSpec.BooleanValue TARGET_GLOW_ENABLED = BUILDER
            .define("targetGlowEnabled", true);
    private static final ForgeConfigSpec.IntValue ROTATE_ANGLE_MODE = BUILDER
            .defineInRange("rotateAngleMode", 1, 0, 2);
    private static final ForgeConfigSpec.DoubleValue CAMERA_SNAP_ROTATION_SPEED = BUILDER
            .defineInRange("cameraSnapRotationSpeed", 0.2, 0.05, 0.5);
    private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
            .defineInRange("cameraPitch", 40.0, 10.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MINING_MODE_PITCH = BUILDER
            .defineInRange("miningModePitch", 45.0, 10.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MAX_CAMERA_DISTANCE = BUILDER
            .defineInRange("maxCameraDistance", 50.0, 0.0, 200.0);
    private static final ForgeConfigSpec.DoubleValue DEFAULT_CAMERA_DISTANCE = BUILDER
            .defineInRange("defaultCameraDistance", 30.0, 0.0, 100.0);
    private static final ForgeConfigSpec.BooleanValue DRAG_ROTATION_ENABLED = BUILDER
            .define("dragRotationEnabled", true);
    private static final ForgeConfigSpec.BooleanValue CAMERA_Y_FOLLOW_DELAY_ENABLED = BUILDER
            .define("cameraYFollowDelayEnabled", true);
    private static final ForgeConfigSpec.DoubleValue CAMERA_Y_FOLLOW_DELAY = BUILDER
            .defineInRange("cameraYFollowDelay", 1.0, 0.0, 4.0);
    private static final ForgeConfigSpec.BooleanValue CAMERA_X_FOLLOW_DELAY_ENABLED = BUILDER
            .define("cameraXFollowDelayEnabled", false);
    private static final ForgeConfigSpec.DoubleValue CAMERA_X_FOLLOW_DELAY = BUILDER
            .defineInRange("cameraXFollowDelay", 1.0, 0.0, 4.0);
    private static final ForgeConfigSpec.BooleanValue CAMERA_Z_FOLLOW_DELAY_ENABLED = BUILDER
            .define("cameraZFollowDelayEnabled", false);
    private static final ForgeConfigSpec.DoubleValue CAMERA_Z_FOLLOW_DELAY = BUILDER
            .defineInRange("cameraZFollowDelay", 1.0, 0.0, 4.0);
    private static final ForgeConfigSpec.BooleanValue FOLLOW_DELAY_WHILE_MOUNTED = BUILDER
            .define("followDelayWhileMounted", false);
    private static final ForgeConfigSpec.DoubleValue PLAYER_SCREEN_OFFSET = BUILDER
            .defineInRange("playerScreenOffset", 0.5, -10.0, 10.0);
    private static final ForgeConfigSpec.BooleanValue HEAD_BODY_ROTATION_ENABLED = BUILDER
            .define("headBodyRotationEnabled", true);
    private static final ForgeConfigSpec.BooleanValue INDEPENDENT_MOUNT_AIM = BUILDER
            .define("independentMountAim", true);
    private static final ForgeConfigSpec.IntValue MOUNT_AIM_MAX_TWIST = BUILDER
            .defineInRange("mountAimMaxTwist", 90, 45, 360);
    private static final ForgeConfigSpec.DoubleValue MOUNT_TURN_SMOOTHING = BUILDER
            .defineInRange("mountTurnSmoothing", 0.25, 0.05, 1.0);
    private static final ForgeConfigSpec.IntValue BOAT_HEAD_MAX_TWIST = BUILDER
            .defineInRange("boatHeadMaxTwist", 60, 30, 180);
    private static final ForgeConfigSpec.IntValue BOAT_BODY_MAX_TWIST = BUILDER
            .defineInRange("boatBodyMaxTwist", 45, 15, 90);
    private static final ForgeConfigSpec.IntValue TOP_DOWN_FOV = BUILDER
            .defineInRange("topDownFov", 30, 30, 110);
    private static final ForgeConfigSpec.BooleanValue LOCKED_TOP_DOWN = BUILDER
            .comment("Locks the camera to top-down view. Prevents switching back to first-person via F5 or toggle key.", "This setting is not available in the in-game GUI. Edit the config file directly to change it.")
            .define("lockedTopDown", false);
    private static final ForgeConfigSpec.BooleanValue SCROLL_ONLY_ZOOM_ENABLED = BUILDER
            .define("scrollOnlyZoomEnabled", false);
    private static final ForgeConfigSpec.BooleanValue TARGET_LOCK_ENABLED = BUILDER
            .define("targetLockEnabled", true);
    private static final ForgeConfigSpec.IntValue TARGET_LOCK_DURATION = BUILDER
            .defineInRange("targetLockDuration", 120, 0, 600);
    private static final ForgeConfigSpec.DoubleValue TARGET_HITBOX_EXPANSION = BUILDER
            .defineInRange("targetHitboxExpansion", 1.0, 0.0, 5.0);
    private static final ForgeConfigSpec.BooleanValue SCREEN_REACH_ENABLED = BUILDER
            .define("screenReachEnabled", false);
    private static final ForgeConfigSpec.DoubleValue REACH_DISTANCE = BUILDER
            .defineInRange("reachDistance", 10.0, 1.0, 100.0);

    // PlacementPreview 設定
    private static final ForgeConfigSpec.BooleanValue PLACEMENT_PREVIEW_ENABLED = BUILDER
            .define("placementPreviewEnabled", true);
    private static final ForgeConfigSpec.DoubleValue PLACEMENT_TRANSPARENCY = BUILDER
            .defineInRange("placementTransparency", 0.5, 0.1, 0.9);
    private static final ForgeConfigSpec.BooleanValue CLICK_POSITION_PLACEMENT_ENABLED = BUILDER
            .define("clickPositionPlacementEnabled", true);

    // 階段カリング除外設定
    private static final ForgeConfigSpec.BooleanValue STAIRCASE_EXCLUSION_ENABLED = BUILDER
            .comment("Excludes staircase blocks from culling. (May impact performance / 動作が少し重くなります)")
            .define("staircaseExclusionEnabled", false);
    private static final ForgeConfigSpec.IntValue STAIRCASE_EXCLUSION_HEIGHT = BUILDER
            .defineInRange("staircaseExclusionHeight", 2, 1, 10);

    // 階段視線遮蔽時の透明度設定
    private static final ForgeConfigSpec.BooleanValue STAIRCASE_OCCLUDE_ENABLED = BUILDER
            .define("staircaseOccludeEnabled", false);
    private static final ForgeConfigSpec.DoubleValue STAIRCASE_OCCLUDE_ALPHA = BUILDER
            .defineInRange("staircaseOccludeAlpha", 0.0, 0.0, 1.0);

    // ブロック配置方向手動指定
    private static final ForgeConfigSpec.BooleanValue PLACEMENT_ROTATION_ENABLED = BUILDER
            .define("placementRotationEnabled", false);

    private static final ForgeConfigSpec.IntValue SIGN_HOVER_DISPLAY_MODE = BUILDER
            .defineInRange("signHoverDisplayMode", 2, 0, 2);

    private static final ForgeConfigSpec.DoubleValue SIGN_HOVER_SCALE = BUILDER
            .defineInRange("signHoverScale", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue SHOW_INTERACTION_PROMPT = BUILDER
            .define("showInteractionPrompt", false);

    private static final ForgeConfigSpec.DoubleValue INTERACTION_PROMPT_SCALE = BUILDER
            .defineInRange("interactionPromptScale", 0.8, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue INTERACTION_PROMPT_SHADOW = BUILDER
            .define("interactionPromptShadow", false);

    private static final ForgeConfigSpec.BooleanValue SHOW_SPATIAL_PROMPT = BUILDER
            .define("showSpatialPrompt", false);

    private static final ForgeConfigSpec.DoubleValue SPATIAL_PROMPT_RADIUS = BUILDER
            .defineInRange("spatialPromptRadius", 8.0, 1.0, 16.0);

    private static final ForgeConfigSpec.BooleanValue SPATIAL_PROMPT_ALL_BLOCKS = BUILDER
            .define("spatialPromptAllBlocks", false);

    private static final ForgeConfigSpec.BooleanValue IGNORE_LEAVES_IN_RAYCAST = BUILDER
            .define("ignoreLeavesInRaycast", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue SERVER_REACH_DISTANCE = COMMON_BUILDER
            .defineInRange("serverReachDistance", 10.0, 1.0, 100.0);
    public static final ForgeConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private static double syncedServerReach = -1.0;

    private static int cylinderRadiusHorizontal;
    private static int cylinderRadiusVertical;
    private static int cylinderForwardShift;
    private static int miningCylinderRadius;
    private static int miningCylinderForwardShift;
    private static boolean miningModeEnabled;
    private static boolean clickToMoveEnabled;
    private static double arrivalThreshold;
    private static boolean forceAutoJump;
    private static double sprintDistanceThreshold;
    private static boolean autoAlignToMovementEnabled;
    private static int autoAlignAngleThreshold;
    private static int autoAlignCooldownTicks;
    private static int stableDirectionAngle;
    private static int stableDirectionTicks;
    private static double autoAlignAnimationSpeed;
    private static boolean mobCullingEnabled;
    private static boolean trapdoorTranslucencyEnabled;
    private static double trapdoorTransparency;
    private static boolean fadeEnabled;
    private static double fadeBlockHitThreshold;
    private static double fadeStart;
    private static double fadeNearAlpha;
    private static boolean rangeIndicatorEnabled;
    private static boolean destinationHighlightEnabled;
    private static double rangeEmptyHand;
    private static double rangeSword;
    private static double rangeAxe;
    private static double rangePickaxe;
    private static double rangeShovel;
    private static double rangeOther;
    private static boolean defaultEnabled;
    private static boolean targetGlowEnabled;
    private static int rotateAngleMode;
    private static double cameraSnapRotationSpeed;
    private static double cameraPitch;
    private static double miningModePitch;
    private static double maxCameraDistance;
    private static double defaultCameraDistance;
    private static boolean dragRotationEnabled;
    private static boolean cameraYFollowDelayEnabled;
    private static double cameraYFollowDelay;
    private static boolean cameraXFollowDelayEnabled;
    private static double cameraXFollowDelay;
    private static boolean cameraZFollowDelayEnabled;
    private static double cameraZFollowDelay;
    private static boolean followDelayWhileMounted;
    private static double playerScreenOffset;
    private static boolean headBodyRotationEnabled;
    private static boolean independentMountAim;
    private static int mountAimMaxTwist;
    private static double mountTurnSmoothing;
    private static int boatHeadMaxTwist;
    private static int boatBodyMaxTwist;
    private static int topDownFov;
    private static boolean lockedTopDown;
    private static boolean scrollOnlyZoomEnabled;
    private static boolean targetLockEnabled;
    private static int targetLockDuration;
    private static double targetHitboxExpansion;
    private static boolean screenReachEnabled;
    private static double reachDistance;
    private static double serverReachDistance;
    private static boolean placementPreviewEnabled;
    private static double placementTransparency;
    private static boolean clickPositionPlacementEnabled;
    private static boolean staircaseExclusionEnabled;
    private static int staircaseExclusionHeight;
    private static boolean staircaseOccludeEnabled;
    private static double staircaseOccludeAlpha;
    private static boolean placementRotationEnabled;
    private static boolean ignoreLeavesInRaycast;
    private static int signHoverDisplayMode;
    private static double signHoverScale;
    private static boolean showInteractionPrompt;
    private static double interactionPromptScale;
    private static boolean interactionPromptShadow;
    private static boolean showSpatialPrompt;
    private static double spatialPromptRadius;
    private static boolean spatialPromptAllBlocks;

    public static int getCylinderRadiusHorizontal() { return cylinderRadiusHorizontal; }
    public static int getCylinderRadiusVertical() { return cylinderRadiusVertical; }
    public static int getCylinderForwardShift() { return cylinderForwardShift; }
    public static int getMiningCylinderRadius() { return miningCylinderRadius; }
    public static int getMiningCylinderForwardShift() { return miningCylinderForwardShift; }
    public static boolean isMiningModeEnabled() { return miningModeEnabled; }
    public static boolean isClickToMoveEnabled() { return clickToMoveEnabled; }
    public static double getArrivalThreshold() { return arrivalThreshold; }
    public static boolean isForceAutoJump() { return forceAutoJump; }
    public static double getSprintDistanceThreshold() { return sprintDistanceThreshold; }
    public static boolean isAutoAlignToMovementEnabled() { return autoAlignToMovementEnabled; }
    public static int getAutoAlignAngleThreshold() { return autoAlignAngleThreshold; }
    public static int getAutoAlignCooldownTicks() { return autoAlignCooldownTicks; }
    public static int getStableDirectionAngle() { return stableDirectionAngle; }
    public static int getStableDirectionTicks() { return stableDirectionTicks; }
    public static double getAutoAlignAnimationSpeed() { return autoAlignAnimationSpeed; }
    public static boolean isMobCullingEnabled() { return mobCullingEnabled; }
    public static boolean isTrapdoorTranslucencyEnabled() { return trapdoorTranslucencyEnabled; }
    public static double getTrapdoorTransparency() { return trapdoorTransparency; }
    public static boolean isFadeEnabled() { return fadeEnabled; }
    public static double getFadeBlockHitThreshold() { return fadeBlockHitThreshold; }
    public static double getFadeStart() { return fadeStart; }
    public static double getFadeNearAlpha() { return fadeNearAlpha; }
    public static boolean isRangeIndicatorEnabled() { return rangeIndicatorEnabled; }
    public static boolean isDestinationHighlightEnabled() { return destinationHighlightEnabled; }
    public static double getRangeEmptyHand() { return rangeEmptyHand; }
    public static double getRangeSword() { return rangeSword; }
    public static double getRangeAxe() { return rangeAxe; }
    public static double getRangePickaxe() { return rangePickaxe; }
    public static double getRangeShovel() { return rangeShovel; }
    public static double getRangeOther() { return rangeOther; }
    public static boolean isDefaultEnabled() { return defaultEnabled; }
    public static boolean isTargetGlowEnabled() { return targetGlowEnabled; }
    public static int getRotateAngleMode() { return rotateAngleMode; }
    public static double getCameraSnapRotationSpeed() { return cameraSnapRotationSpeed; }
    public static double getCameraPitch() { return cameraPitch; }
    public static double getMiningModePitch() { return miningModePitch; }
    public static double getMaxCameraDistance() { return maxCameraDistance; }
    public static double getDefaultCameraDistance() { return defaultCameraDistance; }
    public static boolean isDragRotationEnabled() { return dragRotationEnabled; }
    public static boolean isCameraYFollowDelayEnabled() { return cameraYFollowDelayEnabled; }
    public static double getCameraYFollowDelay() { return cameraYFollowDelay; }
    public static boolean isCameraXFollowDelayEnabled() { return cameraXFollowDelayEnabled; }
    public static double getCameraXFollowDelay() { return cameraXFollowDelay; }
    public static boolean isCameraZFollowDelayEnabled() { return cameraZFollowDelayEnabled; }
    public static double getCameraZFollowDelay() { return cameraZFollowDelay; }
    public static boolean isFollowDelayWhileMounted() { return followDelayWhileMounted; }
    public static double getPlayerScreenOffset() { return playerScreenOffset; }
    public static boolean isHeadBodyRotationEnabled() { return headBodyRotationEnabled; }
    public static boolean isIndependentMountAim() { return independentMountAim; }
    public static int getMountAimMaxTwist() { return mountAimMaxTwist; }
    public static double getMountTurnSmoothing() { return mountTurnSmoothing; }
    public static int getBoatHeadMaxTwist() { return boatHeadMaxTwist; }
    public static int getBoatBodyMaxTwist() { return boatBodyMaxTwist; }
    public static int getTopDownFov() { return topDownFov; }
    public static boolean isLockedTopDown() { return lockedTopDown; }
    public static boolean isScrollOnlyZoomEnabled() { return scrollOnlyZoomEnabled; }
    public static boolean isTargetLockEnabled() { return targetLockEnabled; }
    public static int getTargetLockDuration() { return targetLockDuration; }
    public static double getTargetHitboxExpansion() { return targetHitboxExpansion; }
    public static boolean isScreenReachEnabled() { return screenReachEnabled; }
    public static double getReachDistance() { return reachDistance; }
    public static double getServerReachDistance() { return serverReachDistance; }
    public static boolean isPlacementPreviewEnabled() { return placementPreviewEnabled; }
    public static double getPlacementTransparency() { return placementTransparency; }
    public static boolean isClickPositionPlacementEnabled() { return clickPositionPlacementEnabled; }
    public static boolean isStaircaseExclusionEnabled() { return staircaseExclusionEnabled; }
    public static int getStaircaseExclusionHeight() { return staircaseExclusionHeight; }
    public static boolean isStaircaseOccludeEnabled() { return staircaseOccludeEnabled; }
    public static double getStaircaseOccludeAlpha() { return staircaseOccludeAlpha; }
    public static boolean isPlacementRotationEnabled() { return placementRotationEnabled; }
    public static boolean isIgnoreLeavesInRaycast() { return ignoreLeavesInRaycast; }
    public static int getSignHoverDisplayMode() { return signHoverDisplayMode; }
    public static double getSignHoverScale() { return signHoverScale; }
    public static boolean isShowInteractionPrompt() { return showInteractionPrompt; }
    public static double getInteractionPromptScale() { return interactionPromptScale; }
    public static boolean isInteractionPromptShadow() { return interactionPromptShadow; }
    public static boolean isShowSpatialPrompt() { return showSpatialPrompt; }
    public static double getSpatialPromptRadius() { return spatialPromptRadius; }
    public static boolean isSpatialPromptAllBlocks() { return spatialPromptAllBlocks; }
    public static double getEffectiveReachDistance() {
        return syncedServerReach >= 0 ? syncedServerReach : reachDistance;
    }
    public static void setSyncedServerReach(double value) { syncedServerReach = value; }
    public static void clearSyncedServerReach() { syncedServerReach = -1.0; }
    public static boolean hasSyncedServerReach() { return syncedServerReach >= 0; }

    public static void setCylinderRadiusHorizontal(int value) { cylinderRadiusHorizontal = clamp(value, 1, 10); }
    public static void setCylinderRadiusVertical(int value) { cylinderRadiusVertical = clamp(value, 1, 10); }
    public static void setCylinderForwardShift(int value) { cylinderForwardShift = clamp(value, 0, 10); }
    public static void setMiningCylinderRadius(int value) { miningCylinderRadius = clamp(value, 1, 16); }
    public static void setMiningCylinderForwardShift(int value) { miningCylinderForwardShift = clamp(value, 0, 10); }
    public static void setMiningModeEnabled(boolean value) { miningModeEnabled = value; }
    public static void setClickToMoveEnabled(boolean value) { clickToMoveEnabled = value; }
    public static void setArrivalThreshold(double value) { arrivalThreshold = clamp(value, 0.5, 5.0); }
    public static void setForceAutoJump(boolean value) { forceAutoJump = value; }
    public static void setSprintDistanceThreshold(double value) { sprintDistanceThreshold = clamp(value, 1.0, 50.0); }
    public static void setAutoAlignToMovementEnabled(boolean value) { autoAlignToMovementEnabled = value; }
    public static void setAutoAlignAngleThreshold(int value) { autoAlignAngleThreshold = clamp(value, 0, 90); }
    public static void setAutoAlignCooldownTicks(int value) { autoAlignCooldownTicks = clamp(value, 0, 100); }
    public static void setStableDirectionAngle(int value) { stableDirectionAngle = clamp(value, 5, 60); }
    public static void setStableDirectionTicks(int value) { stableDirectionTicks = clamp(value, 5, 60); }
    public static void setAutoAlignAnimationSpeed(double value) { autoAlignAnimationSpeed = clamp(value, 0.05, 0.5); }
    public static void setMobCullingEnabled(boolean value) { mobCullingEnabled = value; }
    public static void setTrapdoorTranslucencyEnabled(boolean value) { trapdoorTranslucencyEnabled = value; }
    public static void setTrapdoorTransparency(double value) { trapdoorTransparency = clamp(value, 0.0, 1.0); }
    public static void setFadeEnabled(boolean value) { fadeEnabled = value; }
    public static void setFadeBlockHitThreshold(double value) { fadeBlockHitThreshold = clamp(value, 0.0, 1.0); }
    public static void setFadeStart(double value) { fadeStart = clamp(value, 0.0, 0.9); }
    public static void setFadeNearAlpha(double value) { fadeNearAlpha = clamp(value, 0.0, 1.0); }
    public static void setRangeIndicatorEnabled(boolean value) { rangeIndicatorEnabled = value; }
    public static void setDestinationHighlightEnabled(boolean value) { destinationHighlightEnabled = value; }
    public static void setRangeEmptyHand(double value) { rangeEmptyHand = clamp(value, 1.0, 10.0); }
    public static void setRangeSword(double value) { rangeSword = clamp(value, 1.0, 10.0); }
    public static void setRangeAxe(double value) { rangeAxe = clamp(value, 1.0, 10.0); }
    public static void setRangePickaxe(double value) { rangePickaxe = clamp(value, 1.0, 10.0); }
    public static void setRangeShovel(double value) { rangeShovel = clamp(value, 1.0, 10.0); }
    public static void setRangeOther(double value) { rangeOther = clamp(value, 1.0, 10.0); }
    public static void setDefaultEnabled(boolean value) { defaultEnabled = value; }
    public static void setTargetGlowEnabled(boolean value) { targetGlowEnabled = value; }
    public static void setRotateAngleMode(int value) { rotateAngleMode = clamp(value, 0, 2); }
    public static void setCameraSnapRotationSpeed(double value) { cameraSnapRotationSpeed = clamp(value, 0.05, 0.5); }
    public static void setCameraPitch(double value) { cameraPitch = clamp(value, 10.0, 90.0); }
    public static void setMiningModePitch(double value) { miningModePitch = clamp(value, 10.0, 90.0); }
    public static void setMaxCameraDistance(double value) { maxCameraDistance = clamp(value, 0.0, 200.0); }
    public static void setDefaultCameraDistance(double value) { defaultCameraDistance = Math.min(clamp(value, 0.0, 200.0), maxCameraDistance); }
    public static void setDragRotationEnabled(boolean value) { dragRotationEnabled = value; }
    public static void setCameraYFollowDelayEnabled(boolean value) { cameraYFollowDelayEnabled = value; }
    public static void setCameraYFollowDelay(double value) { cameraYFollowDelay = clamp(value, 0.0, 4.0); }
    public static void setCameraXFollowDelayEnabled(boolean value) { cameraXFollowDelayEnabled = value; }
    public static void setCameraXFollowDelay(double value) { cameraXFollowDelay = clamp(value, 0.0, 4.0); }
    public static void setCameraZFollowDelayEnabled(boolean value) { cameraZFollowDelayEnabled = value; }
    public static void setCameraZFollowDelay(double value) { cameraZFollowDelay = clamp(value, 0.0, 4.0); }
    public static void setFollowDelayWhileMounted(boolean value) { followDelayWhileMounted = value; }
    public static void setPlayerScreenOffset(double value) { playerScreenOffset = clamp(value, -10.0, 10.0); }
    public static void setHeadBodyRotationEnabled(boolean value) { headBodyRotationEnabled = value; }
    public static void setIndependentMountAim(boolean value) { independentMountAim = value; }
    public static void setMountAimMaxTwist(int value) { mountAimMaxTwist = clamp(value, 45, 360); }
    public static void setMountTurnSmoothing(double value) { mountTurnSmoothing = clamp(value, 0.05, 1.0); }
    public static void setBoatHeadMaxTwist(int value) { boatHeadMaxTwist = clamp(value, 30, 180); }
    public static void setBoatBodyMaxTwist(int value) { boatBodyMaxTwist = clamp(value, 15, 90); }
    public static void setTopDownFov(int value) { topDownFov = clamp(value, 30, 110); }
    public static void setLockedTopDown(boolean value) { lockedTopDown = value; }
    public static void setScrollOnlyZoomEnabled(boolean value) { scrollOnlyZoomEnabled = value; }
    public static void setTargetLockEnabled(boolean value) { targetLockEnabled = value; }
    public static void setTargetLockDuration(int value) { targetLockDuration = clamp(value, 0, 600); }
    public static void setTargetHitboxExpansion(double value) { targetHitboxExpansion = clamp(value, 0.0, 5.0); }
    public static void setScreenReachEnabled(boolean value) { screenReachEnabled = value; }
    public static void setReachDistance(double value) { reachDistance = clamp(value, 1.0, 100.0); }
    public static void setPlacementPreviewEnabled(boolean value) { placementPreviewEnabled = value; }
    public static void setPlacementTransparency(double value) { placementTransparency = clamp(value, 0.1, 0.9); }
    public static void setClickPositionPlacementEnabled(boolean value) { clickPositionPlacementEnabled = value; }
    public static void setStaircaseExclusionEnabled(boolean value) { staircaseExclusionEnabled = value; }
    public static void setStaircaseExclusionHeight(int value) { staircaseExclusionHeight = clamp(value, 1, 10); }
    public static void setStaircaseOccludeEnabled(boolean value) { staircaseOccludeEnabled = value; }
    public static void setStaircaseOccludeAlpha(double value) { staircaseOccludeAlpha = clamp(value, 0.0, 1.0); }
    public static void setPlacementRotationEnabled(boolean value) { placementRotationEnabled = value; }
    public static void setIgnoreLeavesInRaycast(boolean value) { ignoreLeavesInRaycast = value; }
    public static void setSignHoverDisplayMode(int value) { signHoverDisplayMode = clamp(value, 0, 2); }
    public static void setSignHoverScale(double value) { signHoverScale = clamp(value, 0.0, 1.0); }
    public static void setShowInteractionPrompt(boolean value) { showInteractionPrompt = value; }
    public static void setInteractionPromptScale(double value) { interactionPromptScale = clamp(value, 0.0, 1.0); }
    public static void setInteractionPromptShadow(boolean value) { interactionPromptShadow = value; }
    public static void setShowSpatialPrompt(boolean value) { showSpatialPrompt = value; }
    public static void setSpatialPromptRadius(double value) { spatialPromptRadius = clamp(value, 1.0, 16.0); }
    public static void setSpatialPromptAllBlocks(boolean value) { spatialPromptAllBlocks = value; }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadClientConfig();
            com.topdownview.state.ModState.STATUS.setEnabled(defaultEnabled);
            notifyConfigChanged();
        } else if (event.getConfig().getSpec() == COMMON_SPEC) {
            loadCommonConfig();
        }
    }

    private static void loadClientConfig() {
        cylinderRadiusHorizontal = CYLINDER_RADIUS_HORIZONTAL.get();
        cylinderRadiusVertical = CYLINDER_RADIUS_VERTICAL.get();
        cylinderForwardShift = CYLINDER_FORWARD_SHIFT.get();
        miningCylinderRadius = MINING_CYLINDER_RADIUS.get();
        miningCylinderForwardShift = MINING_CYLINDER_FORWARD_SHIFT.get();
        miningModeEnabled = MINING_MODE_ENABLED.get();
        clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.get();
        arrivalThreshold = ARRIVAL_THRESHOLD.get();
        forceAutoJump = FORCE_AUTO_JUMP.get();
        sprintDistanceThreshold = SPRINT_DISTANCE_THRESHOLD.get();
        autoAlignToMovementEnabled = AUTO_ALIGN_TO_MOVEMENT_ENABLED.get();
        autoAlignAngleThreshold = AUTO_ALIGN_ANGLE_THRESHOLD.get();
        autoAlignCooldownTicks = AUTO_ALIGN_COOLDOWN_TICKS.get();
        stableDirectionAngle = STABLE_DIRECTION_ANGLE.get();
        stableDirectionTicks = STABLE_DIRECTION_TICKS.get();
        autoAlignAnimationSpeed = AUTO_ALIGN_ANIMATION_SPEED.get();
        mobCullingEnabled = MOB_CULLING_ENABLED.get();
        trapdoorTranslucencyEnabled = TRAPDOOR_TRANSLUCENCY_ENABLED.get();
        trapdoorTransparency = TRAPDOOR_TRANSPARENCY.get();
        fadeEnabled = FADE_ENABLED.get();
        fadeBlockHitThreshold = FADE_BLOCK_HIT_THRESHOLD.get();
        fadeStart = FADE_START.get();
        fadeNearAlpha = FADE_NEAR_ALPHA.get();
        rangeIndicatorEnabled = RANGE_INDICATOR_ENABLED.get();
        destinationHighlightEnabled = DESTINATION_HIGHLIGHT_ENABLED.get();
        rangeEmptyHand = RANGE_EMPTY_HAND.get();
        rangeSword = RANGE_SWORD.get();
        rangeAxe = RANGE_AXE.get();
        rangePickaxe = RANGE_PICKAXE.get();
        rangeShovel = RANGE_SHOVEL.get();
        rangeOther = RANGE_OTHER.get();
        defaultEnabled = DEFAULT_ENABLED.get();
        targetGlowEnabled = TARGET_GLOW_ENABLED.get();
        rotateAngleMode = ROTATE_ANGLE_MODE.get();
        cameraSnapRotationSpeed = CAMERA_SNAP_ROTATION_SPEED.get();
        cameraPitch = CAMERA_PITCH.get();
        miningModePitch = MINING_MODE_PITCH.get();
        maxCameraDistance = MAX_CAMERA_DISTANCE.get();
        defaultCameraDistance = DEFAULT_CAMERA_DISTANCE.get();
        dragRotationEnabled = DRAG_ROTATION_ENABLED.get();
        cameraYFollowDelayEnabled = CAMERA_Y_FOLLOW_DELAY_ENABLED.get();
        cameraYFollowDelay = CAMERA_Y_FOLLOW_DELAY.get();
        cameraXFollowDelayEnabled = CAMERA_X_FOLLOW_DELAY_ENABLED.get();
        cameraXFollowDelay = CAMERA_X_FOLLOW_DELAY.get();
        cameraZFollowDelayEnabled = CAMERA_Z_FOLLOW_DELAY_ENABLED.get();
        cameraZFollowDelay = CAMERA_Z_FOLLOW_DELAY.get();
        followDelayWhileMounted = FOLLOW_DELAY_WHILE_MOUNTED.get();
        playerScreenOffset = PLAYER_SCREEN_OFFSET.get();
        headBodyRotationEnabled = HEAD_BODY_ROTATION_ENABLED.get();
        independentMountAim = INDEPENDENT_MOUNT_AIM.get();
        mountAimMaxTwist = MOUNT_AIM_MAX_TWIST.get();
        mountTurnSmoothing = MOUNT_TURN_SMOOTHING.get();
        boatHeadMaxTwist = BOAT_HEAD_MAX_TWIST.get();
        boatBodyMaxTwist = BOAT_BODY_MAX_TWIST.get();
        topDownFov = TOP_DOWN_FOV.get();
        lockedTopDown = LOCKED_TOP_DOWN.get();
        scrollOnlyZoomEnabled = SCROLL_ONLY_ZOOM_ENABLED.get();
        targetLockEnabled = TARGET_LOCK_ENABLED.get();
        targetLockDuration = TARGET_LOCK_DURATION.get();
        targetHitboxExpansion = TARGET_HITBOX_EXPANSION.get();
        screenReachEnabled = SCREEN_REACH_ENABLED.get();
        reachDistance = REACH_DISTANCE.get();
        placementPreviewEnabled = PLACEMENT_PREVIEW_ENABLED.get();
        placementTransparency = PLACEMENT_TRANSPARENCY.get();
        clickPositionPlacementEnabled = CLICK_POSITION_PLACEMENT_ENABLED.get();
        staircaseExclusionEnabled = STAIRCASE_EXCLUSION_ENABLED.get();
        staircaseExclusionHeight = STAIRCASE_EXCLUSION_HEIGHT.get();
        staircaseOccludeEnabled = STAIRCASE_OCCLUDE_ENABLED.get();
        staircaseOccludeAlpha = STAIRCASE_OCCLUDE_ALPHA.get();
        placementRotationEnabled = PLACEMENT_ROTATION_ENABLED.get();
        ignoreLeavesInRaycast = IGNORE_LEAVES_IN_RAYCAST.get();
        signHoverDisplayMode = SIGN_HOVER_DISPLAY_MODE.get();
        signHoverScale = SIGN_HOVER_SCALE.get();
        showInteractionPrompt = SHOW_INTERACTION_PROMPT.get();
        interactionPromptScale = INTERACTION_PROMPT_SCALE.get();
        interactionPromptShadow = INTERACTION_PROMPT_SHADOW.get();
        showSpatialPrompt = SHOW_SPATIAL_PROMPT.get();
        spatialPromptRadius = SPATIAL_PROMPT_RADIUS.get();
        spatialPromptAllBlocks = SPATIAL_PROMPT_ALL_BLOCKS.get();
    }

    private static void loadCommonConfig() {
        serverReachDistance = SERVER_REACH_DISTANCE.get();
    }

    public static void save() {
        TopDownViewMod.getLogger().info("[TopDownView][Config.save] Saving values - maxCameraDistance: {}, defaultCameraDistance: {}",
                maxCameraDistance, defaultCameraDistance);
        CYLINDER_RADIUS_HORIZONTAL.set(cylinderRadiusHorizontal);
        CYLINDER_RADIUS_VERTICAL.set(cylinderRadiusVertical);
        CYLINDER_FORWARD_SHIFT.set(cylinderForwardShift);
        MINING_CYLINDER_RADIUS.set(miningCylinderRadius);
        MINING_CYLINDER_FORWARD_SHIFT.set(miningCylinderForwardShift);
        MINING_MODE_ENABLED.set(miningModeEnabled);
        CLICK_TO_MOVE_ENABLED.set(clickToMoveEnabled);
        ARRIVAL_THRESHOLD.set(arrivalThreshold);
        FORCE_AUTO_JUMP.set(forceAutoJump);
        SPRINT_DISTANCE_THRESHOLD.set(sprintDistanceThreshold);
        AUTO_ALIGN_TO_MOVEMENT_ENABLED.set(autoAlignToMovementEnabled);
        AUTO_ALIGN_ANGLE_THRESHOLD.set(autoAlignAngleThreshold);
        AUTO_ALIGN_COOLDOWN_TICKS.set(autoAlignCooldownTicks);
        STABLE_DIRECTION_ANGLE.set(stableDirectionAngle);
        STABLE_DIRECTION_TICKS.set(stableDirectionTicks);
        AUTO_ALIGN_ANIMATION_SPEED.set(autoAlignAnimationSpeed);
        MOB_CULLING_ENABLED.set(mobCullingEnabled);
        TRAPDOOR_TRANSLUCENCY_ENABLED.set(trapdoorTranslucencyEnabled);
        TRAPDOOR_TRANSPARENCY.set(trapdoorTransparency);
        FADE_ENABLED.set(fadeEnabled);
        FADE_BLOCK_HIT_THRESHOLD.set(fadeBlockHitThreshold);
        FADE_START.set(fadeStart);
        FADE_NEAR_ALPHA.set(fadeNearAlpha);
        RANGE_INDICATOR_ENABLED.set(rangeIndicatorEnabled);
        DESTINATION_HIGHLIGHT_ENABLED.set(destinationHighlightEnabled);
        RANGE_EMPTY_HAND.set(rangeEmptyHand);
        RANGE_SWORD.set(rangeSword);
        RANGE_AXE.set(rangeAxe);
        RANGE_PICKAXE.set(rangePickaxe);
        RANGE_SHOVEL.set(rangeShovel);
        RANGE_OTHER.set(rangeOther);
        DEFAULT_ENABLED.set(defaultEnabled);
        TARGET_GLOW_ENABLED.set(targetGlowEnabled);
        ROTATE_ANGLE_MODE.set(rotateAngleMode);
        CAMERA_SNAP_ROTATION_SPEED.set(cameraSnapRotationSpeed);
        CAMERA_PITCH.set(cameraPitch);
        MINING_MODE_PITCH.set(miningModePitch);
        MAX_CAMERA_DISTANCE.set(maxCameraDistance);
        DEFAULT_CAMERA_DISTANCE.set(defaultCameraDistance);
        DRAG_ROTATION_ENABLED.set(dragRotationEnabled);
        CAMERA_Y_FOLLOW_DELAY_ENABLED.set(cameraYFollowDelayEnabled);
        CAMERA_Y_FOLLOW_DELAY.set(cameraYFollowDelay);
        CAMERA_X_FOLLOW_DELAY_ENABLED.set(cameraXFollowDelayEnabled);
        CAMERA_X_FOLLOW_DELAY.set(cameraXFollowDelay);
        CAMERA_Z_FOLLOW_DELAY_ENABLED.set(cameraZFollowDelayEnabled);
        CAMERA_Z_FOLLOW_DELAY.set(cameraZFollowDelay);
        FOLLOW_DELAY_WHILE_MOUNTED.set(followDelayWhileMounted);
        PLAYER_SCREEN_OFFSET.set(playerScreenOffset);
        HEAD_BODY_ROTATION_ENABLED.set(headBodyRotationEnabled);
        INDEPENDENT_MOUNT_AIM.set(independentMountAim);
        MOUNT_AIM_MAX_TWIST.set(mountAimMaxTwist);
        MOUNT_TURN_SMOOTHING.set(mountTurnSmoothing);
        BOAT_HEAD_MAX_TWIST.set(boatHeadMaxTwist);
        BOAT_BODY_MAX_TWIST.set(boatBodyMaxTwist);
        TOP_DOWN_FOV.set(topDownFov);
        LOCKED_TOP_DOWN.set(lockedTopDown);
        SCROLL_ONLY_ZOOM_ENABLED.set(scrollOnlyZoomEnabled);
        TARGET_LOCK_ENABLED.set(targetLockEnabled);
        TARGET_LOCK_DURATION.set(targetLockDuration);
        TARGET_HITBOX_EXPANSION.set(targetHitboxExpansion);
        SCREEN_REACH_ENABLED.set(screenReachEnabled);
        REACH_DISTANCE.set(reachDistance);
        PLACEMENT_PREVIEW_ENABLED.set(placementPreviewEnabled);
        PLACEMENT_TRANSPARENCY.set(placementTransparency);
        CLICK_POSITION_PLACEMENT_ENABLED.set(clickPositionPlacementEnabled);
        STAIRCASE_EXCLUSION_ENABLED.set(staircaseExclusionEnabled);
        STAIRCASE_EXCLUSION_HEIGHT.set(staircaseExclusionHeight);
        STAIRCASE_OCCLUDE_ENABLED.set(staircaseOccludeEnabled);
        STAIRCASE_OCCLUDE_ALPHA.set(staircaseOccludeAlpha);
        PLACEMENT_ROTATION_ENABLED.set(placementRotationEnabled);
        IGNORE_LEAVES_IN_RAYCAST.set(ignoreLeavesInRaycast);
        SIGN_HOVER_DISPLAY_MODE.set(signHoverDisplayMode);
        SIGN_HOVER_SCALE.set(signHoverScale);
        SHOW_INTERACTION_PROMPT.set(showInteractionPrompt);
        INTERACTION_PROMPT_SCALE.set(interactionPromptScale);
        INTERACTION_PROMPT_SHADOW.set(interactionPromptShadow);
        SHOW_SPATIAL_PROMPT.set(showSpatialPrompt);
        SPATIAL_PROMPT_RADIUS.set(spatialPromptRadius);
        SPATIAL_PROMPT_ALL_BLOCKS.set(spatialPromptAllBlocks);
        SPEC.save();
        TopDownViewMod.getLogger().info("[TopDownView][Config.save] Config file saved successfully");
        notifyConfigChanged();
    }

    public static void resetToDefaults() {
        cylinderRadiusHorizontal = CYLINDER_RADIUS_HORIZONTAL.getDefault();
        cylinderRadiusVertical = CYLINDER_RADIUS_VERTICAL.getDefault();
        cylinderForwardShift = CYLINDER_FORWARD_SHIFT.getDefault();
        miningCylinderRadius = MINING_CYLINDER_RADIUS.getDefault();
        miningCylinderForwardShift = MINING_CYLINDER_FORWARD_SHIFT.getDefault();
        miningModeEnabled = MINING_MODE_ENABLED.getDefault();
        clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.getDefault();
        arrivalThreshold = ARRIVAL_THRESHOLD.getDefault();
        forceAutoJump = FORCE_AUTO_JUMP.getDefault();
        sprintDistanceThreshold = SPRINT_DISTANCE_THRESHOLD.getDefault();
        autoAlignToMovementEnabled = AUTO_ALIGN_TO_MOVEMENT_ENABLED.getDefault();
        autoAlignAngleThreshold = AUTO_ALIGN_ANGLE_THRESHOLD.getDefault();
        autoAlignCooldownTicks = AUTO_ALIGN_COOLDOWN_TICKS.getDefault();
        stableDirectionAngle = STABLE_DIRECTION_ANGLE.getDefault();
        stableDirectionTicks = STABLE_DIRECTION_TICKS.getDefault();
        autoAlignAnimationSpeed = AUTO_ALIGN_ANIMATION_SPEED.getDefault();
        mobCullingEnabled = MOB_CULLING_ENABLED.getDefault();
        trapdoorTranslucencyEnabled = TRAPDOOR_TRANSLUCENCY_ENABLED.getDefault();
        trapdoorTransparency = TRAPDOOR_TRANSPARENCY.getDefault();
        fadeEnabled = FADE_ENABLED.getDefault();
        fadeBlockHitThreshold = FADE_BLOCK_HIT_THRESHOLD.getDefault();
        fadeStart = FADE_START.getDefault();
        fadeNearAlpha = FADE_NEAR_ALPHA.getDefault();
        rangeIndicatorEnabled = RANGE_INDICATOR_ENABLED.getDefault();
        destinationHighlightEnabled = DESTINATION_HIGHLIGHT_ENABLED.getDefault();
        rangeEmptyHand = RANGE_EMPTY_HAND.getDefault();
        rangeSword = RANGE_SWORD.getDefault();
        rangeAxe = RANGE_AXE.getDefault();
        rangePickaxe = RANGE_PICKAXE.getDefault();
        rangeShovel = RANGE_SHOVEL.getDefault();
        rangeOther = RANGE_OTHER.getDefault();
        defaultEnabled = DEFAULT_ENABLED.getDefault();
        targetGlowEnabled = TARGET_GLOW_ENABLED.getDefault();
        rotateAngleMode = ROTATE_ANGLE_MODE.getDefault();
        cameraSnapRotationSpeed = CAMERA_SNAP_ROTATION_SPEED.getDefault();
        cameraPitch = CAMERA_PITCH.getDefault();
        miningModePitch = MINING_MODE_PITCH.getDefault();
        maxCameraDistance = MAX_CAMERA_DISTANCE.getDefault();
        defaultCameraDistance = DEFAULT_CAMERA_DISTANCE.getDefault();
        dragRotationEnabled = DRAG_ROTATION_ENABLED.getDefault();
        cameraYFollowDelayEnabled = CAMERA_Y_FOLLOW_DELAY_ENABLED.getDefault();
        cameraYFollowDelay = CAMERA_Y_FOLLOW_DELAY.getDefault();
        cameraXFollowDelayEnabled = CAMERA_X_FOLLOW_DELAY_ENABLED.getDefault();
        cameraXFollowDelay = CAMERA_X_FOLLOW_DELAY.getDefault();
        cameraZFollowDelayEnabled = CAMERA_Z_FOLLOW_DELAY_ENABLED.getDefault();
        cameraZFollowDelay = CAMERA_Z_FOLLOW_DELAY.getDefault();
        followDelayWhileMounted = FOLLOW_DELAY_WHILE_MOUNTED.getDefault();
        playerScreenOffset = PLAYER_SCREEN_OFFSET.getDefault();
        headBodyRotationEnabled = HEAD_BODY_ROTATION_ENABLED.getDefault();
        independentMountAim = INDEPENDENT_MOUNT_AIM.getDefault();
        mountAimMaxTwist = MOUNT_AIM_MAX_TWIST.getDefault();
        mountTurnSmoothing = MOUNT_TURN_SMOOTHING.getDefault();
        boatHeadMaxTwist = BOAT_HEAD_MAX_TWIST.getDefault();
        boatBodyMaxTwist = BOAT_BODY_MAX_TWIST.getDefault();
        topDownFov = TOP_DOWN_FOV.getDefault();
        lockedTopDown = LOCKED_TOP_DOWN.getDefault();
        scrollOnlyZoomEnabled = SCROLL_ONLY_ZOOM_ENABLED.getDefault();
        targetLockEnabled = TARGET_LOCK_ENABLED.getDefault();
        targetLockDuration = TARGET_LOCK_DURATION.getDefault();
        targetHitboxExpansion = TARGET_HITBOX_EXPANSION.getDefault();
        screenReachEnabled = SCREEN_REACH_ENABLED.getDefault();
        reachDistance = REACH_DISTANCE.getDefault();
        placementPreviewEnabled = PLACEMENT_PREVIEW_ENABLED.getDefault();
        placementTransparency = PLACEMENT_TRANSPARENCY.getDefault();
        clickPositionPlacementEnabled = CLICK_POSITION_PLACEMENT_ENABLED.getDefault();
        staircaseExclusionEnabled = STAIRCASE_EXCLUSION_ENABLED.getDefault();
        staircaseExclusionHeight = STAIRCASE_EXCLUSION_HEIGHT.getDefault();
        staircaseOccludeEnabled = STAIRCASE_OCCLUDE_ENABLED.getDefault();
        staircaseOccludeAlpha = STAIRCASE_OCCLUDE_ALPHA.getDefault();
        placementRotationEnabled = PLACEMENT_ROTATION_ENABLED.getDefault();
        ignoreLeavesInRaycast = IGNORE_LEAVES_IN_RAYCAST.getDefault();
        signHoverDisplayMode = SIGN_HOVER_DISPLAY_MODE.getDefault();
        signHoverScale = SIGN_HOVER_SCALE.getDefault();
        showInteractionPrompt = SHOW_INTERACTION_PROMPT.getDefault();
        interactionPromptScale = INTERACTION_PROMPT_SCALE.getDefault();
        interactionPromptShadow = INTERACTION_PROMPT_SHADOW.getDefault();
        showSpatialPrompt = SHOW_SPATIAL_PROMPT.getDefault();
        spatialPromptRadius = SPATIAL_PROMPT_RADIUS.getDefault();
        spatialPromptAllBlocks = SPATIAL_PROMPT_ALL_BLOCKS.getDefault();
    }

    public static ForgeConfigSpec.DoubleValue getMaxCameraDistanceSpec() {
        return MAX_CAMERA_DISTANCE;
    }

    public static ForgeConfigSpec.DoubleValue getDefaultCameraDistanceSpec() {
        return DEFAULT_CAMERA_DISTANCE;
    }
}