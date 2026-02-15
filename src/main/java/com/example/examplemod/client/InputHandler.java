package com.example.examplemod.client;

import com.example.examplemod.state.ModState;
import com.example.examplemod.TopDownViewMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class InputHandler {

    // キー状態追跡用
    private static boolean wasToggleKeyDown = false;
    private static boolean wasRotateKeyDown = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        // Toggleキー (F4) - 生のキー状態で判定（修飾キーの影響を受けない）
        boolean isToggleDown = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_F4) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (isToggleDown && !wasToggleKeyDown) {
            toggleTopDownView();
        }
        wasToggleKeyDown = isToggleDown;

        // Rotateキー (R) - 生のキー状態で判定（修飾キーの影響を受けない）
        boolean isRotateDown = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_R) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (ClientForgeEvents.isTopDownView() && isRotateDown && !wasRotateKeyDown) {
            CameraController.rotateCamera90Degrees();
        }
        wasRotateKeyDown = isRotateDown;
    }

    private static void toggleTopDownView() {
        ClientForgeEvents.setTopDownView(!ClientForgeEvents.isTopDownView());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        mc.player.displayClientMessage(
                Component.literal("Top-Down View: " + (ClientForgeEvents.isTopDownView() ? "ON" : "OFF")),
                true);

        if (ClientForgeEvents.isTopDownView()) {
            enableTopDownView(mc);
        } else {
            disableTopDownView(mc);
        }
    }

    private static void enableTopDownView(Minecraft mc) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.mouseHandler.releaseMouse();
        ModState.resetAll();
        ModState.STATUS.setEnabled(true);
        if (mc.level != null) {
            ModState.TIME.setStartTime(mc.level.getGameTime());
        }
    }

    private static void disableTopDownView(Minecraft mc) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.mouseHandler.grabMouse();
        ModState.resetAll();
        ModState.STATUS.setEnabled(false);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!ClientForgeEvents.isTopDownView())
            return;

        double scroll = event.getScrollDelta();
        if (scroll == 0)
            return;

        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();
        boolean isAltDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(handle,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) ||
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);

        if (isAltDown) {
            double newDistance = ClientForgeEvents.getCameraDistance() - scroll * 1.5;
            double clampedDistance = Math.max(ClientForgeEvents.getMinCameraDistance(),
                    Math.min(newDistance, ClientForgeEvents.getMaxCameraDistance()));
            ClientForgeEvents.setCameraDistance(clampedDistance);
            event.setCanceled(true);
        }
    }
}
