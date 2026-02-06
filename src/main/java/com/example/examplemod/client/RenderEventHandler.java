package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class RenderEventHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (ClientForgeEvents.isTopDownView()) {
            TargetHighlightRenderer.onRenderLevelStage(event);
        }
    }
}
