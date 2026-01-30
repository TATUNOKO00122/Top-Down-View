package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ComputeFovModifierEvent;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    public static boolean isTopDownView = false;
    public static double cameraDistance = 15.0D;
    public static boolean isBlockCullingEnabled = true;

    // Mixinで参照する定数などがあればここに定義してもよいが、Mixin側で独立して計算しているため不要

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ClientModBusEvents.TOGGLE_VIEW_KEY.consumeClick()) {
            isTopDownView = !isTopDownView;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Top-Down View: " + (isTopDownView ? "ON" : "OFF")),
                        true);
                if (isTopDownView) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    // マウスフリーは行わない（エイムに使うため、カーソルは隠れたままが良い？いや、POEだと見える）
                    // Minecraft標準では視点移動＝マウス移動。
                    // "Mouse Aim"にするなら、視点（カメラ）は固定で、マウスカーソルが画面上を動く必要がある。
                    // そのためには `mc.mouseHandler.releaseMouse()` が必要。
                    mc.mouseHandler.releaseMouse();
                } else {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    mc.mouseHandler.grabMouse();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isTopDownView) {
            double scroll = event.getScrollDelta();
            if (scroll != 0) {
                cameraDistance -= scroll * 1.5;
                cameraDistance = Math.max(2.0, Math.min(cameraDistance, 50.0));
                event.setCanceled(true);
            }
        }
    }

    // 3人称視点ベースなら手は元々映らないが、念のため
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isTopDownView) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (isTopDownView) {
            event.setNewFovModifier(1.0f);
        }
    }

    // マウスエイム（プレイヤーの向き更新）
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (!isTopDownView || mc.player == null)
            return;

        // GUIが開いている場合は、マウス操作（エイム・クリック）を無効化する
        if (mc.screen != null)
            return;

        // マウス解放を維持
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        updatePlayerRotationToMouse(mc);

        // ClickActionHandler.onClientTick(mc); // バニラの入力処理に任せるため無効化
    }

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (isTopDownView) {
            TargetHighlightRenderer.onRenderLevelStage(event);
        }
    }

    // onMouseInput was removed to allow vanilla handling

    private static void updatePlayerRotationToMouse(Minecraft mc) {
        // マウス位置からのレイキャストを行い、3次元空間上のターゲット位置を取得する
        HitResult hitResult = MouseRaycast.getHitResult(mc, mc.getFrameTime(), MouseRaycast.CUSTOM_REACH_DISTANCE);
        Vec3 targetPos = hitResult.getLocation();

        Vec3 playerEyePos = mc.player.getEyePosition(mc.getFrameTime());
        double dx = targetPos.x - playerEyePos.x;
        double dy = targetPos.y - playerEyePos.y;
        double dz = targetPos.z - playerEyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Yawの計算 (atan2(z, x) - 90度)
        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        float pitch;

        // 弓を使用中の場合は弾道計算を適用
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BowItem) {
            // 弓の弾道補正を適用
            pitch = calculateBowPitch(horizontalDist, dy);
        } else {
            // 通常の照準（直線）
            pitch = (float) -(Math.atan2(dy, horizontalDist) * (180D / Math.PI));
        }

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yHeadRot = yaw;
        mc.player.yBodyRot = yaw;
    }

    /**
     * 弓の弾道を考慮したピッチ角を計算する
     * 
     * Minecraftの矢の物理:
     * - 初速度: 最大チャージで約3.0ブロック/tick (60ブロック/秒)
     * - 重力加速度: 0.05ブロック/tick² (20ブロック/秒²)
     * - 空気抵抗: 速度 *= 0.99 毎tick (無視可能なレベル)
     * 
     * 放物線運動の方程式:
     * y = x * tan(θ) - (g * x²) / (2 * v² * cos²(θ))
     * 
     * ターゲットに到達するための発射角度θを求める
     */
    private static float calculateBowPitch(double horizontalDist, double verticalDist) {
        // 矢の物理パラメータ (1秒 = 20tick)
        double arrowSpeed = 3.0 * 20.0; // ブロック/秒に変換
        double gravity = 0.05 * 20.0 * 20.0; // ブロック/秒²に変換

        double v = arrowSpeed;
        double g = gravity;
        double x = horizontalDist;
        double y = verticalDist;

        // 二次方程式の解を求める
        // 発射角度θを求めるための式:
        // tan(θ) = (v² ± √(v⁴ - g(gx² + 2yv²))) / (gx)

        double v2 = v * v;
        double v4 = v2 * v2;
        double gx = g * x;
        double discriminant = v4 - g * (g * x * x + 2.0 * y * v2);

        if (discriminant < 0) {
            // 到達不可能 - 直線照準にフォールバック
            return (float) -(Math.atan2(y, x) * (180D / Math.PI));
        }

        double sqrtDisc = Math.sqrt(discriminant);

        // 低弾道（フラット）と高弾道（アーチ）の2つの解がある
        // ゲームプレイ的には低弾道を使う方が直感的
        double tanTheta = (v2 - sqrtDisc) / gx;

        double theta = Math.atan(tanTheta);
        float pitchDegrees = (float) (-theta * (180D / Math.PI));

        // ピッチを妥当な範囲にクランプ (-90 to 90)
        return Mth.clamp(pitchDegrees, -90.0F, 90.0F);
    }

    // WASD移動をカメラ基準（ワールド座標）に固定
    // プレイヤーがどの方向を向いていても、WASDは常に：
    // W=北（画面上）、S=南（画面下）、A=西（画面左）、D=東（画面右）
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isTopDownView)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // カメラは南向き（Yaw=0）で固定
        // 入力の意味（画面基準）：
        // W (forward>0) → 北(-Z)へ, S (forward<0) → 南(+Z)へ
        // A (strafe>0) → 西(-X)へ, D (strafe<0) → 東(+X)へ
        //
        // ワールド方向ベクトル: dx = -strafe, dz = -forward
        //
        // プレイヤーがYaw=θを向いている時、ワールド方向(dx,dz)に動くには：
        // newForward = -dx * sin(θ) + dz * cos(θ)
        // newStrafe = dx * cos(θ) + dz * sin(θ)

        float forward = event.getInput().forwardImpulse;
        float strafe = event.getInput().leftImpulse;

        // ワールド方向に変換
        float dx = strafe;
        float dz = forward;

        float playerYaw = mc.player.getYRot();
        float rad = (float) Math.toRadians(playerYaw);
        float c = Mth.cos(rad);
        float s = Mth.sin(rad);

        // プレイヤーローカル座標に変換
        float newForward = -dx * s + dz * c;
        float newStrafe = dx * c + dz * s;

        event.getInput().forwardImpulse = newForward;
        event.getInput().leftImpulse = newStrafe;
    }
}
