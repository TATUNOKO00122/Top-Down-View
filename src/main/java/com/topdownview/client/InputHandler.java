package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.TopDownViewMod;
import com.topdownview.Config;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class InputHandler {

    private InputHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS)
            return;
        handleInput(event.getKey());
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        // ドラッグ回転の処理
        if (ModState.STATUS.isEnabled() && Config.isDragRotationEnabled()) {
            int dragButton = ClientModBusEvents.DRAG_ROTATE_KEY.getKey().getValue();

            if (event.getButton() == dragButton) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    startDragRotation(mc);
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    stopDragRotation();
                }
                // ドラッグボタンのイベントはキャンセルしない（他の機能との競合を避ける）
            }
        }

        // 既存のボタン処理
        if (event.getAction() == GLFW.GLFW_PRESS) {
            handleInput(event.getButton());
        }
    }

    /**
     * ドラッグ回転の更新処理（ClientTickEventから呼び出し）
     */
    public static void updateDragRotation(Minecraft mc) {
        if (!ModState.STATUS.isEnabled())
            return;
        if (!Config.isDragRotationEnabled())
            return;
        if (!ModState.CAMERA.isDragging())
            return;
        if (mc.screen != null)
            return;

        double currentMouseX = mc.mouseHandler.xpos();
        double deltaX = currentMouseX - ModState.CAMERA.getDragStartMouseX();

        // 感度を適用して回転角度を計算
        double sensitivity = Config.getDragRotationSensitivity();
        double rotationDelta = deltaX * sensitivity;

        float newYaw = ModState.CAMERA.getDragStartYaw() + (float) rotationDelta;
        ModState.CAMERA.setYaw(newYaw);

        // 継続的なドラッグのため、開始位置を更新
        ModState.CAMERA.setDragStartYaw(newYaw);
        ModState.CAMERA.setDragStartMouseX(currentMouseX);
    }

    private static void startDragRotation(Minecraft mc) {
        ModState.CAMERA.setDragging(true);
        ModState.CAMERA.setDragStartYaw(ModState.CAMERA.getYaw());
        ModState.CAMERA.setDragStartMouseX(mc.mouseHandler.xpos());
        // ドラッグ中はカーソルを非表示にする
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
    }

    private static void stopDragRotation() {
        ModState.CAMERA.setDragging(false);
        // カーソルを通常モードに戻す
        Minecraft mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
    }

    private static void handleInput(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        int toggleKeyCode = ClientModBusEvents.TOGGLE_VIEW_KEY.getKey().getValue();
        int rotateKeyCode = ClientModBusEvents.ROTATE_VIEW_KEY.getKey().getValue();
        int alignKeyCode = ClientModBusEvents.ALIGN_TO_MOVEMENT_KEY.getKey().getValue();
        int miningModeKeyCode = ClientModBusEvents.MINING_MODE_KEY.getKey().getValue();
        int jumpKeyCode = mc.options.keyJump.getKey().getValue();

        if (keyCode == toggleKeyCode) {
            toggleTopDownView();
        } else if (ModState.STATUS.isEnabled() && keyCode == rotateKeyCode) {
            CameraController.rotateCamera();
        } else if (ModState.STATUS.isEnabled() && keyCode == alignKeyCode) {
            CameraController.alignCameraToMovementImmediate();
        } else if (ModState.STATUS.isEnabled() && Config.isMiningModeEnabled() && keyCode == miningModeKeyCode) {
            toggleMiningMode();
        } else if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled() && keyCode == jumpKeyCode) {
            ClickToMoveController.reset();
        }
    }

    private static void toggleTopDownView() {
        boolean newState = !ModState.STATUS.isEnabled();
        ModState.STATUS.setEnabled(newState);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        if (newState) {
            CameraController.initializeTopDownView(mc);
        } else {
            CameraController.disableTopDownView(mc);
        }
    }

    private static void toggleMiningMode() {
        boolean newState = !ModState.STATUS.isMiningMode();
        ModState.STATUS.setMiningMode(newState);
        // カリングキャッシュをクリアして表示を更新
        com.topdownview.culling.CullingManager.reset();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!ModState.STATUS.isEnabled())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null)
            return;

        double scroll = event.getScrollDelta();
        if (scroll == 0)
            return;

        boolean isZoomModifierDown = ClientModBusEvents.ZOOM_MODIFIER_KEY.isDown();

        if (isZoomModifierDown) {
            double newDistance = ModState.CAMERA.getCameraDistance() - scroll * 1.5;
            double maxDistance = com.topdownview.state.CameraState.getEffectiveMaxCameraDistance();
            double clampedDistance = Math.max(com.topdownview.state.CameraState.MIN_CAMERA_DISTANCE,
                    Math.min(newDistance, maxDistance));
            ModState.CAMERA.setCameraDistance(clampedDistance);
            event.setCanceled(true);
        }
    }
}
