package com.example.examplemod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        // Camera settings
        private static final ForgeConfigSpec.DoubleValue CAMERA_DISTANCE = BUILDER
                        .comment("プレイヤーからのデフォルトのカメラ距離")
                        .defineInRange("cameraDistance", 9.0, 1.0, 100.0);

        private static final ForgeConfigSpec.DoubleValue MIN_CAMERA_DISTANCE = BUILDER
                        .comment("カメラの最小距離")
                        .defineInRange("minCameraDistance", 5.0, 1.0, 100.0);

        private static final ForgeConfigSpec.DoubleValue MAX_CAMERA_DISTANCE = BUILDER
                        .comment("カメラの最大距離")
                        .defineInRange("maxCameraDistance", 50.0, 1.0, 200.0);

        private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
                        .comment("カメラのピッチ（角度）。0 = 水平、90 = 真上から")
                        .defineInRange("cameraPitch", 45.0, 0.0, 90.0);

        private static final ForgeConfigSpec.DoubleValue CAMERA_YAW = BUILDER
                        .comment("カメラのヨー（方位）。0 = 北")
                        .defineInRange("cameraYaw", 0.0, -180.0, 180.0);

        private static final ForgeConfigSpec.DoubleValue CULLING_RANGE = BUILDER
                        .comment("カリングを適用する範囲")
                        .defineInRange("cullingRange", 20.0, 1.0, 100.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static double cameraDistance;
        public static double minCameraDistance;
        public static double maxCameraDistance;
        public static double cameraPitch;
        public static double cameraYaw;
        public static double cullingRange;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                cameraDistance = CAMERA_DISTANCE.get();
                minCameraDistance = MIN_CAMERA_DISTANCE.get();
                maxCameraDistance = MAX_CAMERA_DISTANCE.get();
                cameraPitch = CAMERA_PITCH.get();
                cameraYaw = CAMERA_YAW.get();
                cullingRange = CULLING_RANGE.get();
        }

        public static void save() {
                CAMERA_DISTANCE.set(cameraDistance);
                MIN_CAMERA_DISTANCE.set(minCameraDistance);
                MAX_CAMERA_DISTANCE.set(maxCameraDistance);
                CAMERA_PITCH.set(cameraPitch);
                CAMERA_YAW.set(cameraYaw);
                CULLING_RANGE.set(cullingRange);
                SPEC.save();
        }
}
