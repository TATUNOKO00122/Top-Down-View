package com.topdownview;

import com.mojang.logging.LogUtils;
import com.topdownview.network.PacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(TopDownViewMod.MODID)
public class TopDownViewMod {
    public static final String MODID = "topdown_view";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger getLogger() {
        return LOGGER;
    }

    public TopDownViewMod(FMLJavaModLoadingContext context) {
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        context.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        Config.registerConfigChangeListener(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.topdownview.culling.TopDownCuller.getInstance().clearCache();
                com.topdownview.client.ReachManager.forceUpdate();
            }
            LOGGER.info("TopDownView cache cleared due to config change");
        });

        context.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (mc, lastScreen) -> new com.topdownview.client.gui.ConfigScreen(lastScreen)));

        context.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> "IGNORE_ALL_VERSION",
                        (remoteVersion, fromServer) -> true));

        context.getModEventBus().addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            LOGGER.info("[TopDownView] Network channel registered");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[TopDownView] Mod loaded on server side");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("TopDownView mod client setup");
            MinecraftForge.EVENT_BUS.register(com.topdownview.client.ClientForgeEvents.class);
            LOGGER.info("ClientForgeEvents registered");
        }
    }
}