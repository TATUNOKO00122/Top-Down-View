package com.topdownview;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        // Camera settings
        private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
                        .comment("カメラのピッチ（角度）。0 = 水平、90 = 真上から")
                        .defineInRange("cameraPitch", 45.0, 0.0, 90.0);

        private static final ForgeConfigSpec.DoubleValue CAMERA_YAW = BUILDER
                        .comment("カメラのヨー（方位）。0 = 北")
                        .defineInRange("cameraYaw", 0.0, -180.0, 180.0);

        private static final ForgeConfigSpec.IntValue CEILING_HEIGHT = BUILDER
                        .comment("プレイヤー周囲円柱での天井カリング高さ（ブロック数）")
                        .defineInRange("ceilingHeight", 2, 0, 10);

        private static final ForgeConfigSpec.IntValue BASE_PROTECTION_HEIGHT = BUILDER
                        .comment("足元からの全体保護高さ（この高さ以下は無条件で表示）")
                        .defineInRange("baseProtectionHeight", 1, 0, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS = BUILDER
                        .comment("シリンダーキャストの半径（ブロック数）")
                        .defineInRange("cylinderRadius", 3, 1, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_EXTENSION = BUILDER
                        .comment("プレイヤー位置からの延長距離（ブロック数）")
                        .defineInRange("cylinderExtension", 5, 0, 20);

        private static final ForgeConfigSpec.DoubleValue HYSTERESIS_THRESHOLD = BUILDER
                        .comment("カリング基準点の更新閾値（ブロック数）。小さいほど追従性が高いがチカチカしやすい")
                        .defineInRange("hysteresisThreshold", 1.0, 1.0, 3.0);

        private static final ForgeConfigSpec.DoubleValue PROTECTION_SLOPE = BUILDER
                        .comment("保護高さの傾斜（距離1ブロックあたりの保護高さ増加分）。大きいほど遠くまで高く保護")
                        .defineInRange("protectionSlope", 1.2, 0.0, 5.0);

        private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
                        .comment("クリックツームーブ機能の有効/無効")
                        .define("clickToMoveEnabled", true);

        private static final ForgeConfigSpec.DoubleValue ARRIVAL_THRESHOLD = BUILDER
                        .comment("目的地到達判定の距離（ブロック数）")
                        .defineInRange("arrivalThreshold", 1.5, 0.5, 5.0);

        private static final ForgeConfigSpec.DoubleValue ATTACK_RANGE = BUILDER
                        .comment("エンティティ攻撃実行距離（ブロック数）")
                        .defineInRange("attackRange", 3.0, 1.0, 6.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static double cameraPitch;
        public static double cameraYaw;
        public static int ceilingHeight;
        public static int baseProtectionHeight;
        public static int cylinderRadius;
        public static int cylinderExtension;
        public static double hysteresisThreshold;
        public static double protectionSlope;
        public static boolean clickToMoveEnabled;
        public static double arrivalThreshold;
        public static double attackRange;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                cameraPitch = CAMERA_PITCH.get();
                cameraYaw = CAMERA_YAW.get();
                ceilingHeight = CEILING_HEIGHT.get();
                baseProtectionHeight = BASE_PROTECTION_HEIGHT.get();
                cylinderRadius = CYLINDER_RADIUS.get();
                cylinderExtension = CYLINDER_EXTENSION.get();
                hysteresisThreshold = HYSTERESIS_THRESHOLD.get();
                protectionSlope = PROTECTION_SLOPE.get();
                clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.get();
                arrivalThreshold = ARRIVAL_THRESHOLD.get();
                attackRange = ATTACK_RANGE.get();
        }

        public static void save() {
                CAMERA_PITCH.set(cameraPitch);
                CAMERA_YAW.set(cameraYaw);
                CEILING_HEIGHT.set(ceilingHeight);
                BASE_PROTECTION_HEIGHT.set(baseProtectionHeight);
                CYLINDER_RADIUS.set(cylinderRadius);
                CYLINDER_EXTENSION.set(cylinderExtension);
                HYSTERESIS_THRESHOLD.set(hysteresisThreshold);
                PROTECTION_SLOPE.set(protectionSlope);
                CLICK_TO_MOVE_ENABLED.set(clickToMoveEnabled);
                ARRIVAL_THRESHOLD.set(arrivalThreshold);
                ATTACK_RANGE.set(attackRange);
                SPEC.save();
        }
}
