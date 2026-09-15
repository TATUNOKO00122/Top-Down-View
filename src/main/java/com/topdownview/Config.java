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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
            .defineInRange("autoAlignAnimationSpeed", 0.1, 0.05, 0.5);
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
            .comment("0 = Cylinder (legacy), 1 = Cover + Corridor (new), 2 = Cover only.")
            .defineInRange("cullingMode", 1, 0, 2);
    private static final ForgeConfigSpec.IntValue VIEW_WEDGE_HALF_ANGLE = BUILDER
            .defineInRange("viewWedgeHalfAngle", 60, 10, 90);
    private static final ForgeConfigSpec.IntValue COVER_CULLING_RADIUS = BUILDER
            .defineInRange("coverCullingRadius", 10, 4, 24);
    private static final ForgeConfigSpec.BooleanValue COVER_CULLING_VIEWSHED_ENABLED = BUILDER
            .comment("Culls covers only over ground actually visible from the player's eye.")
            .define("coverCullingViewshedEnabled", true);
    private static final ForgeConfigSpec.BooleanValue PLAYER_NEAR_TRANSLUCENCY_ENABLED = BUILDER
            .define("playerNearTranslucencyEnabled", true);
    private static final ForgeConfigSpec.DoubleValue PLAYER_NEAR_TRANSLUCENCY_ALPHA = BUILDER
            .defineInRange("playerNearTranslucencyAlpha", 0.4, 0.0, 1.0);
    private static final ForgeConfigSpec.IntValue PLAYER_NEAR_TRANSLUCENCY_RANGE_HORIZONTAL = BUILDER
            .defineInRange("playerNearTranslucencyRangeHorizontal", 1, 0, 5);
    private static final ForgeConfigSpec.IntValue PLAYER_NEAR_TRANSLUCENCY_RANGE_VERTICAL = BUILDER
            .defineInRange("playerNearTranslucencyRangeVertical", 1, 1, 5);
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
            .defineInRange("topDownFov", 30, 30, 110);
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
            .defineInRange("mousePanSmoothing", 0.1, 0.01, 0.2);
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
    public static int getViewWedgeHalfAngle() { return CULLING.getViewWedgeHalfAngle(); }
    public static int getCoverCullingRadius() { return CULLING.getCoverCullingRadius(); }
    public static boolean isCoverCullingViewshedEnabled() { return CULLING.isCoverCullingViewshedEnabled(); }
    public static int getCullingMode() { return CULLING.getCullingMode(); }
    public static boolean isPlayerNearTranslucencyEnabled() { return CULLING.isPlayerNearTranslucencyEnabled(); }
    public static double getPlayerNearTranslucencyAlpha() { return CULLING.getPlayerNearTranslucencyAlpha(); }
    public static int getPlayerNearTranslucencyRangeHorizontal() { return CULLING.getPlayerNearTranslucencyRangeHorizontal(); }
    public static int getPlayerNearTranslucencyRangeVertical() { return CULLING.getPlayerNearTranslucencyRangeVertical(); }
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
    public static void setViewWedgeHalfAngle(int value) { CULLING.setViewWedgeHalfAngle(value); }
    public static void setCoverCullingRadius(int value) { CULLING.setCoverCullingRadius(value); }
    public static void setCoverCullingViewshedEnabled(boolean value) { CULLING.setCoverCullingViewshedEnabled(value); }
    public static void setCullingMode(int value) { CULLING.setCullingMode(value); }
    public static void setPlayerNearTranslucencyEnabled(boolean value) { CULLING.setPlayerNearTranslucencyEnabled(value); }
    public static void setPlayerNearTranslucencyAlpha(double value) { CULLING.setPlayerNearTranslucencyAlpha(value); }
    public static void setPlayerNearTranslucencyRangeHorizontal(int value) { CULLING.setPlayerNearTranslucencyRangeHorizontal(value); }
    public static void setPlayerNearTranslucencyRangeVertical(int value) { CULLING.setPlayerNearTranslucencyRangeVertical(value); }
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

    private static void loadClientConfig() {
        CULLING.setCylinderRadiusHorizontal(CYLINDER_RADIUS_HORIZONTAL.get());
        CULLING.setCylinderRadiusVertical(CYLINDER_RADIUS_VERTICAL.get());
        CULLING.setCylinderForwardShift(CYLINDER_FORWARD_SHIFT.get());
        CULLING.setMiningCylinderRadius(MINING_CYLINDER_RADIUS.get());
        CULLING.setMiningCylinderForwardShift(MINING_CYLINDER_FORWARD_SHIFT.get());
        CULLING.setUndergroundCullingEnabled(UNDERGROUND_CULLING_ENABLED.get());
        CULLING.setUndergroundCullingStartDistance(UNDERGROUND_CULLING_START_DISTANCE.get());
        CULLING.setUndergroundCullingKeepDepth(UNDERGROUND_CULLING_KEEP_DEPTH.get());
        INTERACTION.setMiningModeEnabled(MINING_MODE_ENABLED.get());
        INTERACTION.setClickToMoveEnabled(CLICK_TO_MOVE_ENABLED.get());
        INTEGRATIONS.setBaritoneRenderPath(BARITONE_RENDER_PATH.get());
        INTEGRATIONS.setBaritoneRenderGoal(BARITONE_RENDER_GOAL.get());
        INTERACTION.setArrivalThreshold(ARRIVAL_THRESHOLD.get());
        INTERACTION.setForceAutoJump(FORCE_AUTO_JUMP.get());
        INTERACTION.setSprintDistanceThreshold(SPRINT_DISTANCE_THRESHOLD.get());
        INTERACTION.setAutoAlignToMovementEnabled(AUTO_ALIGN_TO_MOVEMENT_ENABLED.get());
        INTERACTION.setAutoAlignAngleThreshold(AUTO_ALIGN_ANGLE_THRESHOLD.get());
        INTERACTION.setAutoAlignCooldownTicks(AUTO_ALIGN_COOLDOWN_TICKS.get());
        INTERACTION.setStableDirectionAngle(STABLE_DIRECTION_ANGLE.get());
        INTERACTION.setStableDirectionTicks(STABLE_DIRECTION_TICKS.get());
        INTERACTION.setAutoAlignAnimationSpeed(AUTO_ALIGN_ANIMATION_SPEED.get());
        CULLING.setMobCullingEnabled(MOB_CULLING_ENABLED.get());
        CULLING.setMobTranslucencyEnabled(MOB_TRANSLUCENCY_ENABLED.get());
        CULLING.setMobTranslucencyAlpha(MOB_TRANSLUCENCY_ALPHA.get());
        CULLING.setTrapdoorTranslucencyEnabled(TRAPDOOR_TRANSLUCENCY_ENABLED.get());
        CULLING.setTrapdoorTransparency(TRAPDOOR_TRANSPARENCY.get());
        CULLING.setFadeEnabled(FADE_ENABLED.get());
        CULLING.setFadeBlockHitThreshold(FADE_BLOCK_HIT_THRESHOLD.get());
        CULLING.setFadeStart(FADE_START.get());
        CULLING.setFadeNearAlpha(FADE_NEAR_ALPHA.get());
        CULLING.setFadeSmoothingHalfLife(FADE_SMOOTHING_HALF_LIFE.get());
        CULLING.setViewWedgeHalfAngle(VIEW_WEDGE_HALF_ANGLE.get());
        CULLING.setCoverCullingRadius(COVER_CULLING_RADIUS.get());
        CULLING.setCoverCullingViewshedEnabled(COVER_CULLING_VIEWSHED_ENABLED.get());
        CULLING.setCullingMode(CULLING_MODE.get());
        CULLING.setPlayerNearTranslucencyEnabled(PLAYER_NEAR_TRANSLUCENCY_ENABLED.get());
        CULLING.setPlayerNearTranslucencyAlpha(PLAYER_NEAR_TRANSLUCENCY_ALPHA.get());
        CULLING.setPlayerNearTranslucencyRangeHorizontal(PLAYER_NEAR_TRANSLUCENCY_RANGE_HORIZONTAL.get());
        CULLING.setPlayerNearTranslucencyRangeVertical(PLAYER_NEAR_TRANSLUCENCY_RANGE_VERTICAL.get());
        INTERACTION.setRangeIndicatorEnabled(RANGE_INDICATOR_ENABLED.get());
        INTERACTION.setDestinationHighlightEnabled(DESTINATION_HIGHLIGHT_ENABLED.get());
        INTERACTION.setRangeEmptyHand(RANGE_EMPTY_HAND.get());
        INTERACTION.setRangeSword(RANGE_SWORD.get());
        INTERACTION.setRangeAxe(RANGE_AXE.get());
        INTERACTION.setRangePickaxe(RANGE_PICKAXE.get());
        INTERACTION.setRangeShovel(RANGE_SHOVEL.get());
        INTERACTION.setRangeOther(RANGE_OTHER.get());
        INTERACTION.setDefaultEnabled(DEFAULT_ENABLED.get());
        INTERACTION.setTargetGlowEnabled(TARGET_GLOW_ENABLED.get());
        CULLING.setMobConeCullingEnabled(MOB_CONE_CULLING_ENABLED.get());
        CULLING.setMobConeHalfAngle(MOB_CONE_HALF_ANGLE.get());
        CULLING.setMobConeFadeAngle(MOB_CONE_FADE_ANGLE.get());
        CULLING.setMobNearRadius(MOB_NEAR_RADIUS.get());
        CULLING.setMobFogEnd(MOB_FOG_END.get());
        CAMERA.setRotateAngleMode(ROTATE_ANGLE_MODE.get());
        CAMERA.setCameraSnapRotationSpeed(CAMERA_SNAP_ROTATION_SPEED.get());
        CAMERA.setCameraPitch(CAMERA_PITCH.get());
        CAMERA.setMiningModePitch(MINING_MODE_PITCH.get());
        CAMERA.setMaxCameraDistance(MAX_CAMERA_DISTANCE.get());
        CAMERA.setDefaultCameraDistance(DEFAULT_CAMERA_DISTANCE.get());
        CAMERA.setCameraYFollowDelayEnabled(CAMERA_Y_FOLLOW_DELAY_ENABLED.get());
        CAMERA.setCameraYFollowDelay(CAMERA_Y_FOLLOW_DELAY.get());
        CAMERA.setCameraXFollowDelayEnabled(CAMERA_X_FOLLOW_DELAY_ENABLED.get());
        CAMERA.setCameraXFollowDelay(CAMERA_X_FOLLOW_DELAY.get());
        CAMERA.setCameraZFollowDelayEnabled(CAMERA_Z_FOLLOW_DELAY_ENABLED.get());
        CAMERA.setCameraZFollowDelay(CAMERA_Z_FOLLOW_DELAY.get());
        CAMERA.setFollowDelayWhileMounted(FOLLOW_DELAY_WHILE_MOUNTED.get());
        CAMERA.setPlayerScreenOffset(PLAYER_SCREEN_OFFSET.get());
        CAMERA.setHeadBodyRotationEnabled(HEAD_BODY_ROTATION_ENABLED.get());
        CAMERA.setWaterMovementControlEnabled(WATER_MOVEMENT_CONTROL_ENABLED.get());
        CAMERA.setIndependentMountAim(INDEPENDENT_MOUNT_AIM.get());
        CAMERA.setMountAimMaxTwist(MOUNT_AIM_MAX_TWIST.get());
        CAMERA.setMountTurnSmoothing(MOUNT_TURN_SMOOTHING.get());
        CAMERA.setBoatHeadMaxTwist(BOAT_HEAD_MAX_TWIST.get());
        CAMERA.setBoatBodyMaxTwist(BOAT_BODY_MAX_TWIST.get());
        CAMERA.setTopDownFov(TOP_DOWN_FOV.get());
        CAMERA.setLockedTopDown(LOCKED_TOP_DOWN.get());
        CAMERA.setScrollOnlyZoomEnabled(SCROLL_ONLY_ZOOM_ENABLED.get());
        CAMERA.setCameraZoomSmoothingEnabled(CAMERA_ZOOM_SMOOTHING_ENABLED.get());
        CAMERA.setCameraZoomSmoothing(CAMERA_ZOOM_SMOOTHING.get());
        CAMERA.setMousePanEnabled(MOUSE_PAN_ENABLED.get());
        CAMERA.setMousePanMaxDistance(MOUSE_PAN_MAX_DISTANCE.get());
        CAMERA.setMousePanSmoothing(MOUSE_PAN_SMOOTHING.get());
        INTERACTION.setTargetLockEnabled(TARGET_LOCK_ENABLED.get());
        INTERACTION.setTargetLockDuration(TARGET_LOCK_DURATION.get());
        INTERACTION.setTargetHitboxExpansion(TARGET_HITBOX_EXPANSION.get());
        INTERACTION.setScreenReachEnabled(SCREEN_REACH_ENABLED.get());
        INTERACTION.setReachDistance(REACH_DISTANCE.get());
        PLACEMENT.setPlacementPreviewEnabled(PLACEMENT_PREVIEW_ENABLED.get());
        PLACEMENT.setPlacementTransparency(PLACEMENT_TRANSPARENCY.get());
        PLACEMENT.setClickPositionPlacementEnabled(CLICK_POSITION_PLACEMENT_ENABLED.get());
        CULLING.setStaircaseExclusionEnabled(STAIRCASE_EXCLUSION_ENABLED.get());
        CULLING.setStaircaseExclusionHeight(STAIRCASE_EXCLUSION_HEIGHT.get());
        CULLING.setStaircaseOccludeEnabled(STAIRCASE_OCCLUDE_ENABLED.get());
        CULLING.setStaircaseOccludeAlpha(STAIRCASE_OCCLUDE_ALPHA.get());
        CULLING.setLadderOccludeEnabled(LADDER_OCCLUDE_ENABLED.get());
        CULLING.setLadderOccludeAlpha(LADDER_OCCLUDE_ALPHA.get());
        CULLING.setTreeOccludeEnabled(TREE_OCCLUDE_ENABLED.get());
        CULLING.setTreeOccludeAlpha(TREE_OCCLUDE_ALPHA.get());
        CULLING.setIgnoreLeavesInRaycast(IGNORE_LEAVES_IN_RAYCAST.get());
        CULLING.setProtectNaturalTreeLogs(PROTECT_NATURAL_TREE_LOGS.get());
        CULLING.setTranslucentFluid(TRANSLUCENT_FLUID.get());
        CULLING.setFluidAlpha(FLUID_ALPHA.get());
        INTERACTION.setSignHoverDisplayMode(SIGN_HOVER_DISPLAY_MODE.get());
        INTERACTION.setSignHoverScale(SIGN_HOVER_SCALE.get());
        INTERACTION.setShowInteractionPrompt(SHOW_INTERACTION_PROMPT.get());
        INTERACTION.setInteractionPromptScale(INTERACTION_PROMPT_SCALE.get());
        INTERACTION.setInteractionPromptShadow(INTERACTION_PROMPT_SHADOW.get());
        INTERACTION.setShowSpatialPrompt(SHOW_SPATIAL_PROMPT.get());
        INTERACTION.setSpatialPromptRadius(SPATIAL_PROMPT_RADIUS.get());
        INTERACTION.setSpatialPromptAllBlocks(SPATIAL_PROMPT_ALL_BLOCKS.get());
    }

    private static void loadCommonConfig() {
        INTERACTION.setServerReachDistance(SERVER_REACH_DISTANCE.get());
    }

    public static void save() {
        isSaving = true;
        try {
            CYLINDER_RADIUS_HORIZONTAL.set(getCylinderRadiusHorizontal());
            CYLINDER_RADIUS_VERTICAL.set(getCylinderRadiusVertical());
            CYLINDER_FORWARD_SHIFT.set(getCylinderForwardShift());
            MINING_CYLINDER_RADIUS.set(getMiningCylinderRadius());
            MINING_CYLINDER_FORWARD_SHIFT.set(getMiningCylinderForwardShift());
            UNDERGROUND_CULLING_ENABLED.set(isUndergroundCullingEnabled());
            UNDERGROUND_CULLING_START_DISTANCE.set(getUndergroundCullingStartDistance());
            UNDERGROUND_CULLING_KEEP_DEPTH.set(getUndergroundCullingKeepDepth());
            MINING_MODE_ENABLED.set(isMiningModeEnabled());
            CLICK_TO_MOVE_ENABLED.set(isClickToMoveEnabled());
            BARITONE_RENDER_PATH.set(isBaritoneRenderPath());
            BARITONE_RENDER_GOAL.set(isBaritoneRenderGoal());
            ARRIVAL_THRESHOLD.set(getArrivalThreshold());
            FORCE_AUTO_JUMP.set(isForceAutoJump());
            SPRINT_DISTANCE_THRESHOLD.set(getSprintDistanceThreshold());
            AUTO_ALIGN_TO_MOVEMENT_ENABLED.set(isAutoAlignToMovementEnabled());
            AUTO_ALIGN_ANGLE_THRESHOLD.set(getAutoAlignAngleThreshold());
            AUTO_ALIGN_COOLDOWN_TICKS.set(getAutoAlignCooldownTicks());
            STABLE_DIRECTION_ANGLE.set(getStableDirectionAngle());
            STABLE_DIRECTION_TICKS.set(getStableDirectionTicks());
            AUTO_ALIGN_ANIMATION_SPEED.set(getAutoAlignAnimationSpeed());
            MOB_CULLING_ENABLED.set(isMobCullingEnabled());
            MOB_TRANSLUCENCY_ENABLED.set(isMobTranslucencyEnabled());
            MOB_TRANSLUCENCY_ALPHA.set(getMobTranslucencyAlpha());
            TRAPDOOR_TRANSLUCENCY_ENABLED.set(isTrapdoorTranslucencyEnabled());
            TRAPDOOR_TRANSPARENCY.set(getTrapdoorTransparency());
            FADE_ENABLED.set(isFadeEnabled());
            FADE_BLOCK_HIT_THRESHOLD.set(getFadeBlockHitThreshold());
            FADE_START.set(getFadeStart());
            FADE_NEAR_ALPHA.set(getFadeNearAlpha());
            FADE_SMOOTHING_HALF_LIFE.set(getFadeSmoothingHalfLife());
            CULLING_MODE.set(getCullingMode());
            VIEW_WEDGE_HALF_ANGLE.set(getViewWedgeHalfAngle());
            COVER_CULLING_RADIUS.set(getCoverCullingRadius());
            COVER_CULLING_VIEWSHED_ENABLED.set(isCoverCullingViewshedEnabled());
            PLAYER_NEAR_TRANSLUCENCY_ENABLED.set(isPlayerNearTranslucencyEnabled());
            PLAYER_NEAR_TRANSLUCENCY_ALPHA.set(getPlayerNearTranslucencyAlpha());
            PLAYER_NEAR_TRANSLUCENCY_RANGE_HORIZONTAL.set(getPlayerNearTranslucencyRangeHorizontal());
            PLAYER_NEAR_TRANSLUCENCY_RANGE_VERTICAL.set(getPlayerNearTranslucencyRangeVertical());
            RANGE_INDICATOR_ENABLED.set(isRangeIndicatorEnabled());
            DESTINATION_HIGHLIGHT_ENABLED.set(isDestinationHighlightEnabled());
            RANGE_EMPTY_HAND.set(getRangeEmptyHand());
            RANGE_SWORD.set(getRangeSword());
            RANGE_AXE.set(getRangeAxe());
            RANGE_PICKAXE.set(getRangePickaxe());
            RANGE_SHOVEL.set(getRangeShovel());
            RANGE_OTHER.set(getRangeOther());
            DEFAULT_ENABLED.set(isDefaultEnabled());
            TARGET_GLOW_ENABLED.set(isTargetGlowEnabled());
            MOB_CONE_CULLING_ENABLED.set(isMobConeCullingEnabled());
            MOB_CONE_HALF_ANGLE.set(getMobConeHalfAngle());
            MOB_CONE_FADE_ANGLE.set(getMobConeFadeAngle());
            MOB_NEAR_RADIUS.set(getMobNearRadius());
            MOB_FOG_END.set(getMobFogEnd());
            ROTATE_ANGLE_MODE.set(getRotateAngleMode());
            CAMERA_SNAP_ROTATION_SPEED.set(getCameraSnapRotationSpeed());
            CAMERA_PITCH.set(getCameraPitch());
            MINING_MODE_PITCH.set(getMiningModePitch());
            MAX_CAMERA_DISTANCE.set(getMaxCameraDistance());
            DEFAULT_CAMERA_DISTANCE.set(getDefaultCameraDistance());
            CAMERA_Y_FOLLOW_DELAY_ENABLED.set(isCameraYFollowDelayEnabled());
            CAMERA_Y_FOLLOW_DELAY.set(getCameraYFollowDelay());
            CAMERA_X_FOLLOW_DELAY_ENABLED.set(isCameraXFollowDelayEnabled());
            CAMERA_X_FOLLOW_DELAY.set(getCameraXFollowDelay());
            CAMERA_Z_FOLLOW_DELAY_ENABLED.set(isCameraZFollowDelayEnabled());
            CAMERA_Z_FOLLOW_DELAY.set(getCameraZFollowDelay());
            FOLLOW_DELAY_WHILE_MOUNTED.set(isFollowDelayWhileMounted());
            PLAYER_SCREEN_OFFSET.set(getPlayerScreenOffset());
            HEAD_BODY_ROTATION_ENABLED.set(isHeadBodyRotationEnabled());
            WATER_MOVEMENT_CONTROL_ENABLED.set(isWaterMovementControlEnabled());
            INDEPENDENT_MOUNT_AIM.set(isIndependentMountAim());
            MOUNT_AIM_MAX_TWIST.set(getMountAimMaxTwist());
            MOUNT_TURN_SMOOTHING.set(getMountTurnSmoothing());
            BOAT_HEAD_MAX_TWIST.set(getBoatHeadMaxTwist());
            BOAT_BODY_MAX_TWIST.set(getBoatBodyMaxTwist());
            TOP_DOWN_FOV.set(getTopDownFov());
            LOCKED_TOP_DOWN.set(isLockedTopDown());
            SCROLL_ONLY_ZOOM_ENABLED.set(isScrollOnlyZoomEnabled());
            CAMERA_ZOOM_SMOOTHING_ENABLED.set(isCameraZoomSmoothingEnabled());
            CAMERA_ZOOM_SMOOTHING.set(getCameraZoomSmoothing());
            MOUSE_PAN_ENABLED.set(isMousePanEnabled());
            MOUSE_PAN_MAX_DISTANCE.set(getMousePanMaxDistance());
            MOUSE_PAN_SMOOTHING.set(getMousePanSmoothing());
            TARGET_LOCK_ENABLED.set(isTargetLockEnabled());
            TARGET_LOCK_DURATION.set(getTargetLockDuration());
            TARGET_HITBOX_EXPANSION.set(getTargetHitboxExpansion());
            SCREEN_REACH_ENABLED.set(isScreenReachEnabled());
            REACH_DISTANCE.set(getReachDistance());
            PLACEMENT_PREVIEW_ENABLED.set(isPlacementPreviewEnabled());
            PLACEMENT_TRANSPARENCY.set(getPlacementTransparency());
            CLICK_POSITION_PLACEMENT_ENABLED.set(isClickPositionPlacementEnabled());
            STAIRCASE_EXCLUSION_ENABLED.set(isStaircaseExclusionEnabled());
            STAIRCASE_EXCLUSION_HEIGHT.set(getStaircaseExclusionHeight());
            STAIRCASE_OCCLUDE_ENABLED.set(isStaircaseOccludeEnabled());
            STAIRCASE_OCCLUDE_ALPHA.set(getStaircaseOccludeAlpha());
            LADDER_OCCLUDE_ENABLED.set(isLadderOccludeEnabled());
            LADDER_OCCLUDE_ALPHA.set(getLadderOccludeAlpha());
            TREE_OCCLUDE_ENABLED.set(isTreeOccludeEnabled());
            TREE_OCCLUDE_ALPHA.set(getTreeOccludeAlpha());
            IGNORE_LEAVES_IN_RAYCAST.set(isIgnoreLeavesInRaycast());
            PROTECT_NATURAL_TREE_LOGS.set(isProtectNaturalTreeLogs());
            TRANSLUCENT_FLUID.set(isTranslucentFluid());
            FLUID_ALPHA.set(getFluidAlpha());
            SIGN_HOVER_DISPLAY_MODE.set(getSignHoverDisplayMode());
            SIGN_HOVER_SCALE.set(getSignHoverScale());
            SHOW_INTERACTION_PROMPT.set(isShowInteractionPrompt());
            INTERACTION_PROMPT_SCALE.set(getInteractionPromptScale());
            INTERACTION_PROMPT_SHADOW.set(isInteractionPromptShadow());
            SHOW_SPATIAL_PROMPT.set(isShowSpatialPrompt());
            SPATIAL_PROMPT_RADIUS.set(getSpatialPromptRadius());
            SPATIAL_PROMPT_ALL_BLOCKS.set(isSpatialPromptAllBlocks());
            SPEC.save();
        } finally {
            isSaving = false;
        }
        notifyConfigChanged();
    }

    public static void resetToDefaults() {
        CULLING.setCylinderRadiusHorizontal(CYLINDER_RADIUS_HORIZONTAL.getDefault());
        CULLING.setCylinderRadiusVertical(CYLINDER_RADIUS_VERTICAL.getDefault());
        CULLING.setCylinderForwardShift(CYLINDER_FORWARD_SHIFT.getDefault());
        CULLING.setMiningCylinderRadius(MINING_CYLINDER_RADIUS.getDefault());
        CULLING.setMiningCylinderForwardShift(MINING_CYLINDER_FORWARD_SHIFT.getDefault());
        CULLING.setUndergroundCullingEnabled(UNDERGROUND_CULLING_ENABLED.getDefault());
        CULLING.setUndergroundCullingStartDistance(UNDERGROUND_CULLING_START_DISTANCE.getDefault());
        CULLING.setUndergroundCullingKeepDepth(UNDERGROUND_CULLING_KEEP_DEPTH.getDefault());
        INTERACTION.setMiningModeEnabled(MINING_MODE_ENABLED.getDefault());
        INTERACTION.setClickToMoveEnabled(CLICK_TO_MOVE_ENABLED.getDefault());
        INTEGRATIONS.setBaritoneRenderPath(BARITONE_RENDER_PATH.getDefault());
        INTEGRATIONS.setBaritoneRenderGoal(BARITONE_RENDER_GOAL.getDefault());
        INTERACTION.setArrivalThreshold(ARRIVAL_THRESHOLD.getDefault());
        INTERACTION.setForceAutoJump(FORCE_AUTO_JUMP.getDefault());
        INTERACTION.setSprintDistanceThreshold(SPRINT_DISTANCE_THRESHOLD.getDefault());
        INTERACTION.setAutoAlignToMovementEnabled(AUTO_ALIGN_TO_MOVEMENT_ENABLED.getDefault());
        INTERACTION.setAutoAlignAngleThreshold(AUTO_ALIGN_ANGLE_THRESHOLD.getDefault());
        INTERACTION.setAutoAlignCooldownTicks(AUTO_ALIGN_COOLDOWN_TICKS.getDefault());
        INTERACTION.setStableDirectionAngle(STABLE_DIRECTION_ANGLE.getDefault());
        INTERACTION.setStableDirectionTicks(STABLE_DIRECTION_TICKS.getDefault());
        INTERACTION.setAutoAlignAnimationSpeed(AUTO_ALIGN_ANIMATION_SPEED.getDefault());
        CULLING.setMobCullingEnabled(MOB_CULLING_ENABLED.getDefault());
        CULLING.setMobTranslucencyEnabled(MOB_TRANSLUCENCY_ENABLED.getDefault());
        CULLING.setMobTranslucencyAlpha(MOB_TRANSLUCENCY_ALPHA.getDefault());
        CULLING.setTrapdoorTranslucencyEnabled(TRAPDOOR_TRANSLUCENCY_ENABLED.getDefault());
        CULLING.setTrapdoorTransparency(TRAPDOOR_TRANSPARENCY.getDefault());
        CULLING.setFadeEnabled(FADE_ENABLED.getDefault());
        CULLING.setFadeBlockHitThreshold(FADE_BLOCK_HIT_THRESHOLD.getDefault());
        CULLING.setFadeStart(FADE_START.getDefault());
        CULLING.setFadeNearAlpha(FADE_NEAR_ALPHA.getDefault());
        CULLING.setFadeSmoothingHalfLife(FADE_SMOOTHING_HALF_LIFE.getDefault());
        INTERACTION.setRangeIndicatorEnabled(RANGE_INDICATOR_ENABLED.getDefault());
        INTERACTION.setDestinationHighlightEnabled(DESTINATION_HIGHLIGHT_ENABLED.getDefault());
        INTERACTION.setRangeEmptyHand(RANGE_EMPTY_HAND.getDefault());
        INTERACTION.setRangeSword(RANGE_SWORD.getDefault());
        INTERACTION.setRangeAxe(RANGE_AXE.getDefault());
        INTERACTION.setRangePickaxe(RANGE_PICKAXE.getDefault());
        INTERACTION.setRangeShovel(RANGE_SHOVEL.getDefault());
        INTERACTION.setRangeOther(RANGE_OTHER.getDefault());
        INTERACTION.setDefaultEnabled(DEFAULT_ENABLED.getDefault());
        INTERACTION.setTargetGlowEnabled(TARGET_GLOW_ENABLED.getDefault());
        CULLING.setMobConeCullingEnabled(MOB_CONE_CULLING_ENABLED.getDefault());
        CULLING.setMobConeHalfAngle(MOB_CONE_HALF_ANGLE.getDefault());
        CULLING.setMobConeFadeAngle(MOB_CONE_FADE_ANGLE.getDefault());
        CULLING.setMobNearRadius(MOB_NEAR_RADIUS.getDefault());
        CULLING.setMobFogEnd(MOB_FOG_END.getDefault());
        CAMERA.setRotateAngleMode(ROTATE_ANGLE_MODE.getDefault());
        CAMERA.setCameraSnapRotationSpeed(CAMERA_SNAP_ROTATION_SPEED.getDefault());
        CAMERA.setCameraPitch(CAMERA_PITCH.getDefault());
        CAMERA.setMiningModePitch(MINING_MODE_PITCH.getDefault());
        CAMERA.setMaxCameraDistance(MAX_CAMERA_DISTANCE.getDefault());
        CAMERA.setDefaultCameraDistance(DEFAULT_CAMERA_DISTANCE.getDefault());
        CAMERA.setCameraYFollowDelayEnabled(CAMERA_Y_FOLLOW_DELAY_ENABLED.getDefault());
        CAMERA.setCameraYFollowDelay(CAMERA_Y_FOLLOW_DELAY.getDefault());
        CAMERA.setCameraXFollowDelayEnabled(CAMERA_X_FOLLOW_DELAY_ENABLED.getDefault());
        CAMERA.setCameraXFollowDelay(CAMERA_X_FOLLOW_DELAY.getDefault());
        CAMERA.setCameraZFollowDelayEnabled(CAMERA_Z_FOLLOW_DELAY_ENABLED.getDefault());
        CAMERA.setCameraZFollowDelay(CAMERA_Z_FOLLOW_DELAY.getDefault());
        CAMERA.setFollowDelayWhileMounted(FOLLOW_DELAY_WHILE_MOUNTED.getDefault());
        CAMERA.setPlayerScreenOffset(PLAYER_SCREEN_OFFSET.getDefault());
        CAMERA.setHeadBodyRotationEnabled(HEAD_BODY_ROTATION_ENABLED.getDefault());
        CAMERA.setIndependentMountAim(INDEPENDENT_MOUNT_AIM.getDefault());
        CAMERA.setMountAimMaxTwist(MOUNT_AIM_MAX_TWIST.getDefault());
        CAMERA.setMountTurnSmoothing(MOUNT_TURN_SMOOTHING.getDefault());
        CAMERA.setBoatHeadMaxTwist(BOAT_HEAD_MAX_TWIST.getDefault());
        CAMERA.setBoatBodyMaxTwist(BOAT_BODY_MAX_TWIST.getDefault());
        CAMERA.setTopDownFov(TOP_DOWN_FOV.getDefault());
        CAMERA.setLockedTopDown(LOCKED_TOP_DOWN.getDefault());
        CAMERA.setScrollOnlyZoomEnabled(SCROLL_ONLY_ZOOM_ENABLED.getDefault());
        CAMERA.setCameraZoomSmoothingEnabled(CAMERA_ZOOM_SMOOTHING_ENABLED.getDefault());
        CAMERA.setCameraZoomSmoothing(CAMERA_ZOOM_SMOOTHING.getDefault());
        CAMERA.setMousePanEnabled(MOUSE_PAN_ENABLED.getDefault());
        CAMERA.setMousePanMaxDistance(MOUSE_PAN_MAX_DISTANCE.getDefault());
        CAMERA.setMousePanSmoothing(MOUSE_PAN_SMOOTHING.getDefault());
        INTERACTION.setTargetLockEnabled(TARGET_LOCK_ENABLED.getDefault());
        INTERACTION.setTargetLockDuration(TARGET_LOCK_DURATION.getDefault());
        INTERACTION.setTargetHitboxExpansion(TARGET_HITBOX_EXPANSION.getDefault());
        INTERACTION.setScreenReachEnabled(SCREEN_REACH_ENABLED.getDefault());
        INTERACTION.setReachDistance(REACH_DISTANCE.getDefault());
        PLACEMENT.setPlacementPreviewEnabled(PLACEMENT_PREVIEW_ENABLED.getDefault());
        PLACEMENT.setPlacementTransparency(PLACEMENT_TRANSPARENCY.getDefault());
        PLACEMENT.setClickPositionPlacementEnabled(CLICK_POSITION_PLACEMENT_ENABLED.getDefault());
        CULLING.setStaircaseExclusionEnabled(STAIRCASE_EXCLUSION_ENABLED.getDefault());
        CULLING.setStaircaseExclusionHeight(STAIRCASE_EXCLUSION_HEIGHT.getDefault());
        CULLING.setStaircaseOccludeEnabled(STAIRCASE_OCCLUDE_ENABLED.getDefault());
        CULLING.setStaircaseOccludeAlpha(STAIRCASE_OCCLUDE_ALPHA.getDefault());
        CULLING.setLadderOccludeEnabled(LADDER_OCCLUDE_ENABLED.getDefault());
        CULLING.setLadderOccludeAlpha(LADDER_OCCLUDE_ALPHA.getDefault());
        CULLING.setTreeOccludeEnabled(TREE_OCCLUDE_ENABLED.getDefault());
        CULLING.setTreeOccludeAlpha(TREE_OCCLUDE_ALPHA.getDefault());
        CULLING.setIgnoreLeavesInRaycast(IGNORE_LEAVES_IN_RAYCAST.getDefault());
        CULLING.setProtectNaturalTreeLogs(PROTECT_NATURAL_TREE_LOGS.getDefault());
        CULLING.setTranslucentFluid(TRANSLUCENT_FLUID.getDefault());
        CULLING.setFluidAlpha(FLUID_ALPHA.getDefault());
        INTERACTION.setSignHoverDisplayMode(SIGN_HOVER_DISPLAY_MODE.getDefault());
        INTERACTION.setSignHoverScale(SIGN_HOVER_SCALE.getDefault());
        INTERACTION.setShowInteractionPrompt(SHOW_INTERACTION_PROMPT.getDefault());
        INTERACTION.setInteractionPromptScale(INTERACTION_PROMPT_SCALE.getDefault());
        INTERACTION.setInteractionPromptShadow(INTERACTION_PROMPT_SHADOW.getDefault());
        INTERACTION.setShowSpatialPrompt(SHOW_SPATIAL_PROMPT.getDefault());
        INTERACTION.setSpatialPromptRadius(SPATIAL_PROMPT_RADIUS.getDefault());
        INTERACTION.setSpatialPromptAllBlocks(SPATIAL_PROMPT_ALL_BLOCKS.getDefault());
    }

    public static ForgeConfigSpec.DoubleValue getMaxCameraDistanceSpec() {
        return MAX_CAMERA_DISTANCE;
    }

    public static ForgeConfigSpec.DoubleValue getDefaultCameraDistanceSpec() {
        return DEFAULT_CAMERA_DISTANCE;
    }
}