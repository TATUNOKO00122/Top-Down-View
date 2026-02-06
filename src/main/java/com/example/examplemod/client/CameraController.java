package com.example.examplemod.client;

import com.example.examplemod.state.ModState;
import com.example.examplemod.TopDownViewMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * カメラ制御クラス
 * 責務：カメラ関連のイベント処理とプレイヤー回転制御
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class CameraController {

    // 定数
    public static final float DEFAULT_CAMERA_YAW = 0.0f;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    private static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
    
    // 状態
    private static float cameraYaw = DEFAULT_CAMERA_YAW;

    private CameraController() {
        throw new AssertionError("ユーティリティクラスはインスタンス化できません");
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ClientForgeEvents.isTopDownView()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (ClientForgeEvents.isTopDownView()) {
            event.setNewFovModifier(1.0f);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientForgeEvents.isTopDownView()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.isPaused()) return;

        updatePlayerRotationToMouse(mc);
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    /**
     * プレイヤーの回転をマウス位置に合わせて更新
     */
    public static void updatePlayerRotationToMouse(Minecraft mc) {
        HitResult hitResult = MouseRaycast.getHitResult(
            mc, 
            mc.getFrameTime(), 
            MouseRaycast.getCustomReachDistance()
        );
        
        if (hitResult == null || mc.player == null) {
            return;
        }
        
        Vec3 targetPos = hitResult.getLocation();
        Vec3 playerEyePos = mc.player.getEyePosition(mc.getFrameTime());
        
        double dx = targetPos.x - playerEyePos.x;
        double dy = targetPos.y - playerEyePos.y;
        double dz = targetPos.z - playerEyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * RADIANS_TO_DEGREES) - 90.0f;
        cameraYaw = yaw;

        float pitch = calculatePitch(mc, horizontalDist, dy);

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yHeadRot = yaw;
        mc.player.yBodyRot = yaw;
    }

    private static float calculatePitch(Minecraft mc, double horizontalDist, double verticalDist) {
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BowItem) {
            return BowTrajectoryCalculator.calculateBowPitch(horizontalDist, verticalDist);
        }
        return (float) -(Math.atan2(verticalDist, horizontalDist) * RADIANS_TO_DEGREES);
    }
}
