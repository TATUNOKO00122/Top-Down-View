package com.topdownview.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.topdownview.state.ModState;
import com.topdownview.TopDownViewMod;
import com.topdownview.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class InputHandler {

    private static final long DOUBLE_CLICK_THRESHOLD_MS = 300;
    private static long lastFreeCameraClickTime = 0;

    private InputHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null)
            return;

        int keyCode = event.getKey();
        
        // フリーカメラキー（キーボードの場合のみ処理）
        if (isKeyboardKey(ClientModBusEvents.FREE_CAMERA_KEY)) {
            int freeCameraKeyCode = ClientModBusEvents.FREE_CAMERA_KEY.getKey().getValue();
            if (keyCode == freeCameraKeyCode && ModState.STATUS.isEnabled()) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastFreeCameraClickTime < DOUBLE_CLICK_THRESHOLD_MS) {
                        resetFreeCameraAngles();
                        lastFreeCameraClickTime = 0;
                    } else {
                        startFreeCameraMode(mc);
                        lastFreeCameraClickTime = currentTime;
                    }
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    stopFreeCameraMode(mc);
                }
                return;
            }
        }

        if (event.getAction() != GLFW.GLFW_PRESS)
            return;
        handleInput(keyCode, InputConstants.Type.KEYSYM);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null)
            return;

        int button = event.getButton();

        // フリーカメラキー（マウスボタンの場合のみ処理）
        if (isMouseButton(ClientModBusEvents.FREE_CAMERA_KEY) && ModState.STATUS.isEnabled()) {
            int freeCameraButton = ClientModBusEvents.FREE_CAMERA_KEY.getKey().getValue();
            if (button == freeCameraButton) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastFreeCameraClickTime < DOUBLE_CLICK_THRESHOLD_MS) {
                        resetFreeCameraAngles();
                        lastFreeCameraClickTime = 0;
                    } else {
                        startFreeCameraMode(mc);
                        lastFreeCameraClickTime = currentTime;
                    }
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    stopFreeCameraMode(mc);
                }
                return;
            }
        }

        // ドラッグ回転の処理
        if (ModState.STATUS.isEnabled() && Config.isDragRotationEnabled()) {
            int dragButton = ClientModBusEvents.DRAG_ROTATE_KEY.getKey().getValue();

            if (event.getButton() == dragButton) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    startDragRotation(mc);
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    stopDragRotation();
                }
            }
        }

        if (event.getAction() == GLFW.GLFW_PRESS) {
            handleInput(button, InputConstants.Type.MOUSE);
        }
    }

    private static boolean isKeyboardKey(KeyMapping keyMapping) {
        return keyMapping.getKey().getType() == InputConstants.Type.KEYSYM;
    }

    private static boolean isMouseButton(KeyMapping keyMapping) {
        return keyMapping.getKey().getType() == InputConstants.Type.MOUSE;
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

        double sensitivity = mc.options.sensitivity().get();
        double f = sensitivity * 0.6 + 0.2;
        double multiplier = f * f * f * 8.0;
        double guiScale = mc.getWindow().getGuiScale();
        double rotationDelta = deltaX * multiplier / guiScale;

        float newYaw = ModState.CAMERA.getDragStartYaw() + (float) rotationDelta;
        ModState.CAMERA.setYaw(newYaw);

        // 継続的なドラッグのため、開始位置を更新
        ModState.CAMERA.setDragStartYaw(newYaw);
        ModState.CAMERA.setDragStartMouseX(currentMouseX);
    }

    private static void startDragRotation(Minecraft mc) {
        // Rキーの回転アニメーションをキャンセル
        ModState.CAMERA.setAnimating(false);
        ModState.CAMERA.setDragging(true);
        ModState.CAMERA.setDragStartYaw(ModState.CAMERA.getYaw());
        ModState.CAMERA.setDragStartMouseX(mc.mouseHandler.xpos());
        // ドラッグ中はカーソルを非表示にする
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
    }

    private static void stopDragRotation() {
        // ドラッグ終了時に回転アニメーションをキャンセル
        ModState.CAMERA.setAnimating(false);
        ModState.CAMERA.setDragging(false);
        // カーソルを通常モードに戻す
        Minecraft mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
    }

    private static void startFreeCameraMode(Minecraft mc) {
        // Rキーの回転アニメーションをキャンセル
        ModState.CAMERA.setAnimating(false);
        // フリーカム開始時に前回値を初期化（補間用）
        ModState.CAMERA.updatePrevYaw();
        ModState.CAMERA.updatePrevFreeCameraPitch();
        ModState.CAMERA.setFreeCameraMode(true);
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
    }

    private static void stopFreeCameraMode(Minecraft mc) {
        // フリーカム終了時に回転アニメーションをキャンセル
        ModState.CAMERA.setAnimating(false);
        ModState.CAMERA.setFreeCameraMode(false);
        ModState.CAMERA.setFreeCameraMouseInitialized(false);
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
    }

    private static void resetFreeCameraAngles() {
        ModState.CAMERA.setFreeCameraPitch((float) com.topdownview.Config.getCameraPitch());
        ModState.CAMERA.setFreeCameraPitchAdjusted(false);
        ModState.CAMERA.updatePrevFreeCameraPitch();
    }

    private static void handleInput(int keyCode, InputConstants.Type inputType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        
        // 各キーマッピングと比較（タイプも考慮）
        if (matchesKeyBinding(ClientModBusEvents.TOGGLE_VIEW_KEY, keyCode, inputType)) {
            toggleTopDownView();
        } else if (ModState.STATUS.isEnabled() && matchesKeyBinding(ClientModBusEvents.ROTATE_VIEW_KEY, keyCode, inputType)) {
            CameraController.rotateCamera();
        } else if (ModState.STATUS.isEnabled() && matchesKeyBinding(ClientModBusEvents.ALIGN_TO_MOVEMENT_KEY, keyCode, inputType)) {
            CameraController.alignCameraToMovementImmediate();
        } else if (ModState.STATUS.isEnabled() && Config.isMiningModeEnabled() && matchesKeyBinding(ClientModBusEvents.MINING_MODE_KEY, keyCode, inputType)) {
            toggleMiningMode();
        } else if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled() && matchesKeyBinding(mc.options.keyJump, keyCode, inputType)) {
            ClickToMoveController.reset();
        }
    }

    private static boolean matchesKeyBinding(KeyMapping keyMapping, int keyCode, InputConstants.Type inputType) {
        InputConstants.Key key = keyMapping.getKey();
        return key.getType() == inputType && key.getValue() == keyCode;
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
        boolean isFreeCameraMode = ModState.CAMERA.isFreeCameraMode();

        if (isZoomModifierDown || isFreeCameraMode) {
            double newDistance = ModState.CAMERA.getCameraDistance() - scroll * 1.5;
            double maxDistance = com.topdownview.state.CameraState.getEffectiveMaxCameraDistance();
            double clampedDistance = Math.max(com.topdownview.state.CameraState.MIN_CAMERA_DISTANCE,
                    Math.min(newDistance, maxDistance));
            ModState.CAMERA.setCameraDistance(clampedDistance);
            event.setCanceled(true);
        }
    }
}
