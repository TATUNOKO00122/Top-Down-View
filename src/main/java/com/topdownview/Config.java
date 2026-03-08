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
                        .defineInRange("miningCylinderRadius", 3, 1, 16);

        private static final ForgeConfigSpec.IntValue MINING_CYLINDER_FORWARD_SHIFT = BUILDER
                        .comment("マイニングモード時の円柱中心を前方にシフトする距離（ブロック数）")
                        .defineInRange("miningCylinderForwardShift", 2, 0, 10);

        // Movement settings
        private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
                        .comment("クリックツームーブ機能の有効/無効")
                        .define("clickToMoveEnabled", false);

        private static final ForgeConfigSpec.DoubleValue ARRIVAL_THRESHOLD = BUILDER
                        .comment("目的地到達判定の距離（ブロック数）")
                        .defineInRange("arrivalThreshold", 1.5, 0.5, 5.0);

        private static final ForgeConfigSpec.BooleanValue FORCE_AUTO_JUMP = BUILDER
                        .comment("クリックツームーブ中の自動ジャンプ強制")
                        .define("forceAutoJump", true);

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
			.defineInRange("miningModePitch", 90.0, 10.0, 90.0);

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
                        .defineInRange("dragRotationSensitivity", 0.11, 0.01, 1.0);

        // Pathfinding settings (DISABLED - straight-line movement only)
        // private static final ForgeConfigSpec.IntValue PATHFINDING_RANGE = BUILDER
        // .comment("経路探索の最大距離（ブロック数）")
        // .defineInRange("pathfindingRange", 32, 8, 64);
        // private static final ForgeConfigSpec.IntValue PATH_RECALC_COOLDOWN = BUILDER
        // .comment("経路再計算のクールダウン（tick）")
        // .defineInRange("pathRecalcCooldown", 20, 5, 100);
        // private static final ForgeConfigSpec.BooleanValue PATHFINDING_ENABLED =
        // BUILDER
        // .comment("経路探索（A*）と動的回避の有効/無効")
        // .define("pathfindingEnabled", true);
        // private static final ForgeConfigSpec.DoubleValue AVOIDANCE_RADIUS = BUILDER
        // .comment("回避判定の半径（ブロック数）")
        // .defineInRange("avoidanceRadius", 2.0, 1.0, 5.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static int cylinderRadiusHorizontal;
        public static int cylinderRadiusVertical;
        public static int cylinderForwardShift;
        public static int miningCylinderRadius;
        public static int miningCylinderForwardShift;
        public static boolean clickToMoveEnabled;
        public static double arrivalThreshold;
        public static boolean forceAutoJump;
        public static boolean autoAlignToMovementEnabled;
        public static int autoAlignAngleThreshold;
        public static int autoAlignCooldownTicks;
        public static int stableDirectionAngle;
        public static int stableDirectionTicks;
        public static double autoAlignAnimationSpeed;
        public static boolean trapdoorTranslucencyEnabled;
        public static double trapdoorTransparency;
        public static boolean fadeEnabled;
        public static boolean fadeBlockRaycastProtection;
        public static double fadeStart;
        public static double fadeNearAlpha;

        public static boolean rangeIndicatorEnabled;

        public static double rangeEmptyHand;
        public static double rangeSword;
        public static double rangeAxe;
        public static double rangePickaxe;
        public static double rangeShovel;
        public static double rangeOther;

        public static boolean defaultEnabled;

	public static int rotateAngleMode;
	public static double cameraPitch;
	public static double miningModePitch;
	public static double maxCameraDistance;
	public static double defaultCameraDistance;

	// Drag rotation settings
	public static boolean dragRotationEnabled;
	public static double dragRotationSensitivity;

	// public static int pathfindingRange;
        // public static int pathRecalcCooldown;
        // public static boolean pathfindingEnabled;
        // public static double avoidanceRadius;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
	cylinderRadiusHorizontal = CYLINDER_RADIUS_HORIZONTAL.get();
			cylinderRadiusVertical = CYLINDER_RADIUS_VERTICAL.get();
			cylinderForwardShift = CYLINDER_FORWARD_SHIFT.get();
			miningCylinderRadius = MINING_CYLINDER_RADIUS.get();
			miningCylinderForwardShift = MINING_CYLINDER_FORWARD_SHIFT.get();
			clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.get();
                arrivalThreshold = ARRIVAL_THRESHOLD.get();
                forceAutoJump = FORCE_AUTO_JUMP.get();
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

                // ゲーム起動時にデフォルト状態を適用
                com.topdownview.state.ModStatus.INSTANCE.setEnabled(defaultEnabled);

                notifyConfigChanged();
        }

        public static void save() {
                CYLINDER_RADIUS_HORIZONTAL.set(cylinderRadiusHorizontal);
                CYLINDER_RADIUS_VERTICAL.set(cylinderRadiusVertical);
                CYLINDER_FORWARD_SHIFT.set(cylinderForwardShift);
            MINING_CYLINDER_RADIUS.set(miningCylinderRadius);
            MINING_CYLINDER_FORWARD_SHIFT.set(miningCylinderForwardShift);
            CLICK_TO_MOVE_ENABLED.set(clickToMoveEnabled);
                ARRIVAL_THRESHOLD.set(arrivalThreshold);
                FORCE_AUTO_JUMP.set(forceAutoJump);
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
	SPEC.save();
                notifyConfigChanged();
        }
}
