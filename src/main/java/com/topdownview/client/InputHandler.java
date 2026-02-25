package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.TopDownViewMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class InputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        int toggleKeyCode = ClientModBusEvents.TOGGLE_VIEW_KEY.getKey().getValue();
        int rotateKeyCode = ClientModBusEvents.ROTATE_VIEW_KEY.getKey().getValue();

        if (event.getKey() == toggleKeyCode) {
            toggleTopDownView();
        } else if (ClientForgeEvents.isTopDownView() && event.getKey() == rotateKeyCode) {
            CameraController.rotateCamera90Degrees();
        }
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
        ModState.CAMERA.setPreviousCameraType(mc.options.getCameraType());
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.mouseHandler.releaseMouse();
        ModState.resetAll();
        ModState.STATUS.setEnabled(true);
        if (mc.level != null) {
            ModState.TIME.setStartTime(mc.level.getGameTime());
        }
    }

    private static void disableTopDownView(Minecraft mc) {
        CameraType restoreType = ModState.CAMERA.getPreviousCameraType();
        if (restoreType == null) {
            restoreType = CameraType.FIRST_PERSON;
        }
        mc.options.setCameraType(restoreType);
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
        boolean isZoomModifierDown = ClientModBusEvents.ZOOM_MODIFIER_KEY.isDown();

        if (isZoomModifierDown) {
            double newDistance = ModState.CAMERA.getCameraDistance() - scroll * 1.5;
            double clampedDistance = Math.max(com.topdownview.state.CameraState.MIN_CAMERA_DISTANCE,
                    Math.min(newDistance, com.topdownview.state.CameraState.MAX_CAMERA_DISTANCE));
            ModState.CAMERA.setCameraDistance(clampedDistance);
            event.setCanceled(true);
        }
    }
}
