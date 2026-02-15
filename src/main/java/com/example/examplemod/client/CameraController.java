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
        if (event.phase != TickEvent.Phase.END)
            return;
        if (!ClientForgeEvents.isTopDownView())
            return;

        // 次のティックの計算前に、現在の角度を保存しておく
        ModState.CAMERA.updatePrevYaw();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        if (mc.isPaused())
            return;

        // アニメーションの更新
        updateAnimation();
        // 通常のマウス位置への回転
        updatePlayerRotationToMouse(mc);
    }

    /**
     * 回転アニメーションを更新
     */
    private static void updateAnimation() {
        if (!ModState.CAMERA.isAnimating())
            return;

        float currentYaw = ModState.CAMERA.getYaw();
        float targetYaw = ModState.CAMERA.getTargetYaw();

        // 角度の差分を計算（最短距離で回転）
        float diff = targetYaw - currentYaw;
        while (diff < -180.0f)
            diff += 360.0f;
        while (diff > 180.0f)
            diff -= 360.0f;

        if (Math.abs(diff) < 0.1f) {
            ModState.CAMERA.setYaw(targetYaw);
            ModState.CAMERA.setAnimating(false);
        } else {
            // 線形補間（Lerp）による滑らかな回転
            float lerpStrength = 0.2f;
            ModState.CAMERA.setYaw(currentYaw + diff * lerpStrength);
        }
    }

    /**
     * マウスの位置に応じて、カメラを 90 度または 180 度回転させる
     */
    public static void rotateCamera90Degrees() {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        boolean isLeftSide = mouseX < (screenWidth / 2.0);
        // 画面下部（下から20%以内）にあるか判定
        boolean isBottomSide = mouseY > (screenHeight * 0.8);

        float baseYaw = ModState.CAMERA.isAnimating() ? ModState.CAMERA.getTargetYaw() : ModState.CAMERA.getYaw();

        // 目的地（nextYaw）の計算
        float nextYaw;
        if (isBottomSide) {
            // 下部：180度回転（反対側へスナップ）
            // 現在の 90 度区切りから +180 度
            nextYaw = (float) (Math.round(baseYaw / 90.0) * 90.0 + 180.0);
        } else if (isLeftSide) {
            // 左側：反時計回り
            nextYaw = (float) (Math.ceil(baseYaw / 90.0) * 90.0 - 90.0);
        } else {
            // 右側：時計回り
            nextYaw = (float) (Math.floor(baseYaw / 90.0) * 90.0 + 90.0);
        }

        ModState.CAMERA.setTargetYaw(nextYaw);
        ModState.CAMERA.setAnimating(true);
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
                MouseRaycast.getCustomReachDistance());

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
