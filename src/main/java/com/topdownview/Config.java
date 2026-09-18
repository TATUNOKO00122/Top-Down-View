package com.topdownview;

import com.topdownview.config.CameraConfig;
import com.topdownview.config.CullingConfig;
import com.topdownview.config.IntegrationsConfig;
import com.topdownview.config.InteractionConfig;
import com.topdownview.config.PlacementConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mod全体の設定管理ファサードクラス。
 * 内部の設定保持と検証は機能ドメインごとのサブ構成クラス (CameraConfig, CullingConfig 等) に委譲されます。
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    public static final CameraConfig CAMERA = new CameraConfig();
    public static final CullingConfig CULLING = new CullingConfig();
    public static final PlacementConfig PLACEMENT = new PlacementConfig();
    public static final InteractionConfig INTERACTION = new InteractionConfig();
    public static final IntegrationsConfig INTEGRATIONS = new IntegrationsConfig();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final List<Runnable> configChangeListeners = new CopyOnWriteArrayList<>();
    /** save() 実行中フラグ: Reloading イベントの非同期割り込みによるキャッシュ上書きを防止 */
    private static volatile boolean isSaving = false;

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
    private static final ForgeConfigSpec.BooleanValue UNDERGROUND_CULLING_ENABLED = BUILDER
            .comment("Cull blocks deep below the surface that are far from the player and invisible from above.")
            .define("undergroundCullingEnabled", true);
    private static final ForgeConfigSpec.IntValue UNDERGROUND_CULLING_START_DISTANCE = BUILDER
            .defineInRange("undergroundCullingStartDistance", 4, 1, 16);
    private static final ForgeConfigSpec.IntValue UNDERGROUND_CULLING_KEEP_DEPTH = BUILDER
            .defineInRange("undergroundCullingKeepDepth", 16, 4, 64);
    private static final ForgeConfigSpec.BooleanValue MINING_MODE_ENABLED = BUILDER
            .define("miningModeEnabled", false);
    private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
            .define("clickToMoveEnabled", false);
    private static final ForgeConfigSpec.BooleanValue BARITONE_RENDER_PATH = BUILDER
            .define("baritoneRenderPath", true);
    private static final ForgeConfigSpec.BooleanValue BARITONE_RENDER_GOAL = BUILDER
            .define("baritoneRenderGoal", true);
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
            .defineInRange("autoAlignAnimationSpeed", 0.1, 0.01, 0.19);
    private static final ForgeConfigSpec.BooleanValue AUTO_ALIGN_ANIMATION_ACCELERATION = BUILDER
            .define("autoAlignAnimationAcceleration", false);
    private static final ForgeConfigSpec.BooleanValue MOB_CULLING_ENABLED = BUILDER
            .define("mobCullingEnabled", true);
    private static final ForgeConfigSpec.BooleanValue MOB_TRANSLUCENCY_ENABLED = BUILDER
            .define("mobTranslucencyEnabled", false);
    private static final ForgeConfigSpec.DoubleValue MOB_TRANSLUCENCY_ALPHA = BUILDER
            .defineInRange("mobTranslucencyAlpha", 0.5, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue TRAPDOOR_TRANSLUCENCY_ENABLED = BUILDER
            .define("trapdoorTranslucencyEnabled", false);
    private static final ForgeConfigSpec.DoubleValue TRAPDOOR_TRANSPARENCY = BUILDER
            .defineInRange("trapdoorTransparency", 0.3, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue FADE_ENABLED = BUILDER
            .define("fadeEnabled", false);
    private static final ForgeConfigSpec.DoubleValue FADE_BLOCK_HIT_THRESHOLD = BUILDER
            .defineInRange("fadeBlockHitThreshold", 0.5, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue FADE_START = BUILDER
            .defineInRange("fadeStart", 0.7, 0.0, 0.9);
    private static final ForgeConfigSpec.DoubleValue FADE_NEAR_ALPHA = BUILDER
            .defineInRange("fadeNearAlpha", 0.0, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue FADE_SMOOTHING_HALF_LIFE = BUILDER
            .comment("Exponential smoothing half-life (seconds) for the translucent fade alpha. 0 disables smoothing.")
            .defineInRange("fadeSmoothingHalfLife", 0.14, 0.0, 1.0);
    private static final ForgeConfigSpec.IntValue CULLING_MODE = BUILDER
            .comment("0 = Legacy (cylinder), 1 = New (cover + corridor).")
            .defineInRange("cullingMode", 1, 0, 1);
    private static final ForgeConfigSpec.IntValue VIEW_WEDGE_HALF_ANGLE = BUILDER
            .defineInRange("viewWedgeHalfAngle", 60, 10, 90);
    private static final ForgeConfigSpec.IntValue COVER_CULLING_RADIUS = BUILDER
            .defineInRange("coverCullingRadius", 10, 4, 24);
    private static final ForgeConfigSpec.BooleanValue COVER_CULLING_VIEWSHED_ENABLED = BUILDER
            .comment("Culls covers only over ground actually visible from the player's eye.")
            .define("coverCullingViewshedEnabled", true);
    private static final ForgeConfigSpec.BooleanValue INDOOR_CEILING_CULLING_ENABLED = BUILDER
            .comment("Culls the ceiling slice above the player's floor while indoors.")
            .define("indoorCeilingCullingEnabled", true);
    private static final ForgeConfigSpec.BooleanValue PLAYER_NEAR_TRANSLUCENCY_ENABLED = BUILDER
            .define("playerNearTranslucencyEnabled", true);
    private static final ForgeConfigSpec.DoubleValue PLAYER_NEAR_TRANSLUCENCY_ALPHA = BUILDER
            .defineInRange("playerNearTranslucencyAlpha", 0.6, 0.0, 1.0);
    private static final ForgeConfigSpec.IntValue PLAYER_NEAR_TRANSLUCENCY_RANGE_HORIZONTAL = BUILDER
            .defineInRange("playerNearTranslucencyRangeHorizontal", 2, 1, 5);
    private static final ForgeConfigSpec.IntValue PLAYER_NEAR_TRANSLUCENCY_RANGE_VERTICAL = BUILDER
            .defineInRange("playerNearTranslucencyRangeVertical", 2, 1, 5);
    private static final ForgeConfigSpec.BooleanValue PLAYER_NEAR_TRANSLUCENCY_HITTABLE = BUILDER
            .comment("Whether proximity-displayed blocks can be hit by the mouse raycast.")
            .define("playerNearTranslucencyHittable", true);
    private static final ForgeConfigSpec.BooleanValue DISABLE_FADE_INDOORS = BUILDER
            .comment("Disables boundary fade and player-near translucency while the player is indoors.")
            .define("disableFadeIndoors", true);
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
    private static final ForgeConfigSpec.BooleanValue MOB_CONE_CULLING_ENABLED = BUILDER
            .define("mobConeCullingEnabled", false);
    private static final ForgeConfigSpec.DoubleValue MOB_CONE_HALF_ANGLE = BUILDER
            .defineInRange("mobConeHalfAngle", 30.0, 10.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MOB_CONE_FADE_ANGLE = BUILDER
            .defineInRange("mobConeFadeAngle", 10.0, 0.0, 90.0);
    private static final ForgeConfigSpec.DoubleValue MOB_NEAR_RADIUS = BUILDER
            .defineInRange("mobNearRadius", 3.0, 0.0, 20.0);
    private static final ForgeConfigSpec.DoubleValue MOB_FOG_END = BUILDER
            .defineInRange("mobFogEnd", 12.0, 1.0, 50.0);
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
    private static final ForgeConfigSpec.BooleanValue WATER_MOVEMENT_CONTROL_ENABLED = BUILDER
            .define("waterMovementControlEnabled", true);
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
            .defineInRange("topDownFov", 30, 5, 110);
    private static final ForgeConfigSpec.BooleanValue LOCKED_TOP_DOWN = BUILDER
            .comment("Locks the camera to top-down view.", "Edit the config file directly to change it.")
            .define("lockedTopDown", false);
    private static final ForgeConfigSpec.BooleanValue SCROLL_ONLY_ZOOM_ENABLED = BUILDER
            .define("scrollOnlyZoomEnabled", false);
    private static final ForgeConfigSpec.BooleanValue CAMERA_ZOOM_SMOOTHING_ENABLED = BUILDER
            .define("cameraZoomSmoothingEnabled", true);
    private static final ForgeConfigSpec.DoubleValue CAMERA_ZOOM_SMOOTHING = BUILDER
            .defineInRange("cameraZoomSmoothing", 0.15, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue MOUSE_PAN_ENABLED = BUILDER
            .define("mousePanEnabled", false);
    private static final ForgeConfigSpec.DoubleValue MOUSE_PAN_MAX_DISTANCE = BUILDER
            .defineInRange("mousePanMaxDistance", 5.0, 0.0, 20.0);
    private static final ForgeConfigSpec.DoubleValue MOUSE_PAN_SMOOTHING = BUILDER
            .defineInRange("mousePanSmoothing", 0.010, 0.0, 0.100);
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

    private static final ForgeConfigSpec.BooleanValue PLACEMENT_PREVIEW_ENABLED = BUILDER
            .define("placementPreviewEnabled", true);
    private static final ForgeConfigSpec.DoubleValue PLACEMENT_TRANSPARENCY = BUILDER
            .defineInRange("placementTransparency", 0.5, 0.1, 0.9);
    private static final ForgeConfigSpec.BooleanValue CLICK_POSITION_PLACEMENT_ENABLED = BUILDER
            .define("clickPositionPlacementEnabled", true);

    private static final ForgeConfigSpec.BooleanValue STAIRCASE_EXCLUSION_ENABLED = BUILDER
            .comment("Excludes staircase blocks from culling.")
            .define("staircaseExclusionEnabled", true);
    private static final ForgeConfigSpec.IntValue STAIRCASE_EXCLUSION_HEIGHT = BUILDER
            .defineInRange("staircaseExclusionHeight", 2, 1, 10);
    private static final ForgeConfigSpec.BooleanValue STAIRCASE_OCCLUDE_ENABLED = BUILDER
            .define("staircaseOccludeEnabled", true);
    private static final ForgeConfigSpec.DoubleValue STAIRCASE_OCCLUDE_ALPHA = BUILDER
            .defineInRange("staircaseOccludeAlpha", 0.4, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue LADDER_OCCLUDE_ENABLED = BUILDER
            .define("ladderOccludeEnabled", true);
    private static final ForgeConfigSpec.DoubleValue LADDER_OCCLUDE_ALPHA = BUILDER
            .defineInRange("ladderOccludeAlpha", 0.4, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue TREE_OCCLUDE_ENABLED = BUILDER
            .define("treeOccludeEnabled", true);
    private static final ForgeConfigSpec.DoubleValue TREE_OCCLUDE_ALPHA = BUILDER
            .defineInRange("treeOccludeAlpha", 0.4, 0.0, 1.0);

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
    private static final ForgeConfigSpec.BooleanValue PERFORMANCE_MONITOR_ENABLED = BUILDER
            .comment("Shows the live performance overlay (FPS, phase timings) and logs a summary every 5 seconds.")
            .define("performanceMonitorEnabled", false);

    private static final ForgeConfigSpec.BooleanValue IGNORE_LEAVES_IN_RAYCAST = BUILDER
            .define("ignoreLeavesInRaycast", false);
    private static final ForgeConfigSpec.BooleanValue PROTECT_NATURAL_TREE_LOGS = BUILDER
            .define("protectNaturalTreeLogs", false);

    private static final ForgeConfigSpec.BooleanValue TRANSLUCENT_FLUID = BUILDER
            .define("translucentFluid", true);
    private static final ForgeConfigSpec.DoubleValue FLUID_ALPHA = BUILDER
            .defineInRange("fluidAlpha", 0.35, 0.05, 1.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue SERVER_REACH_DISTANCE = COMMON_BUILDER
            .defineInRange("serverReachDistance", 10.0, 1.0, 100.0);
    public static final ForgeConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    // Delegation Getters / Setters for Backward Compatibility
    public static int getCylinderRadiusHorizontal() { return CULLING.getCylinderRadiusHorizontal(); }
    public static int getCylinderRadiusVertical() { return CULLING.getCylinderRadiusVertical(); }
    public static int getCylinderForwardShift() { return CULLING.getCylinderForwardShift(); }
    public static int getMiningCylinderRadius() { return CULLING.getMiningCylinderRadius(); }
    public static int getMiningCylinderForwardShift() { return CULLING.getMiningCylinderForwardShift(); }
    public static boolean isUndergroundCullingEnabled() { return CULLING.isUndergroundCullingEnabled(); }
    public static int getUndergroundCullingStartDistance() { return CULLING.getUndergroundCullingStartDistance(); }
    public static int getUndergroundCullingKeepDepth() { return CULLING.getUndergroundCullingKeepDepth(); }
    public static boolean isMiningModeEnabled() { return INTERACTION.isMiningModeEnabled(); }
    public static boolean isClickToMoveEnabled() { return INTERACTION.isClickToMoveEnabled(); }
    public static boolean isBaritoneRenderPath() { return INTEGRATIONS.isBaritoneRenderPath(); }
    public static boolean isBaritoneRenderGoal() { return INTEGRATIONS.isBaritoneRenderGoal(); }
    public static double getArrivalThreshold() { return INTERACTION.getArrivalThreshold(); }
    public static boolean isForceAutoJump() { return INTERACTION.isForceAutoJump(); }
    public static double getSprintDistanceThreshold() { return INTERACTION.getSprintDistanceThreshold(); }
    public static boolean isAutoAlignToMovementEnabled() { return INTERACTION.isAutoAlignToMovementEnabled(); }
    public static int getAutoAlignAngleThreshold() { return INTERACTION.getAutoAlignAngleThreshold(); }
    public static int getAutoAlignCooldownTicks() { return INTERACTION.getAutoAlignCooldownTicks(); }
    public static int getStableDirectionAngle() { return INTERACTION.getStableDirectionAngle(); }
    public static int getStableDirectionTicks() { return INTERACTION.getStableDirectionTicks(); }
    public static double getAutoAlignAnimationSpeed() { return INTERACTION.getAutoAlignAnimationSpeed(); }
    public static boolean isAutoAlignAnimationAcceleration() { return INTERACTION.isAutoAlignAnimationAcceleration(); }
    public static boolean isMobCullingEnabled() { return CULLING.isMobCullingEnabled(); }
    public static boolean isMobTranslucencyEnabled() { return CULLING.isMobTranslucencyEnabled(); }
    public static double getMobTranslucencyAlpha() { return CULLING.getMobTranslucencyAlpha(); }
    public static boolean isTrapdoorTranslucencyEnabled() { return CULLING.isTrapdoorTranslucencyEnabled(); }
    public static double getTrapdoorTransparency() { return CULLING.getTrapdoorTransparency(); }
    public static boolean isFadeEnabled() { return CULLING.isFadeEnabled(); }
    public static double getFadeBlockHitThreshold() { return CULLING.getFadeBlockHitThreshold(); }
    public static double getFadeStart() { return CULLING.getFadeStart(); }
    public static double getFadeNearAlpha() { return CULLING.getFadeNearAlpha(); }
    public static double getFadeSmoothingHalfLife() { return CULLING.getFadeSmoothingHalfLife(); }
    public static boolean isDisableFadeIndoors() { return CULLING.isDisableFadeIndoors(); }
    public static int getViewWedgeHalfAngle() { return CULLING.getViewWedgeHalfAngle(); }
    public static int getCoverCullingRadius() { return CULLING.getCoverCullingRadius(); }
    public static boolean isCoverCullingViewshedEnabled() { return CULLING.isCoverCullingViewshedEnabled(); }
    public static int getCullingMode() { return CULLING.getCullingMode(); }
    public static boolean isIndoorCeilingCullingEnabled() { return CULLING.isIndoorCeilingCullingEnabled(); }
    public static boolean isPlayerNearTranslucencyEnabled() { return CULLING.isPlayerNearTranslucencyEnabled(); }
    public static double getPlayerNearTranslucencyAlpha() { return CULLING.getPlayerNearTranslucencyAlpha(); }
    public static int getPlayerNearTranslucencyRangeHorizontal() { return CULLING.getPlayerNearTranslucencyRangeHorizontal(); }
    public static int getPlayerNearTranslucencyRangeVertical() { return CULLING.getPlayerNearTranslucencyRangeVertical(); }
    public static boolean isPlayerNearTranslucencyHittable() { return CULLING.isPlayerNearTranslucencyHittable(); }
    public static boolean isRangeIndicatorEnabled() { return INTERACTION.isRangeIndicatorEnabled(); }
    public static boolean isDestinationHighlightEnabled() { return INTERACTION.isDestinationHighlightEnabled(); }
    public static double getRangeEmptyHand() { return INTERACTION.getRangeEmptyHand(); }
    public static double getRangeSword() { return INTERACTION.getRangeSword(); }
    public static double getRangeAxe() { return INTERACTION.getRangeAxe(); }
    public static double getRangePickaxe() { return INTERACTION.getRangePickaxe(); }
    public static double getRangeShovel() { return INTERACTION.getRangeShovel(); }
    public static double getRangeOther() { return INTERACTION.getRangeOther(); }
    public static boolean isDefaultEnabled() { return INTERACTION.isDefaultEnabled(); }
    public static boolean isTargetGlowEnabled() { return INTERACTION.isTargetGlowEnabled(); }
    public static boolean isMobConeCullingEnabled() { return CULLING.isMobConeCullingEnabled(); }
    public static double getMobConeHalfAngle() { return CULLING.getMobConeHalfAngle(); }
    public static double getMobConeFadeAngle() { return CULLING.getMobConeFadeAngle(); }
    public static double getMobNearRadius() { return CULLING.getMobNearRadius(); }
    public static double getMobFogEnd() { return CULLING.getMobFogEnd(); }
    public static int getRotateAngleMode() { return CAMERA.getRotateAngleMode(); }
    public static double getCameraSnapRotationSpeed() { return CAMERA.getCameraSnapRotationSpeed(); }
    public static double getCameraPitch() { return CAMERA.getCameraPitch(); }
    public static double getMiningModePitch() { return CAMERA.getMiningModePitch(); }
    public static double getMaxCameraDistance() { return CAMERA.getMaxCameraDistance(); }
    public static double getDefaultCameraDistance() { return CAMERA.getDefaultCameraDistance(); }
    public static boolean isCameraYFollowDelayEnabled() { return CAMERA.isCameraYFollowDelayEnabled(); }
    public static double getCameraYFollowDelay() { return CAMERA.getCameraYFollowDelay(); }
    public static boolean isCameraXFollowDelayEnabled() { return CAMERA.isCameraXFollowDelayEnabled(); }
    public static double getCameraXFollowDelay() { return CAMERA.getCameraXFollowDelay(); }
    public static boolean isCameraZFollowDelayEnabled() { return CAMERA.isCameraZFollowDelayEnabled(); }
    public static double getCameraZFollowDelay() { return CAMERA.getCameraZFollowDelay(); }
    public static boolean isFollowDelayWhileMounted() { return CAMERA.isFollowDelayWhileMounted(); }
    public static double getPlayerScreenOffset() { return CAMERA.getPlayerScreenOffset(); }
    public static boolean isHeadBodyRotationEnabled() { return CAMERA.isHeadBodyRotationEnabled(); }
    public static boolean isWaterMovementControlEnabled() { return CAMERA.isWaterMovementControlEnabled(); }
    public static boolean isIndependentMountAim() { return CAMERA.isIndependentMountAim(); }
    public static int getMountAimMaxTwist() { return CAMERA.getMountAimMaxTwist(); }
    public static double getMountTurnSmoothing() { return CAMERA.getMountTurnSmoothing(); }
    public static int getBoatHeadMaxTwist() { return CAMERA.getBoatHeadMaxTwist(); }
    public static int getBoatBodyMaxTwist() { return CAMERA.getBoatBodyMaxTwist(); }
    public static int getTopDownFov() { return CAMERA.getTopDownFov(); }
    public static boolean isLockedTopDown() { return CAMERA.isLockedTopDown(); }
    public static boolean isScrollOnlyZoomEnabled() { return CAMERA.isScrollOnlyZoomEnabled(); }
    public static boolean isCameraZoomSmoothingEnabled() { return CAMERA.isCameraZoomSmoothingEnabled(); }
    public static double getCameraZoomSmoothing() { return CAMERA.getCameraZoomSmoothing(); }
    public static boolean isMousePanEnabled() { return CAMERA.isMousePanEnabled(); }
    public static double getMousePanMaxDistance() { return CAMERA.getMousePanMaxDistance(); }
    public static double getMousePanSmoothing() { return CAMERA.getMousePanSmoothing(); }
    public static boolean isTargetLockEnabled() { return INTERACTION.isTargetLockEnabled(); }
    public static int getTargetLockDuration() { return INTERACTION.getTargetLockDuration(); }
    public static double getTargetHitboxExpansion() { return INTERACTION.getTargetHitboxExpansion(); }
    public static boolean isScreenReachEnabled() { return INTERACTION.isScreenReachEnabled(); }
    public static double getReachDistance() { return INTERACTION.getReachDistance(); }
    public static double getServerReachDistance() { return INTERACTION.getServerReachDistance(); }
    public static boolean isPlacementPreviewEnabled() { return PLACEMENT.isPlacementPreviewEnabled(); }
    public static double getPlacementTransparency() { return PLACEMENT.getPlacementTransparency(); }
    public static boolean isClickPositionPlacementEnabled() { return PLACEMENT.isClickPositionPlacementEnabled(); }
    public static boolean isStaircaseExclusionEnabled() { return CULLING.isStaircaseExclusionEnabled(); }
    public static int getStaircaseExclusionHeight() { return CULLING.getStaircaseExclusionHeight(); }
    public static boolean isStaircaseOccludeEnabled() { return CULLING.isStaircaseOccludeEnabled(); }
    public static double getStaircaseOccludeAlpha() { return CULLING.getStaircaseOccludeAlpha(); }
    public static boolean isLadderOccludeEnabled() { return CULLING.isLadderOccludeEnabled(); }
    public static double getLadderOccludeAlpha() { return CULLING.getLadderOccludeAlpha(); }
    public static boolean isTreeOccludeEnabled() { return CULLING.isTreeOccludeEnabled(); }
    public static double getTreeOccludeAlpha() { return CULLING.getTreeOccludeAlpha(); }
    public static boolean isIgnoreLeavesInRaycast() { return CULLING.isIgnoreLeavesInRaycast(); }
    public static boolean isProtectNaturalTreeLogs() { return CULLING.isProtectNaturalTreeLogs(); }
    public static boolean isTranslucentFluid() { return CULLING.isTranslucentFluid(); }
    public static double getFluidAlpha() { return CULLING.getFluidAlpha(); }
    public static int getSignHoverDisplayMode() { return INTERACTION.getSignHoverDisplayMode(); }
    public static double getSignHoverScale() { return INTERACTION.getSignHoverScale(); }
    public static boolean isShowInteractionPrompt() { return INTERACTION.isShowInteractionPrompt(); }
    public static double getInteractionPromptScale() { return INTERACTION.getInteractionPromptScale(); }
    public static boolean isInteractionPromptShadow() { return INTERACTION.isInteractionPromptShadow(); }
    public static boolean isShowSpatialPrompt() { return INTERACTION.isShowSpatialPrompt(); }
    public static double getSpatialPromptRadius() { return INTERACTION.getSpatialPromptRadius(); }
    public static boolean isSpatialPromptAllBlocks() { return INTERACTION.isSpatialPromptAllBlocks(); }
    public static boolean isPerformanceMonitorEnabled() { return INTERACTION.isPerformanceMonitorEnabled(); }

    public static double getEffectiveReachDistance() { return INTERACTION.getEffectiveReachDistance(); }
    public static void setSyncedServerReach(double value) { INTERACTION.setSyncedServerReach(value); }
    public static void clearSyncedServerReach() { INTERACTION.clearSyncedServerReach(); }
    public static boolean hasSyncedServerReach() { return INTERACTION.hasSyncedServerReach(); }

    public static void setCylinderRadiusHorizontal(int value) { CULLING.setCylinderRadiusHorizontal(value); }
    public static void setCylinderRadiusVertical(int value) { CULLING.setCylinderRadiusVertical(value); }
    public static void setCylinderForwardShift(int value) { CULLING.setCylinderForwardShift(value); }
    public static void setMiningCylinderRadius(int value) { CULLING.setMiningCylinderRadius(value); }
    public static void setMiningCylinderForwardShift(int value) { CULLING.setMiningCylinderForwardShift(value); }
    public static void setUndergroundCullingEnabled(boolean value) { CULLING.setUndergroundCullingEnabled(value); }
    public static void setUndergroundCullingStartDistance(int value) { CULLING.setUndergroundCullingStartDistance(value); }
    public static void setUndergroundCullingKeepDepth(int value) { CULLING.setUndergroundCullingKeepDepth(value); }
    public static void setMiningModeEnabled(boolean value) { INTERACTION.setMiningModeEnabled(value); }
    public static void setClickToMoveEnabled(boolean value) { INTERACTION.setClickToMoveEnabled(value); }
    public static void setBaritoneRenderPath(boolean value) { INTEGRATIONS.setBaritoneRenderPath(value); }
    public static void setBaritoneRenderGoal(boolean value) { INTEGRATIONS.setBaritoneRenderGoal(value); }
    public static void setArrivalThreshold(double value) { INTERACTION.setArrivalThreshold(value); }
    public static void setForceAutoJump(boolean value) { INTERACTION.setForceAutoJump(value); }
    public static void setSprintDistanceThreshold(double value) { INTERACTION.setSprintDistanceThreshold(value); }
    public static void setAutoAlignToMovementEnabled(boolean value) { INTERACTION.setAutoAlignToMovementEnabled(value); }
    public static void setAutoAlignAngleThreshold(int value) { INTERACTION.setAutoAlignAngleThreshold(value); }
    public static void setAutoAlignCooldownTicks(int value) { INTERACTION.setAutoAlignCooldownTicks(value); }
    public static void setStableDirectionAngle(int value) { INTERACTION.setStableDirectionAngle(value); }
    public static void setStableDirectionTicks(int value) { INTERACTION.setStableDirectionTicks(value); }
    public static void setAutoAlignAnimationSpeed(double value) { INTERACTION.setAutoAlignAnimationSpeed(value); }
    public static void setAutoAlignAnimationAcceleration(boolean value) { INTERACTION.setAutoAlignAnimationAcceleration(value); }
    public static void setMobCullingEnabled(boolean value) { CULLING.setMobCullingEnabled(value); }
    public static void setMobTranslucencyEnabled(boolean value) { CULLING.setMobTranslucencyEnabled(value); }
    public static void setMobTranslucencyAlpha(double value) { CULLING.setMobTranslucencyAlpha(value); }
    public static void setTrapdoorTranslucencyEnabled(boolean value) { CULLING.setTrapdoorTranslucencyEnabled(value); }
    public static void setTrapdoorTransparency(double value) { CULLING.setTrapdoorTransparency(value); }
    public static void setFadeEnabled(boolean value) { CULLING.setFadeEnabled(value); }
    public static void setFadeBlockHitThreshold(double value) { CULLING.setFadeBlockHitThreshold(value); }
    public static void setFadeStart(double value) { CULLING.setFadeStart(value); }
    public static void setFadeNearAlpha(double value) { CULLING.setFadeNearAlpha(value); }
    public static void setFadeSmoothingHalfLife(double value) { CULLING.setFadeSmoothingHalfLife(value); }
    public static void setDisableFadeIndoors(boolean value) { CULLING.setDisableFadeIndoors(value); }
    public static void setViewWedgeHalfAngle(int value) { CULLING.setViewWedgeHalfAngle(value); }
    public static void setCoverCullingRadius(int value) { CULLING.setCoverCullingRadius(value); }
    public static void setCoverCullingViewshedEnabled(boolean value) { CULLING.setCoverCullingViewshedEnabled(value); }
    public static void setCullingMode(int value) { CULLING.setCullingMode(value); }
    public static void setIndoorCeilingCullingEnabled(boolean value) { CULLING.setIndoorCeilingCullingEnabled(value); }
    public static void setPlayerNearTranslucencyEnabled(boolean value) { CULLING.setPlayerNearTranslucencyEnabled(value); }
    public static void setPlayerNearTranslucencyAlpha(double value) { CULLING.setPlayerNearTranslucencyAlpha(value); }
    public static void setPlayerNearTranslucencyRangeHorizontal(int value) { CULLING.setPlayerNearTranslucencyRangeHorizontal(value); }
    public static void setPlayerNearTranslucencyRangeVertical(int value) { CULLING.setPlayerNearTranslucencyRangeVertical(value); }
    public static void setPlayerNearTranslucencyHittable(boolean value) { CULLING.setPlayerNearTranslucencyHittable(value); }
    public static void setRangeIndicatorEnabled(boolean value) { INTERACTION.setRangeIndicatorEnabled(value); }
    public static void setDestinationHighlightEnabled(boolean value) { INTERACTION.setDestinationHighlightEnabled(value); }
    public static void setRangeEmptyHand(double value) { INTERACTION.setRangeEmptyHand(value); }
    public static void setRangeSword(double value) { INTERACTION.setRangeSword(value); }
    public static void setRangeAxe(double value) { INTERACTION.setRangeAxe(value); }
    public static void setRangePickaxe(double value) { INTERACTION.setRangePickaxe(value); }
    public static void setRangeShovel(double value) { INTERACTION.setRangeShovel(value); }
    public static void setRangeOther(double value) { INTERACTION.setRangeOther(value); }
    public static void setDefaultEnabled(boolean value) { INTERACTION.setDefaultEnabled(value); }
    public static void setTargetGlowEnabled(boolean value) { INTERACTION.setTargetGlowEnabled(value); }
    public static void setMobConeCullingEnabled(boolean value) { CULLING.setMobConeCullingEnabled(value); }
    public static void setMobConeHalfAngle(double value) { CULLING.setMobConeHalfAngle(value); }
    public static void setMobConeFadeAngle(double value) { CULLING.setMobConeFadeAngle(value); }
    public static void setMobNearRadius(double value) { CULLING.setMobNearRadius(value); }
    public static void setMobFogEnd(double value) { CULLING.setMobFogEnd(value); }
    public static void setRotateAngleMode(int value) { CAMERA.setRotateAngleMode(value); }
    public static void setCameraSnapRotationSpeed(double value) { CAMERA.setCameraSnapRotationSpeed(value); }
    public static void setCameraPitch(double value) { CAMERA.setCameraPitch(value); }
    public static void setMiningModePitch(double value) { CAMERA.setMiningModePitch(value); }
    public static void setMaxCameraDistance(double value) { CAMERA.setMaxCameraDistance(value); }
    public static void setDefaultCameraDistance(double value) { CAMERA.setDefaultCameraDistance(value); }
    public static void setCameraYFollowDelayEnabled(boolean value) { CAMERA.setCameraYFollowDelayEnabled(value); }
    public static void setCameraYFollowDelay(double value) { CAMERA.setCameraYFollowDelay(value); }
    public static void setCameraXFollowDelayEnabled(boolean value) { CAMERA.setCameraXFollowDelayEnabled(value); }
    public static void setCameraXFollowDelay(double value) { CAMERA.setCameraXFollowDelay(value); }
    public static void setCameraZFollowDelayEnabled(boolean value) { CAMERA.setCameraZFollowDelayEnabled(value); }
    public static void setCameraZFollowDelay(double value) { CAMERA.setCameraZFollowDelay(value); }
    public static void setFollowDelayWhileMounted(boolean value) { CAMERA.setFollowDelayWhileMounted(value); }
    public static void setPlayerScreenOffset(double value) { CAMERA.setPlayerScreenOffset(value); }
    public static void setHeadBodyRotationEnabled(boolean value) { CAMERA.setHeadBodyRotationEnabled(value); }
    public static void setWaterMovementControlEnabled(boolean value) { CAMERA.setWaterMovementControlEnabled(value); }
    public static void setIndependentMountAim(boolean value) { CAMERA.setIndependentMountAim(value); }
    public static void setMountAimMaxTwist(int value) { CAMERA.setMountAimMaxTwist(value); }
    public static void setMountTurnSmoothing(double value) { CAMERA.setMountTurnSmoothing(value); }
    public static void setBoatHeadMaxTwist(int value) { CAMERA.setBoatHeadMaxTwist(value); }
    public static void setBoatBodyMaxTwist(int value) { CAMERA.setBoatBodyMaxTwist(value); }
    public static void setTopDownFov(int value) { CAMERA.setTopDownFov(value); }
    public static void setLockedTopDown(boolean value) { CAMERA.setLockedTopDown(value); }
    public static void setScrollOnlyZoomEnabled(boolean value) { CAMERA.setScrollOnlyZoomEnabled(value); }
    public static void setCameraZoomSmoothingEnabled(boolean value) { CAMERA.setCameraZoomSmoothingEnabled(value); }
    public static void setCameraZoomSmoothing(double value) { CAMERA.setCameraZoomSmoothing(value); }
    public static void setMousePanEnabled(boolean value) { CAMERA.setMousePanEnabled(value); }
    public static void setMousePanMaxDistance(double value) { CAMERA.setMousePanMaxDistance(value); }
    public static void setMousePanSmoothing(double value) { CAMERA.setMousePanSmoothing(value); }
    public static void setTargetLockEnabled(boolean value) { INTERACTION.setTargetLockEnabled(value); }
    public static void setTargetLockDuration(int value) { INTERACTION.setTargetLockDuration(value); }
    public static void setTargetHitboxExpansion(double value) { INTERACTION.setTargetHitboxExpansion(value); }
    public static void setScreenReachEnabled(boolean value) { INTERACTION.setScreenReachEnabled(value); }
    public static void setReachDistance(double value) { INTERACTION.setReachDistance(value); }
    public static void setPlacementPreviewEnabled(boolean value) { PLACEMENT.setPlacementPreviewEnabled(value); }
    public static void setPlacementTransparency(double value) { PLACEMENT.setPlacementTransparency(value); }
    public static void setClickPositionPlacementEnabled(boolean value) { PLACEMENT.setClickPositionPlacementEnabled(value); }
    public static void setStaircaseExclusionEnabled(boolean value) { CULLING.setStaircaseExclusionEnabled(value); }
    public static void setStaircaseExclusionHeight(int value) { CULLING.setStaircaseExclusionHeight(value); }
    public static void setStaircaseOccludeEnabled(boolean value) { CULLING.setStaircaseOccludeEnabled(value); }
    public static void setStaircaseOccludeAlpha(double value) { CULLING.setStaircaseOccludeAlpha(value); }
    public static void setLadderOccludeEnabled(boolean value) { CULLING.setLadderOccludeEnabled(value); }
    public static void setLadderOccludeAlpha(double value) { CULLING.setLadderOccludeAlpha(value); }
    public static void setTreeOccludeEnabled(boolean value) { CULLING.setTreeOccludeEnabled(value); }
    public static void setTreeOccludeAlpha(double value) { CULLING.setTreeOccludeAlpha(value); }
    public static void setIgnoreLeavesInRaycast(boolean value) { CULLING.setIgnoreLeavesInRaycast(value); }
    public static void setProtectNaturalTreeLogs(boolean value) { CULLING.setProtectNaturalTreeLogs(value); }
    public static void setTranslucentFluid(boolean value) { CULLING.setTranslucentFluid(value); }
    public static void setFluidAlpha(double value) { CULLING.setFluidAlpha(value); }
    public static void setSignHoverDisplayMode(int value) { INTERACTION.setSignHoverDisplayMode(value); }
    public static void setSignHoverScale(double value) { INTERACTION.setSignHoverScale(value); }
    public static void setShowInteractionPrompt(boolean value) { INTERACTION.setShowInteractionPrompt(value); }
    public static void setInteractionPromptScale(double value) { INTERACTION.setInteractionPromptScale(value); }
    public static void setInteractionPromptShadow(boolean value) { INTERACTION.setInteractionPromptShadow(value); }
    public static void setShowSpatialPrompt(boolean value) { INTERACTION.setShowSpatialPrompt(value); }
    public static void setSpatialPromptRadius(double value) { INTERACTION.setSpatialPromptRadius(value); }
    public static void setSpatialPromptAllBlocks(boolean value) { INTERACTION.setSpatialPromptAllBlocks(value); }
    public static void setPerformanceMonitorEnabled(boolean value) { INTERACTION.setPerformanceMonitorEnabled(value); }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadClientConfig();
            com.topdownview.state.ModState.STATUS.setEnabled(INTERACTION.isDefaultEnabled());
            notifyConfigChanged();
        } else if (event.getConfig().getSpec() == COMMON_SPEC) {
            loadCommonConfig();
        }
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        if (isSaving) {
            return;
        }
        if (event.getConfig().getSpec() == SPEC) {
            loadClientConfig();
            com.topdownview.state.ModState.STATUS.setEnabled(INTERACTION.isDefaultEnabled());
            notifyConfigChanged();
        } else if (event.getConfig().getSpec() == COMMON_SPEC) {
            loadCommonConfig();
        }
    }

    private interface Binding {
        void load();

        void save();

        void reset();
    }

    private record SpecBinding<T>(ForgeConfigSpec.ConfigValue<T> spec,
                                  Consumer<T> setter, Supplier<T> getter) implements Binding {
        @Override
        public void load() {
            setter.accept(spec.get());
        }

        @Override
        public void save() {
            spec.set(getter.get());
        }

        @Override
        public void reset() {
            setter.accept(spec.getDefault());
        }
    }

    // スペックの読み込み・保存・既定値復元を一箇所で定義する単一情報源。
    private static final List<Binding> BINDINGS = new ArrayList<>();

    private static <T> void addBinding(ForgeConfigSpec.ConfigValue<T> spec,
                                       Consumer<T> setter, Supplier<T> getter) {
        BINDINGS.add(new SpecBinding<>(spec, setter, getter));
    }

    static {
        addBinding(CYLINDER_RADIUS_HORIZONTAL, CULLING::setCylinderRadiusHorizontal, Config::getCylinderRadiusHorizontal);
        addBinding(CYLINDER_RADIUS_VERTICAL, CULLING::setCylinderRadiusVertical, Config::getCylinderRadiusVertical);
        addBinding(CYLINDER_FORWARD_SHIFT, CULLING::setCylinderForwardShift, Config::getCylinderForwardShift);
        addBinding(MINING_CYLINDER_RADIUS, CULLING::setMiningCylinderRadius, Config::getMiningCylinderRadius);
        addBinding(MINING_CYLINDER_FORWARD_SHIFT, CULLING::setMiningCylinderForwardShift, Config::getMiningCylinderForwardShift);
        addBinding(UNDERGROUND_CULLING_ENABLED, CULLING::setUndergroundCullingEnabled, Config::isUndergroundCullingEnabled);
        addBinding(UNDERGROUND_CULLING_START_DISTANCE, CULLING::setUndergroundCullingStartDistance, Config::getUndergroundCullingStartDistance);
        addBinding(UNDERGROUND_CULLING_KEEP_DEPTH, CULLING::setUndergroundCullingKeepDepth, Config::getUndergroundCullingKeepDepth);
        addBinding(MINING_MODE_ENABLED, INTERACTION::setMiningModeEnabled, Config::isMiningModeEnabled);
        addBinding(CLICK_TO_MOVE_ENABLED, INTERACTION::setClickToMoveEnabled, Config::isClickToMoveEnabled);
        addBinding(BARITONE_RENDER_PATH, INTEGRATIONS::setBaritoneRenderPath, Config::isBaritoneRenderPath);
        addBinding(BARITONE_RENDER_GOAL, INTEGRATIONS::setBaritoneRenderGoal, Config::isBaritoneRenderGoal);
        addBinding(ARRIVAL_THRESHOLD, INTERACTION::setArrivalThreshold, Config::getArrivalThreshold);
        addBinding(FORCE_AUTO_JUMP, INTERACTION::setForceAutoJump, Config::isForceAutoJump);
        addBinding(SPRINT_DISTANCE_THRESHOLD, INTERACTION::setSprintDistanceThreshold, Config::getSprintDistanceThreshold);
        addBinding(AUTO_ALIGN_TO_MOVEMENT_ENABLED, INTERACTION::setAutoAlignToMovementEnabled, Config::isAutoAlignToMovementEnabled);
        addBinding(AUTO_ALIGN_ANGLE_THRESHOLD, INTERACTION::setAutoAlignAngleThreshold, Config::getAutoAlignAngleThreshold);
        addBinding(AUTO_ALIGN_COOLDOWN_TICKS, INTERACTION::setAutoAlignCooldownTicks, Config::getAutoAlignCooldownTicks);
        addBinding(STABLE_DIRECTION_ANGLE, INTERACTION::setStableDirectionAngle, Config::getStableDirectionAngle);
        addBinding(STABLE_DIRECTION_TICKS, INTERACTION::setStableDirectionTicks, Config::getStableDirectionTicks);
        addBinding(AUTO_ALIGN_ANIMATION_SPEED, INTERACTION::setAutoAlignAnimationSpeed, Config::getAutoAlignAnimationSpeed);
        addBinding(AUTO_ALIGN_ANIMATION_ACCELERATION, INTERACTION::setAutoAlignAnimationAcceleration, Config::isAutoAlignAnimationAcceleration);
        addBinding(MOB_CULLING_ENABLED, CULLING::setMobCullingEnabled, Config::isMobCullingEnabled);
        addBinding(MOB_TRANSLUCENCY_ENABLED, CULLING::setMobTranslucencyEnabled, Config::isMobTranslucencyEnabled);
        addBinding(MOB_TRANSLUCENCY_ALPHA, CULLING::setMobTranslucencyAlpha, Config::getMobTranslucencyAlpha);
        addBinding(TRAPDOOR_TRANSLUCENCY_ENABLED, CULLING::setTrapdoorTranslucencyEnabled, Config::isTrapdoorTranslucencyEnabled);
        addBinding(TRAPDOOR_TRANSPARENCY, CULLING::setTrapdoorTransparency, Config::getTrapdoorTransparency);
        addBinding(FADE_ENABLED, CULLING::setFadeEnabled, Config::isFadeEnabled);
        addBinding(FADE_BLOCK_HIT_THRESHOLD, CULLING::setFadeBlockHitThreshold, Config::getFadeBlockHitThreshold);
        addBinding(FADE_START, CULLING::setFadeStart, Config::getFadeStart);
        addBinding(FADE_NEAR_ALPHA, CULLING::setFadeNearAlpha, Config::getFadeNearAlpha);
        addBinding(FADE_SMOOTHING_HALF_LIFE, CULLING::setFadeSmoothingHalfLife, Config::getFadeSmoothingHalfLife);
        addBinding(DISABLE_FADE_INDOORS, CULLING::setDisableFadeIndoors, Config::isDisableFadeIndoors);
        addBinding(VIEW_WEDGE_HALF_ANGLE, CULLING::setViewWedgeHalfAngle, Config::getViewWedgeHalfAngle);
        addBinding(COVER_CULLING_RADIUS, CULLING::setCoverCullingRadius, Config::getCoverCullingRadius);
        addBinding(COVER_CULLING_VIEWSHED_ENABLED, CULLING::setCoverCullingViewshedEnabled, Config::isCoverCullingViewshedEnabled);
        addBinding(CULLING_MODE, CULLING::setCullingMode, Config::getCullingMode);
        addBinding(INDOOR_CEILING_CULLING_ENABLED, CULLING::setIndoorCeilingCullingEnabled, Config::isIndoorCeilingCullingEnabled);
        addBinding(PLAYER_NEAR_TRANSLUCENCY_ENABLED, CULLING::setPlayerNearTranslucencyEnabled, Config::isPlayerNearTranslucencyEnabled);
        addBinding(PLAYER_NEAR_TRANSLUCENCY_ALPHA, CULLING::setPlayerNearTranslucencyAlpha, Config::getPlayerNearTranslucencyAlpha);
        addBinding(PLAYER_NEAR_TRANSLUCENCY_RANGE_HORIZONTAL, CULLING::setPlayerNearTranslucencyRangeHorizontal, Config::getPlayerNearTranslucencyRangeHorizontal);
        addBinding(PLAYER_NEAR_TRANSLUCENCY_RANGE_VERTICAL, CULLING::setPlayerNearTranslucencyRangeVertical, Config::getPlayerNearTranslucencyRangeVertical);
        addBinding(PLAYER_NEAR_TRANSLUCENCY_HITTABLE, CULLING::setPlayerNearTranslucencyHittable, Config::isPlayerNearTranslucencyHittable);
        addBinding(RANGE_INDICATOR_ENABLED, INTERACTION::setRangeIndicatorEnabled, Config::isRangeIndicatorEnabled);
        addBinding(DESTINATION_HIGHLIGHT_ENABLED, INTERACTION::setDestinationHighlightEnabled, Config::isDestinationHighlightEnabled);
        addBinding(RANGE_EMPTY_HAND, INTERACTION::setRangeEmptyHand, Config::getRangeEmptyHand);
        addBinding(RANGE_SWORD, INTERACTION::setRangeSword, Config::getRangeSword);
        addBinding(RANGE_AXE, INTERACTION::setRangeAxe, Config::getRangeAxe);
        addBinding(RANGE_PICKAXE, INTERACTION::setRangePickaxe, Config::getRangePickaxe);
        addBinding(RANGE_SHOVEL, INTERACTION::setRangeShovel, Config::getRangeShovel);
        addBinding(RANGE_OTHER, INTERACTION::setRangeOther, Config::getRangeOther);
        addBinding(DEFAULT_ENABLED, INTERACTION::setDefaultEnabled, Config::isDefaultEnabled);
        addBinding(TARGET_GLOW_ENABLED, INTERACTION::setTargetGlowEnabled, Config::isTargetGlowEnabled);
        addBinding(MOB_CONE_CULLING_ENABLED, CULLING::setMobConeCullingEnabled, Config::isMobConeCullingEnabled);
        addBinding(MOB_CONE_HALF_ANGLE, CULLING::setMobConeHalfAngle, Config::getMobConeHalfAngle);
        addBinding(MOB_CONE_FADE_ANGLE, CULLING::setMobConeFadeAngle, Config::getMobConeFadeAngle);
        addBinding(MOB_NEAR_RADIUS, CULLING::setMobNearRadius, Config::getMobNearRadius);
        addBinding(MOB_FOG_END, CULLING::setMobFogEnd, Config::getMobFogEnd);
        addBinding(ROTATE_ANGLE_MODE, CAMERA::setRotateAngleMode, Config::getRotateAngleMode);
        addBinding(CAMERA_SNAP_ROTATION_SPEED, CAMERA::setCameraSnapRotationSpeed, Config::getCameraSnapRotationSpeed);
        addBinding(CAMERA_PITCH, CAMERA::setCameraPitch, Config::getCameraPitch);
        addBinding(MINING_MODE_PITCH, CAMERA::setMiningModePitch, Config::getMiningModePitch);
        addBinding(MAX_CAMERA_DISTANCE, CAMERA::setMaxCameraDistance, Config::getMaxCameraDistance);
        addBinding(DEFAULT_CAMERA_DISTANCE, CAMERA::setDefaultCameraDistance, Config::getDefaultCameraDistance);
        addBinding(CAMERA_Y_FOLLOW_DELAY_ENABLED, CAMERA::setCameraYFollowDelayEnabled, Config::isCameraYFollowDelayEnabled);
        addBinding(CAMERA_Y_FOLLOW_DELAY, CAMERA::setCameraYFollowDelay, Config::getCameraYFollowDelay);
        addBinding(CAMERA_X_FOLLOW_DELAY_ENABLED, CAMERA::setCameraXFollowDelayEnabled, Config::isCameraXFollowDelayEnabled);
        addBinding(CAMERA_X_FOLLOW_DELAY, CAMERA::setCameraXFollowDelay, Config::getCameraXFollowDelay);
        addBinding(CAMERA_Z_FOLLOW_DELAY_ENABLED, CAMERA::setCameraZFollowDelayEnabled, Config::isCameraZFollowDelayEnabled);
        addBinding(CAMERA_Z_FOLLOW_DELAY, CAMERA::setCameraZFollowDelay, Config::getCameraZFollowDelay);
        addBinding(FOLLOW_DELAY_WHILE_MOUNTED, CAMERA::setFollowDelayWhileMounted, Config::isFollowDelayWhileMounted);
        addBinding(PLAYER_SCREEN_OFFSET, CAMERA::setPlayerScreenOffset, Config::getPlayerScreenOffset);
        addBinding(HEAD_BODY_ROTATION_ENABLED, CAMERA::setHeadBodyRotationEnabled, Config::isHeadBodyRotationEnabled);
        addBinding(WATER_MOVEMENT_CONTROL_ENABLED, CAMERA::setWaterMovementControlEnabled, Config::isWaterMovementControlEnabled);
        addBinding(INDEPENDENT_MOUNT_AIM, CAMERA::setIndependentMountAim, Config::isIndependentMountAim);
        addBinding(MOUNT_AIM_MAX_TWIST, CAMERA::setMountAimMaxTwist, Config::getMountAimMaxTwist);
        addBinding(MOUNT_TURN_SMOOTHING, CAMERA::setMountTurnSmoothing, Config::getMountTurnSmoothing);
        addBinding(BOAT_HEAD_MAX_TWIST, CAMERA::setBoatHeadMaxTwist, Config::getBoatHeadMaxTwist);
        addBinding(BOAT_BODY_MAX_TWIST, CAMERA::setBoatBodyMaxTwist, Config::getBoatBodyMaxTwist);
        addBinding(TOP_DOWN_FOV, CAMERA::setTopDownFov, Config::getTopDownFov);
        addBinding(LOCKED_TOP_DOWN, CAMERA::setLockedTopDown, Config::isLockedTopDown);
        addBinding(SCROLL_ONLY_ZOOM_ENABLED, CAMERA::setScrollOnlyZoomEnabled, Config::isScrollOnlyZoomEnabled);
        addBinding(CAMERA_ZOOM_SMOOTHING_ENABLED, CAMERA::setCameraZoomSmoothingEnabled, Config::isCameraZoomSmoothingEnabled);
        addBinding(CAMERA_ZOOM_SMOOTHING, CAMERA::setCameraZoomSmoothing, Config::getCameraZoomSmoothing);
        addBinding(MOUSE_PAN_ENABLED, CAMERA::setMousePanEnabled, Config::isMousePanEnabled);
        addBinding(MOUSE_PAN_MAX_DISTANCE, CAMERA::setMousePanMaxDistance, Config::getMousePanMaxDistance);
        addBinding(MOUSE_PAN_SMOOTHING, CAMERA::setMousePanSmoothing, Config::getMousePanSmoothing);
        addBinding(TARGET_LOCK_ENABLED, INTERACTION::setTargetLockEnabled, Config::isTargetLockEnabled);
        addBinding(TARGET_LOCK_DURATION, INTERACTION::setTargetLockDuration, Config::getTargetLockDuration);
        addBinding(TARGET_HITBOX_EXPANSION, INTERACTION::setTargetHitboxExpansion, Config::getTargetHitboxExpansion);
        addBinding(SCREEN_REACH_ENABLED, INTERACTION::setScreenReachEnabled, Config::isScreenReachEnabled);
        addBinding(REACH_DISTANCE, INTERACTION::setReachDistance, Config::getReachDistance);
        addBinding(PLACEMENT_PREVIEW_ENABLED, PLACEMENT::setPlacementPreviewEnabled, Config::isPlacementPreviewEnabled);
        addBinding(PLACEMENT_TRANSPARENCY, PLACEMENT::setPlacementTransparency, Config::getPlacementTransparency);
        addBinding(CLICK_POSITION_PLACEMENT_ENABLED, PLACEMENT::setClickPositionPlacementEnabled, Config::isClickPositionPlacementEnabled);
        addBinding(STAIRCASE_EXCLUSION_ENABLED, CULLING::setStaircaseExclusionEnabled, Config::isStaircaseExclusionEnabled);
        addBinding(STAIRCASE_EXCLUSION_HEIGHT, CULLING::setStaircaseExclusionHeight, Config::getStaircaseExclusionHeight);
        addBinding(STAIRCASE_OCCLUDE_ENABLED, CULLING::setStaircaseOccludeEnabled, Config::isStaircaseOccludeEnabled);
        addBinding(STAIRCASE_OCCLUDE_ALPHA, CULLING::setStaircaseOccludeAlpha, Config::getStaircaseOccludeAlpha);
        addBinding(LADDER_OCCLUDE_ENABLED, CULLING::setLadderOccludeEnabled, Config::isLadderOccludeEnabled);
        addBinding(LADDER_OCCLUDE_ALPHA, CULLING::setLadderOccludeAlpha, Config::getLadderOccludeAlpha);
        addBinding(TREE_OCCLUDE_ENABLED, CULLING::setTreeOccludeEnabled, Config::isTreeOccludeEnabled);
        addBinding(TREE_OCCLUDE_ALPHA, CULLING::setTreeOccludeAlpha, Config::getTreeOccludeAlpha);
        addBinding(IGNORE_LEAVES_IN_RAYCAST, CULLING::setIgnoreLeavesInRaycast, Config::isIgnoreLeavesInRaycast);
        addBinding(PROTECT_NATURAL_TREE_LOGS, CULLING::setProtectNaturalTreeLogs, Config::isProtectNaturalTreeLogs);
        addBinding(TRANSLUCENT_FLUID, CULLING::setTranslucentFluid, Config::isTranslucentFluid);
        addBinding(FLUID_ALPHA, CULLING::setFluidAlpha, Config::getFluidAlpha);
        addBinding(SIGN_HOVER_DISPLAY_MODE, INTERACTION::setSignHoverDisplayMode, Config::getSignHoverDisplayMode);
        addBinding(SIGN_HOVER_SCALE, INTERACTION::setSignHoverScale, Config::getSignHoverScale);
        addBinding(SHOW_INTERACTION_PROMPT, INTERACTION::setShowInteractionPrompt, Config::isShowInteractionPrompt);
        addBinding(INTERACTION_PROMPT_SCALE, INTERACTION::setInteractionPromptScale, Config::getInteractionPromptScale);
        addBinding(INTERACTION_PROMPT_SHADOW, INTERACTION::setInteractionPromptShadow, Config::isInteractionPromptShadow);
        addBinding(SHOW_SPATIAL_PROMPT, INTERACTION::setShowSpatialPrompt, Config::isShowSpatialPrompt);
        addBinding(SPATIAL_PROMPT_RADIUS, INTERACTION::setSpatialPromptRadius, Config::getSpatialPromptRadius);
        addBinding(SPATIAL_PROMPT_ALL_BLOCKS, INTERACTION::setSpatialPromptAllBlocks, Config::isSpatialPromptAllBlocks);
        addBinding(PERFORMANCE_MONITOR_ENABLED, INTERACTION::setPerformanceMonitorEnabled, Config::isPerformanceMonitorEnabled);
    }

    private static void loadClientConfig() {
        BINDINGS.forEach(Binding::load);
    }

    private static void loadCommonConfig() {
        INTERACTION.setServerReachDistance(SERVER_REACH_DISTANCE.get());
    }

    public static void save() {
        isSaving = true;
        try {
            BINDINGS.forEach(Binding::save);
            SPEC.save();
        } finally {
            isSaving = false;
        }
        notifyConfigChanged();
    }

    public static void resetToDefaults() {
        BINDINGS.forEach(Binding::reset);
    }
}