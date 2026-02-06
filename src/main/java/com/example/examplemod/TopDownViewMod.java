package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TopDownViewMod.MODID)
public class TopDownViewMod {
    public static final String MODID = "topdown_view";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TopDownViewMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("TopDownView mod server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("TopDownView mod client setup");
        }
    }
}
