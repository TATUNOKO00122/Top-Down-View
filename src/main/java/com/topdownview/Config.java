package com.topdownview;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

        // Culling settings
        private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_HORIZONTAL = BUILDER
                        .comment("シリンダーの水平方向半径（ブロック数）")
                        .defineInRange("cylinderRadiusHorizontal", 5, 1, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_VERTICAL = BUILDER
                        .comment("シリンダーの垂直方向半径（ブロック数）")
                        .defineInRange("cylinderRadiusVertical", 4, 1, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_FORWARD_SHIFT = BUILDER
                        .comment("楕円柱の中心を前方にシフトする距離（ブロック数）")
                        .defineInRange("cylinderForwardShift", 4, 0, 10);

        // Mining mode cylinder settings (true cylinder, not elliptical)
        private static final ForgeConfigSpec.IntValue MINING_CYLINDER_RADIUS = BUILDER
                        .comment("マイニングモード用円柱の半径（ブロック数）")
                        .defineInRange("miningCylinderRadius", 5, 1, 16);

        private static final ForgeConfigSpec.IntValue MINING_CYLINDER_FORWARD_SHIFT = BUILDER
                        .comment("マイニングモード時の円柱中心を前方にシフトする距離（ブロック数）")
                        .defineInRange("miningCylinderForwardShift", 0, 0, 10);

        private static final ForgeConfigSpec.BooleanValue MINING_MODE_ENABLED = BUILDER
                        .comment("マイニングモード機能の有効/無効（無効時はキー入力を無視）")
                        .define("miningModeEnabled", false);

        // Movement settings
        private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
                        .comment("クリックツームーブ機能の有効/無効")
                        .define("clickToMoveEnabled", false);

        private static final ForgeConfigSpec.DoubleValue ARRIVAL_THRESHOLD = BUILDER
                        .comment("目的地到達判定の距離（ブロック数）")
                        .defineInRange("arrivalThreshold", 1.5, 0.5, 5.0);

        private static final ForgeConfigSpec.BooleanValue FORCE_AUTO_JUMP = BUILDER
                        .comment("クリックツームーブ中の自動ジャンプ強制")
                        .define("forceAutoJump", false);

        private static final ForgeConfigSpec.DoubleValue SPRINT_DISTANCE_THRESHOLD = BUILDER
                        .comment("クリックツームーブ中にダッシュを開始する距離（ブロック数）")
                        .defineInRange("sprintDistanceThreshold", 5.0, 1.0, 50.0);

        private static final ForgeConfigSpec.BooleanValue AUTO_ALIGN_TO_MOVEMENT_ENABLED = BUILDER
                        .comment("移動中にカメラを進行方向へ自動回転")
                        .define("autoAlignToMovementEnabled", false);

        private static final ForgeConfigSpec.IntValue AUTO_ALIGN_ANGLE_THRESHOLD = BUILDER
                        .comment("自動回転を開始する角度差の閾値（度）")
                        .defineInRange("autoAlignAngleThreshold", 45, 0, 90);

        private static final ForgeConfigSpec.IntValue AUTO_ALIGN_COOLDOWN_TICKS = BUILDER
                        .comment("自動回転後のクールダウン時間（tick、20tick=1秒）")
                        .defineInRange("autoAlignCooldownTicks", 30, 0, 100);

        private static final ForgeConfigSpec.IntValue STABLE_DIRECTION_ANGLE = BUILDER
                        .comment("同じ方向と見なす許容角度差（度）")
                        .defineInRange("stableDirectionAngle", 15, 5, 60);

        private static final ForgeConfigSpec.IntValue STABLE_DIRECTION_TICKS = BUILDER
                        .comment("反応に必要な方向安定時間（tick）")
                        .defineInRange("stableDirectionTicks", 20, 5, 60);

        private static final ForgeConfigSpec.DoubleValue AUTO_ALIGN_ANIMATION_SPEED = BUILDER
                        .comment("自動回転のアニメーション速度（0.05=遅い、0.5=速い）")
                        .defineInRange("autoAlignAnimationSpeed", 0.1, 0.05, 0.5);

        // Translucent trapdoor settings
        private static final ForgeConfigSpec.BooleanValue TRAPDOOR_TRANSLUCENCY_ENABLED = BUILDER
                        .comment("遮蔽トラップドアの半透明化の有効/無効")
                        .define("trapdoorTranslucencyEnabled", false);

        private static final ForgeConfigSpec.DoubleValue TRAPDOOR_TRANSPARENCY = BUILDER
                        .comment("遮蔽トラップドアの透明度（0.0=透明、1.0=不透明）")
                        .defineInRange("trapdoorTransparency", 0.3, 0.0, 1.0);

        // Fade settings
        private static final ForgeConfigSpec.BooleanValue FADE_ENABLED = BUILDER
                        .comment("カリング境界の距離フェードの有効/無効")
                        .define("fadeEnabled", true);

        private static final ForgeConfigSpec.BooleanValue FADE_BLOCK_RAYCAST_PROTECTION = BUILDER
                        .comment("半透明化したブロックにマウスレイキャスト判定を付ける保護機能の有効/無効")
                        .define("fadeBlockRaycastProtection", false);

        private static final ForgeConfigSpec.DoubleValue FADE_START = BUILDER
                        .comment("フェード開始位置（境界=1.0に対する比率、0.7=境界の70%位置から開始）")
                        .defineInRange("fadeStart", 0.7, 0.0, 0.9);

        private static final ForgeConfigSpec.DoubleValue FADE_NEAR_ALPHA = BUILDER
                        .comment("カメラ付近の不透明度（0.0=透明、1.0=不透明）")
                        .defineInRange("fadeNearAlpha", 0.0, 0.0, 1.0);

        // Target highlight settings
        private static final ForgeConfigSpec.BooleanValue RANGE_INDICATOR_ENABLED = BUILDER
                        .comment("射程外ターゲットを赤く表示する機能の有効/無効")
                        .define("rangeIndicatorEnabled", false);

        // Weapon type specific attack ranges
        private static final ForgeConfigSpec.DoubleValue RANGE_EMPTY_HAND = BUILDER
                        .comment("素手時の攻撃射程")
                        .defineInRange("rangeEmptyHand", 3.0, 1.0, 10.0);

        private static final ForgeConfigSpec.DoubleValue RANGE_SWORD = BUILDER
                        .comment("剣装備時の攻撃射程")
                        .defineInRange("rangeSword", 3.0, 1.0, 10.0);

        private static final ForgeConfigSpec.DoubleValue RANGE_AXE = BUILDER
                        .comment("斧装備時の攻撃射程")
                        .defineInRange("rangeAxe", 3.0, 1.0, 10.0);

        private static final ForgeConfigSpec.DoubleValue RANGE_PICKAXE = BUILDER
                        .comment("ツルハシ装備時の攻撃射程")
                        .defineInRange("rangePickaxe", 3.0, 1.0, 10.0);

        private static final ForgeConfigSpec.DoubleValue RANGE_SHOVEL = BUILDER
                        .comment("シャベル装備時の攻撃射程")
                        .defineInRange("rangeShovel", 3.0, 1.0, 10.0);

        private static final ForgeConfigSpec.DoubleValue RANGE_OTHER = BUILDER
                        .comment("その他の武器/アイテム装備時の攻撃射程")
                        .defineInRange("rangeOther", 3.0, 1.0, 10.0);

        // Default state
        private static final ForgeConfigSpec.BooleanValue DEFAULT_ENABLED = BUILDER
                        .comment("ゲーム起動時にトップダウン視点をデフォルトで有効にする")
                        .define("defaultEnabled", true);

	// Camera settings
	private static final ForgeConfigSpec.IntValue ROTATE_ANGLE_MODE = BUILDER
			.comment("カメラの回転スナップ角度モード (0=90度, 1=45度, 2=15度)")
			.defineInRange("rotateAngleMode", 0, 0, 2);

	private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
			.comment("カメラが見下ろす垂直の角度（ピッチ）")
			.defineInRange("cameraPitch", 45.0, 10.0, 90.0);

	private static final ForgeConfigSpec.DoubleValue MINING_MODE_PITCH = BUILDER
			.comment("マイニングモード時のカメラ角度（ピッチ）")
			.defineInRange("miningModePitch", 45.0, 10.0, 90.0);

	private static final ForgeConfigSpec.DoubleValue MAX_CAMERA_DISTANCE = BUILDER
			.comment("最大カメラ距離（ズームアウト限界）")
			.defineInRange("maxCameraDistance", 50.0, 10.0, 200.0);

	private static final ForgeConfigSpec.DoubleValue DEFAULT_CAMERA_DISTANCE = BUILDER
			.comment("デフォルトカメラ距離（初期ズーム位置）")
			.defineInRange("defaultCameraDistance", 9.0, 5.0, 100.0);

        // Drag rotation settings
        private static final ForgeConfigSpec.BooleanValue DRAG_ROTATION_ENABLED = BUILDER
                        .comment("マウスドラッグによるカメラ回転の有効/無効")
                        .define("dragRotationEnabled", true);

        private static final ForgeConfigSpec.DoubleValue DRAG_ROTATION_SENSITIVITY = BUILDER
                        .comment("ドラッグ回転の感度（値が大きいほど敏感）")
                        .defineInRange("dragRotationSensitivity", 0.1, 0.01, 1.0);

        // Camera Y follow delay (motion sickness prevention)
        private static final ForgeConfigSpec.BooleanValue CAMERA_Y_FOLLOW_DELAY_ENABLED = BUILDER
                        .comment("カメラY軸追従遅延の有効/無効（3D酔い対策）")
                        .define("cameraYFollowDelayEnabled", false);

        private static final ForgeConfigSpec.DoubleValue CAMERA_Y_FOLLOW_DELAY = BUILDER
                        .comment("カメラY軸追従の遅延時間（秒）、大きいほど遅れる")
                        .defineInRange("cameraYFollowDelay", 0.15, 0.0, 4.0);

        // Camera X follow delay (motion sickness prevention)
        private static final ForgeConfigSpec.BooleanValue CAMERA_X_FOLLOW_DELAY_ENABLED = BUILDER
                        .comment("カメラX軸追従遅延の有効/無効（3D酔い対策）")
                        .define("cameraXFollowDelayEnabled", false);

        private static final ForgeConfigSpec.DoubleValue CAMERA_X_FOLLOW_DELAY = BUILDER
                        .comment("カメラX軸追従の遅延時間（秒）、大きいほど遅れる")
                        .defineInRange("cameraXFollowDelay", 0.1, 0.0, 4.0);

        // Camera Z follow delay (motion sickness prevention)
        private static final ForgeConfigSpec.BooleanValue CAMERA_Z_FOLLOW_DELAY_ENABLED = BUILDER
                        .comment("カメラZ軸追従遅延の有効/無効（3D酔い対策）")
                        .define("cameraZFollowDelayEnabled", false);

        private static final ForgeConfigSpec.DoubleValue CAMERA_Z_FOLLOW_DELAY = BUILDER
                        .comment("カメラZ軸追従の遅延時間（秒）、大きいほど遅れる")
                        .defineInRange("cameraZFollowDelay", 0.1, 0.0, 4.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values (private with getters for encapsulation)
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
        private static boolean fadeBlockRaycastProtection;
        private static double fadeStart;
        private static double fadeNearAlpha;

        private static boolean rangeIndicatorEnabled;

        private static double rangeEmptyHand;
        private static double rangeSword;
        private static double rangeAxe;
        private static double rangePickaxe;
        private static double rangeShovel;
        private static double rangeOther;

        private static boolean defaultEnabled;

	private static int rotateAngleMode;
	private static double cameraPitch;
	private static double miningModePitch;
	private static double maxCameraDistance;
	private static double defaultCameraDistance;

	// Drag rotation settings
	private static boolean dragRotationEnabled;
	private static double dragRotationSensitivity;

	// Camera Y follow delay
	private static boolean cameraYFollowDelayEnabled;
	private static double cameraYFollowDelay;

	// Camera X follow delay
	private static boolean cameraXFollowDelayEnabled;
	private static double cameraXFollowDelay;

	// Camera Z follow delay
	private static boolean cameraZFollowDelayEnabled;
	private static double cameraZFollowDelay;

        // ==================== Getters ====================
        
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
        public static boolean isFadeBlockRaycastProtection() { return fadeBlockRaycastProtection; }
        public static double getFadeStart() { return fadeStart; }
        public static double getFadeNearAlpha() { return fadeNearAlpha; }
        public static boolean isRangeIndicatorEnabled() { return rangeIndicatorEnabled; }
        public static double getRangeEmptyHand() { return rangeEmptyHand; }
        public static double getRangeSword() { return rangeSword; }
        public static double getRangeAxe() { return rangeAxe; }
        public static double getRangePickaxe() { return rangePickaxe; }
        public static double getRangeShovel() { return rangeShovel; }
        public static double getRangeOther() { return rangeOther; }
        public static boolean isDefaultEnabled() { return defaultEnabled; }
	public static int getRotateAngleMode() { return rotateAngleMode; }
	public static double getCameraPitch() { return cameraPitch; }
	public static double getMiningModePitch() { return miningModePitch; }
	public static double getMaxCameraDistance() { return maxCameraDistance; }
	public static double getDefaultCameraDistance() { return defaultCameraDistance; }
	public static boolean isDragRotationEnabled() { return dragRotationEnabled; }
	public static double getDragRotationSensitivity() { return dragRotationSensitivity; }
	public static boolean isCameraYFollowDelayEnabled() { return cameraYFollowDelayEnabled; }
	public static double getCameraYFollowDelay() { return cameraYFollowDelay; }
	public static boolean isCameraXFollowDelayEnabled() { return cameraXFollowDelayEnabled; }
	public static double getCameraXFollowDelay() { return cameraXFollowDelay; }
	public static boolean isCameraZFollowDelayEnabled() { return cameraZFollowDelayEnabled; }
	public static double getCameraZFollowDelay() { return cameraZFollowDelay; }

        // ==================== Setters (for GUI) ====================
        
        public static void setCylinderRadiusHorizontal(int value) { cylinderRadiusHorizontal = value; }
        public static void setCylinderRadiusVertical(int value) { cylinderRadiusVertical = value; }
        public static void setCylinderForwardShift(int value) { cylinderForwardShift = value; }
        public static void setMiningCylinderRadius(int value) { miningCylinderRadius = value; }
        public static void setMiningCylinderForwardShift(int value) { miningCylinderForwardShift = value; }
        public static void setMiningModeEnabled(boolean value) { miningModeEnabled = value; }
        public static void setClickToMoveEnabled(boolean value) { clickToMoveEnabled = value; }
        public static void setArrivalThreshold(double value) { arrivalThreshold = value; }
        public static void setForceAutoJump(boolean value) { forceAutoJump = value; }
        public static void setSprintDistanceThreshold(double value) { sprintDistanceThreshold = value; }
        public static void setAutoAlignToMovementEnabled(boolean value) { autoAlignToMovementEnabled = value; }
        public static void setAutoAlignAngleThreshold(int value) { autoAlignAngleThreshold = value; }
        public static void setAutoAlignCooldownTicks(int value) { autoAlignCooldownTicks = value; }
        public static void setStableDirectionAngle(int value) { stableDirectionAngle = value; }
        public static void setStableDirectionTicks(int value) { stableDirectionTicks = value; }
        public static void setAutoAlignAnimationSpeed(double value) { autoAlignAnimationSpeed = value; }
        public static void setTrapdoorTranslucencyEnabled(boolean value) { trapdoorTranslucencyEnabled = value; }
        public static void setTrapdoorTransparency(double value) { trapdoorTransparency = value; }
        public static void setFadeEnabled(boolean value) { fadeEnabled = value; }
        public static void setFadeBlockRaycastProtection(boolean value) { fadeBlockRaycastProtection = value; }
        public static void setFadeStart(double value) { fadeStart = value; }
        public static void setFadeNearAlpha(double value) { fadeNearAlpha = value; }
        public static void setRangeIndicatorEnabled(boolean value) { rangeIndicatorEnabled = value; }
        public static void setRangeEmptyHand(double value) { rangeEmptyHand = value; }
        public static void setRangeSword(double value) { rangeSword = value; }
        public static void setRangeAxe(double value) { rangeAxe = value; }
        public static void setRangePickaxe(double value) { rangePickaxe = value; }
        public static void setRangeShovel(double value) { rangeShovel = value; }
        public static void setRangeOther(double value) { rangeOther = value; }
        public static void setDefaultEnabled(boolean value) { defaultEnabled = value; }
	public static void setRotateAngleMode(int value) { rotateAngleMode = value; }
	public static void setCameraPitch(double value) { cameraPitch = value; }
	public static void setMiningModePitch(double value) { miningModePitch = value; }
	public static void setMaxCameraDistance(double value) { maxCameraDistance = value; }
	public static void setDefaultCameraDistance(double value) { defaultCameraDistance = value; }
	public static void setDragRotationEnabled(boolean value) { dragRotationEnabled = value; }
	public static void setDragRotationSensitivity(double value) { dragRotationSensitivity = value; }
	public static void setCameraYFollowDelayEnabled(boolean value) { cameraYFollowDelayEnabled = value; }
	public static void setCameraYFollowDelay(double value) { cameraYFollowDelay = value; }
	public static void setCameraXFollowDelayEnabled(boolean value) { cameraXFollowDelayEnabled = value; }
	public static void setCameraXFollowDelay(double value) { cameraXFollowDelay = value; }
	public static void setCameraZFollowDelayEnabled(boolean value) { cameraZFollowDelayEnabled = value; }
	public static void setCameraZFollowDelay(double value) { cameraZFollowDelay = value; }

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
                fadeBlockRaycastProtection = FADE_BLOCK_RAYCAST_PROTECTION.get();
                fadeStart = FADE_START.get();
                fadeNearAlpha = FADE_NEAR_ALPHA.get();
                rangeIndicatorEnabled = RANGE_INDICATOR_ENABLED.get();
                rangeEmptyHand = RANGE_EMPTY_HAND.get();
                rangeSword = RANGE_SWORD.get();
                rangeAxe = RANGE_AXE.get();
                rangePickaxe = RANGE_PICKAXE.get();
                rangeShovel = RANGE_SHOVEL.get();
                rangeOther = RANGE_OTHER.get();
                defaultEnabled = DEFAULT_ENABLED.get();
                rotateAngleMode = ROTATE_ANGLE_MODE.get();
                cameraPitch = CAMERA_PITCH.get();
                miningModePitch = MINING_MODE_PITCH.get();
                maxCameraDistance = MAX_CAMERA_DISTANCE.get();
                defaultCameraDistance = DEFAULT_CAMERA_DISTANCE.get();
		dragRotationEnabled = DRAG_ROTATION_ENABLED.get();
		dragRotationSensitivity = DRAG_ROTATION_SENSITIVITY.get();
		cameraYFollowDelayEnabled = CAMERA_Y_FOLLOW_DELAY_ENABLED.get();
		cameraYFollowDelay = CAMERA_Y_FOLLOW_DELAY.get();
		cameraXFollowDelayEnabled = CAMERA_X_FOLLOW_DELAY_ENABLED.get();
		cameraXFollowDelay = CAMERA_X_FOLLOW_DELAY.get();
		cameraZFollowDelayEnabled = CAMERA_Z_FOLLOW_DELAY_ENABLED.get();
		cameraZFollowDelay = CAMERA_Z_FOLLOW_DELAY.get();

                // ゲーム起動時にデフォルト状態を適用
                com.topdownview.state.ModState.STATUS.setEnabled(defaultEnabled);

                notifyConfigChanged();
        }

        public static void save() {
                com.topdownview.TopDownViewMod.getLogger().info("[TopDownView][Config.save] Saving values - maxCameraDistance: {}, defaultCameraDistance: {}",
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
                FADE_BLOCK_RAYCAST_PROTECTION.set(fadeBlockRaycastProtection);
                FADE_START.set(fadeStart);
                FADE_NEAR_ALPHA.set(fadeNearAlpha);
                RANGE_INDICATOR_ENABLED.set(rangeIndicatorEnabled);
                RANGE_EMPTY_HAND.set(rangeEmptyHand);
                RANGE_SWORD.set(rangeSword);
                RANGE_AXE.set(rangeAxe);
                RANGE_PICKAXE.set(rangePickaxe);
                RANGE_SHOVEL.set(rangeShovel);
                RANGE_OTHER.set(rangeOther);
                DEFAULT_ENABLED.set(defaultEnabled);
        ROTATE_ANGLE_MODE.set(rotateAngleMode);
        CAMERA_PITCH.set(cameraPitch);
        MINING_MODE_PITCH.set(miningModePitch);
        MAX_CAMERA_DISTANCE.set(maxCameraDistance);
        DEFAULT_CAMERA_DISTANCE.set(defaultCameraDistance);
	DRAG_ROTATION_ENABLED.set(dragRotationEnabled);
	DRAG_ROTATION_SENSITIVITY.set(dragRotationSensitivity);
	CAMERA_Y_FOLLOW_DELAY_ENABLED.set(cameraYFollowDelayEnabled);
	CAMERA_Y_FOLLOW_DELAY.set(cameraYFollowDelay);
	CAMERA_X_FOLLOW_DELAY_ENABLED.set(cameraXFollowDelayEnabled);
	CAMERA_X_FOLLOW_DELAY.set(cameraXFollowDelay);
	CAMERA_Z_FOLLOW_DELAY_ENABLED.set(cameraZFollowDelayEnabled);
	CAMERA_Z_FOLLOW_DELAY.set(cameraZFollowDelay);
	SPEC.save();
                com.topdownview.TopDownViewMod.getLogger().info("[TopDownView][Config.save] Config file saved successfully");
                notifyConfigChanged();
        }

        // ForgeConfigSpecへのアクセス用ゲッター
        public static ForgeConfigSpec.DoubleValue getMaxCameraDistanceSpec() {
                return MAX_CAMERA_DISTANCE;
        }

        public static ForgeConfigSpec.DoubleValue getDefaultCameraDistanceSpec() {
                return DEFAULT_CAMERA_DISTANCE;
        }
}
