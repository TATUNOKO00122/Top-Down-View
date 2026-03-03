package com.topdownview;

import com.mojang.logging.LogUtils;
import com.topdownview.culling.TopDownCuller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TopDownViewMod.MODID)
public class TopDownViewMod {
    public static final String MODID = "topdown_view";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TopDownViewMod(FMLJavaModLoadingContext context) {
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        Config.registerConfigChangeListener(() -> {
            TopDownCuller.getInstance().clearCache();
            LOGGER.info("TopDownView cache cleared due to config change");
        });

        context.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (mc, lastScreen) -> new com.topdownview.client.gui.ConfigScreen(lastScreen)));
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
