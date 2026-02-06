package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
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

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_VIEW_KEY);
    }
}