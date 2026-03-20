package com.topdownview.client;

import com.topdownview.TopDownViewMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModBusEvents {
    
    // KeyMapping must be registered on the MOD bus
    public static final KeyMapping TOGGLE_VIEW_KEY = new KeyMapping(
            "key.topdown_view.toggle",
            GLFW.GLFW_KEY_F4,
            "key.categories.topdown_view");

    public static final KeyMapping ROTATE_VIEW_KEY = new KeyMapping(
            "key.topdown_view.rotate",
            GLFW.GLFW_KEY_R,
            "key.categories.topdown_view");

    public static final KeyMapping ZOOM_MODIFIER_KEY = new KeyMapping(
            "key.topdown_view.zoom_modifier",
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.topdown_view");

    public static final KeyMapping ALIGN_TO_MOVEMENT_KEY = new KeyMapping(
            "key.topdown_view.align_to_movement",
            GLFW.GLFW_KEY_V,
            "key.categories.topdown_view");

    public static final KeyMapping DRAG_ROTATE_KEY = new KeyMapping(
            "key.topdown_view.drag_rotate",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.topdown_view");

    public static final KeyMapping MINING_MODE_KEY = new KeyMapping(
            "key.topdown_view.mining_mode",
            GLFW.GLFW_KEY_M,
            "key.categories.topdown_view");

    public static final KeyMapping DESTROY_KEY = new KeyMapping(
            "key.topdown_view.destroy",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.topdown_view");

    public static final KeyMapping FREE_CAMERA_KEY = new KeyMapping(
            "key.topdown_view.free_camera",
            GLFW.GLFW_KEY_C,
            "key.categories.topdown_view");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_VIEW_KEY);
        event.register(ROTATE_VIEW_KEY);
        event.register(ZOOM_MODIFIER_KEY);
        event.register(ALIGN_TO_MOVEMENT_KEY);
        event.register(DRAG_ROTATE_KEY);
        event.register(MINING_MODE_KEY);
        event.register(DESTROY_KEY);
        event.register(FREE_CAMERA_KEY);
    }


}
