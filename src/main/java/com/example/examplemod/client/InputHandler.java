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

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ClientModBusEvents.TOGGLE_VIEW_KEY.consumeClick()) {
            toggleTopDownView();
        }
    }

    private static void toggleTopDownView() {
        ClientForgeEvents.setTopDownView(!ClientForgeEvents.isTopDownView());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(
            Component.literal("Top-Down View: " + (ClientForgeEvents.isTopDownView() ? "ON" : "OFF")),
            true
        );

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
        if (!ClientForgeEvents.isTopDownView()) return;

        double scroll = event.getScrollDelta();
        if (scroll == 0) return;

        double newDistance = ClientForgeEvents.getCameraDistance() - scroll * 1.5;
        double clampedDistance = Math.max(ClientForgeEvents.MIN_CAMERA_DISTANCE, 
                                          Math.min(newDistance, ClientForgeEvents.MAX_CAMERA_DISTANCE));
        ClientForgeEvents.setCameraDistance(clampedDistance);
        event.setCanceled(true);
    }
}