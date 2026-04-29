package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.state.CameraState;
import com.topdownview.TopDownViewMod;
import com.topdownview.culling.CullingManager;
import com.topdownview.util.MathConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    private CameraController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ModState.STATUS.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (ModState.STATUS.isEnabled()) {
            event.setNewFovModifier(1.0f);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        // 初回初期化処理（設定でdefaultEnabled=trueの場合）
        if (ModState.STATUS.isEnabled() && !ModState.STATUS.isInitialized()) {
            if (mc.player != null && mc.level != null) {
                com.topdownview.TopDownViewMod.getLogger().info("[TopDownView][CameraController] Auto-initializing top-down view");
                initializeTopDownView(mc);
            }
        }

        if (!ModState.STATUS.isEnabled())
            return;

        // 次のティックの計算前に、現在の角度を保存しておく
        ModState.CAMERA.updatePrevYaw();

        if (mc.player == null)
            return;
        if (mc.isPaused())
            return;

        // ドラッグ回転の処理
        InputHandler.updateDragRotation(mc);

        // アニメーションの更新
        updateAnimation();

        // プレイヤー回転制御
        updatePlayerRotationToMouse(mc);

        // 動的カメラ回転（アニメーション中でない場合のみ）
        if (com.topdownview.Config.isAutoAlignToMovementEnabled() && !ModState.CAMERA.isAnimating()) {
            alignCameraToMovement();
        }
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
float diff = CameraState.normalizeAngle(targetYaw - currentYaw);

        if (Math.abs(diff) < 0.1f) {
            ModState.CAMERA.setYaw(targetYaw);
            ModState.CAMERA.setAnimating(false);
            ModState.CAMERA.setAutoAlignAnimation(false);
        } else {
            // 線形補間（Lerp）による滑らかな回転
            float lerpStrength = ModState.CAMERA.isAutoAlignAnimation()
                    ? (float) com.topdownview.Config.getAutoAlignAnimationSpeed()
                    : (float) com.topdownview.Config.getCameraSnapRotationSpeed();
            ModState.CAMERA.setYaw(currentYaw + diff * lerpStrength);
        }
    }

    /**
     * マウスの位置に応じて、カメラを 90 度または 180 度回転させる
     */
    public static void rotateCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler == null || mc.getWindow() == null) {
            return;
        }
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        boolean isLeftSide = mouseX < (screenWidth / 2.0);
        // 画面下部（下から20%以内）にあるか判定
        boolean isBottomSide = mouseY > (screenHeight * 0.8);

        float baseYaw = ModState.CAMERA.isAnimating() ? ModState.CAMERA.getTargetYaw() : ModState.CAMERA.getYaw();

        // 目的地（nextYaw）の計算
        double step = switch (com.topdownview.Config.getRotateAngleMode()) {
            case 1 -> 45.0;
            case 2 -> 15.0;
            default -> 90.0;
        };

        float nextYaw;
        if (isBottomSide) {
            // 画面下部: 180度回転
            nextYaw = (float) (Math.round(baseYaw / step) * step + 180.0);
        } else if (isLeftSide) {
            // 左側: 反時計回り（-step）
            nextYaw = (float) (Math.ceil(baseYaw / step) * step - step);
            if (nextYaw > baseYaw + 0.1f) {
                nextYaw -= step;
            }
        } else {
            // 右側: 時計回り（+step）
            nextYaw = (float) (Math.floor(baseYaw / step) * step + step);
            if (nextYaw < baseYaw - 0.1f) {
                nextYaw += step;
            }
        }

        ModState.CAMERA.setTargetYaw(nextYaw);
        ModState.CAMERA.setAutoAlignAnimation(false);
        ModState.CAMERA.setAnimating(true);
    }

    /**
     * トップダウンビューの初期化（カメラタイプ、マウス、時刻設定）
     */
    public static void initializeTopDownView(Minecraft mc) {
        if (mc.options == null || mc.mouseHandler == null) {
            com.topdownview.TopDownViewMod.getLogger().warn("[TopDownView][CameraController] Cannot initialize: options or mouseHandler is null");
            return;
        }
        ModState.CAMERA.setPreviousCameraType(mc.options.getCameraType());
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.mouseHandler.grabMouse();
        if (mc.level != null) {
            ModState.TIME.setStartTime(mc.level.getGameTime());
        }
        // カメラ距離をデフォルト値で初期化
        try {
            double defaultDistance = com.topdownview.state.CameraState.getEffectiveDefaultCameraDistance();
            ModState.CAMERA.setCameraDistance(defaultDistance);
        } catch (IllegalArgumentException e) {
            com.topdownview.TopDownViewMod.getLogger().warn("[TopDownView][CameraController] Failed to set default camera distance, using fallback: {}", e.getMessage());
            ModState.CAMERA.setCameraDistance(com.topdownview.state.CameraState.DEFAULT_CAMERA_DISTANCE);
        }
        ModState.STATUS.setInitialized(true);
    }

    /**
     * トップダウンビューを無効化
     */
    public static void disableTopDownView(Minecraft mc) {
        if (mc.options == null || mc.mouseHandler == null) {
            com.topdownview.TopDownViewMod.getLogger().warn("[TopDownView][CameraController] Cannot disable: options or mouseHandler is null");
            ModState.resetAll();
            return;
        }
        CameraType restoreType = ModState.CAMERA.getPreviousCameraType();
        if (restoreType == null) {
            restoreType = CameraType.FIRST_PERSON;
        }
        mc.options.setCameraType(restoreType);
        mc.mouseHandler.releaseMouse();
        mc.mouseHandler.grabMouse();
        ModState.resetAll();
        CullingManager.forceChunkRebuild(mc);
    }

    /**
     * 移動方向にカメラを即座に整列（キー押下時用）
     */
    public static void alignCameraToMovementImmediate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 velocity = mc.player.getDeltaMovement();
        double dx = velocity.x;
        double dz = velocity.z;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        if (horizontalSpeed < MOVEMENT_THRESHOLD) {
            return;
        }
        float moveDirection = (float) (Math.atan2(dz, dx) * MathConstants.RADIANS_TO_DEGREES);
        float targetYaw = moveDirection - 90.0f;
        alignToYaw(targetYaw);
    }

    private static void alignToYaw(float targetYaw) {
        float currentYaw = ModState.CAMERA.getYaw();
        float angleDiff = CameraState.normalizeAngle(targetYaw - currentYaw);
        
        if (Math.abs(angleDiff) < 5.0f) {
            ModState.CAMERA.setYaw(targetYaw);
        } else {
            ModState.CAMERA.setTargetYaw(targetYaw);
            ModState.CAMERA.setAutoAlignAnimation(true);
            ModState.CAMERA.setAnimating(true);
        }
    }

    private static final double MOVEMENT_THRESHOLD = 0.01;

    public static void alignCameraToMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // クールダウンチェック
        long currentTick = mc.level.getGameTime();
        long ticksSinceLastAlign = currentTick - ModState.CAMERA.getLastAutoAlignTick();
        if (ticksSinceLastAlign < com.topdownview.Config.getAutoAlignCooldownTicks()) return;

        Vec3 velocity = mc.player.getDeltaMovement();
        double dx = velocity.x;
        double dz = velocity.z;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        // 移動速度チェック
        if (horizontalSpeed < MOVEMENT_THRESHOLD) {
            ModState.CAMERA.setStableDirectionTicks(0);
            return;
        }

        float currentDirection = (float) (Math.atan2(dz, dx) * MathConstants.RADIANS_TO_DEGREES);

        // 方向安定性チェック
        float directionDiff = CameraState.normalizeAngle(currentDirection - ModState.CAMERA.getLastMovementDirection());

        if (Math.abs(directionDiff) <= com.topdownview.Config.getStableDirectionAngle()) {
            ModState.CAMERA.setStableDirectionTicks(ModState.CAMERA.getStableDirectionTicks() + 1);
        } else {
            ModState.CAMERA.setStableDirectionTicks(0);
        }
        ModState.CAMERA.setLastMovementDirection(currentDirection);

        if (ModState.CAMERA.getStableDirectionTicks() < com.topdownview.Config.getStableDirectionTicks()) return;

        float targetYaw = currentDirection - 90.0f;

        // 角度差チェック
        float currentYaw = ModState.CAMERA.getYaw();
        float angleDiff = CameraState.normalizeAngle(targetYaw - currentYaw);

        if (Math.abs(angleDiff) < com.topdownview.Config.getAutoAlignAngleThreshold()) return;

        ModState.CAMERA.setTargetYaw(targetYaw);
        ModState.CAMERA.setAutoAlignAnimation(true);
        ModState.CAMERA.setAnimating(true);
        ModState.CAMERA.setLastAutoAlignTick(currentTick);
        ModState.CAMERA.setStableDirectionTicks(0);
    }

    /**
     * プレイヤーの回転をマウス位置に合わせて更新
     */
    public static void updatePlayerRotationToMouse(Minecraft mc) {
        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        if (mc.player == null) {
            return;
        }

        if (mc.player.isPassenger() || mc.player.isFallFlying()) {
            return;
        }

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 targetPos = hitResult.getLocation();
            Vec3 playerEyePos = mc.player.getEyePosition(mc.getFrameTime());

            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;

            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) (Math.atan2(dz, dx) * MathConstants.RADIANS_TO_DEGREES) - 90.0f;

            mc.player.setYRot(yaw);
            mc.player.yHeadRot = yaw;
            mc.player.yBodyRot = yaw;

            Float pitch = calculatePitch(mc, horizontalDist, dy);
            if (pitch != null) {
                mc.player.setXRot(pitch);
            }
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
            if (yawPitch == null) return;

            float yaw = yawPitch[0];
            mc.player.setYRot(yaw);
            mc.player.yHeadRot = yaw;
            mc.player.yBodyRot = yaw;
            mc.player.setXRot(yawPitch[1]);
        }
    }

    private static final double MIN_PULL_FACTOR = 0.65;

    private static Float calculatePitch(Minecraft mc, double horizontalDist, double verticalDist) {
        ItemStack useItem = mc.player.getUseItem();
        Item item = useItem.getItem();
        ProjectilePhysics physics = ProjectilePhysics.fromItem(item);
        
        if (physics != null && mc.player.isUsingItem()) {
            int useTicks = mc.player.getTicksUsingItem();
            double pullFactor = TrajectoryCalculator.calculatePullFactor(item, useTicks);
            if (pullFactor >= MIN_PULL_FACTOR) {
                return TrajectoryCalculator.calculatePitch(physics, horizontalDist, verticalDist, pullFactor);
            }
            return null;
        }
        return (float) -(Math.atan2(verticalDist, horizontalDist) * MathConstants.RADIANS_TO_DEGREES);
    }
}

