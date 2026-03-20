package com.topdownview;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final List<Runnable> configChangeListeners = new ArrayList<>();

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
    private static final ForgeConfigSpec.IntValue ROTATE_ANGLE_MODE = BUILDER
            .defineInRange("rotateAngleMode", 0, 0, 2);
    private static final ForgeConfigSpec.DoubleValue CAMERA_SNAP_ROTATION_SPEED = BUILDER
            .defineInRange("cameraSnapRotationSpeed", 0.2, 0.05, 0.5);
    private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
            .defineInRange("cameraPitch", 45.0, 10.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MINING_MODE_PITCH = BUILDER
            .defineInRange("miningModePitch", 45.0, 10.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MAX_CAMERA_DISTANCE = BUILDER
            .defineInRange("maxCameraDistance", 50.0, 0.0, 200.0);
    private static final ForgeConfigSpec.DoubleValue DEFAULT_CAMERA_DISTANCE = BUILDER
            .defineInRange("defaultCameraDistance", 9.0, 0.0, 100.0);
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

    public static final ForgeConfigSpec SPEC = BUILDER.build();

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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
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

        com.topdownview.state.ModState.STATUS.setEnabled(defaultEnabled);
        notifyConfigChanged();
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
        SPEC.save();
        TopDownViewMod.getLogger().info("[TopDownView][Config.save] Config file saved successfully");
        notifyConfigChanged();
    }

    public static ForgeConfigSpec.DoubleValue getMaxCameraDistanceSpec() {
        return MAX_CAMERA_DISTANCE;
    }

    public static ForgeConfigSpec.DoubleValue getDefaultCameraDistanceSpec() {
        return DEFAULT_CAMERA_DISTANCE;
    }
}