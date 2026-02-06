package com.example.examplemod;

import com.example.examplemod.state.CameraState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Camera settings
    private static final ForgeConfigSpec.DoubleValue CAMERA_DISTANCE = BUILDER
            .comment("Default camera distance from player")
            .defineInRange("cameraDistance", CameraState.DEFAULT_CAMERA_DISTANCE, CameraState.MIN_CAMERA_DISTANCE, CameraState.MAX_CAMERA_DISTANCE);

    private static final ForgeConfigSpec.DoubleValue CAMERA_PITCH = BUILDER
            .comment("Camera pitch angle in degrees (0 = horizontal, 90 = top-down)")
            .defineInRange("cameraPitch", CameraState.DEFAULT_PITCH, 0.0, 90.0);

    private static final ForgeConfigSpec.DoubleValue CAMERA_YAW = BUILDER
            .comment("Camera yaw angle in degrees (0 = north)")
            .defineInRange("cameraYaw", CameraState.DEFAULT_YAW, -180.0, 180.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Runtime values
    public static double cameraDistance;
    public static double cameraPitch;
    public static double cameraYaw;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        cameraDistance = CAMERA_DISTANCE.get();
        cameraPitch = CAMERA_PITCH.get();
        cameraYaw = CAMERA_YAW.get();
    }
}