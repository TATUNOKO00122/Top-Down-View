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

        private static final ForgeConfigSpec.DoubleValue CULLING_RANGE = BUILDER
                        .comment("カリングを適用する範囲")
                        .defineInRange("cullingRange", 20.0, 1.0, 100.0);

        private static final ForgeConfigSpec.IntValue CULLING_HEIGHT_THRESHOLD = BUILDER
                        .comment("カリング保護の高さ（プレイヤーの足元からこの高さ以下は常に表示）")
                        .defineInRange("cullingHeightThreshold", 2, 0, 10);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static double cameraPitch;
        public static double cameraYaw;
        public static double cullingRange;
        public static int cullingHeightThreshold;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                cameraPitch = CAMERA_PITCH.get();
                cameraYaw = CAMERA_YAW.get();
                cullingRange = CULLING_RANGE.get();
                cullingHeightThreshold = CULLING_HEIGHT_THRESHOLD.get();
        }

        public static void save() {
                CAMERA_PITCH.set(cameraPitch);
                CAMERA_YAW.set(cameraYaw);
                CULLING_RANGE.set(cullingRange);
                CULLING_HEIGHT_THRESHOLD.set(cullingHeightThreshold);
                SPEC.save();
        }
}
