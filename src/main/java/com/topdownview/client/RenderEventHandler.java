package com.topdownview.client;

import com.topdownview.state.ModState;
import com.topdownview.TopDownViewMod;
import com.topdownview.placement.PlacementRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class RenderEventHandler {

    private RenderEventHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 空間デバッグはトップダウン有無に関係なく動作
        if (ModState.SPACE_DEBUG.isEnabled()) {
            SpaceDebugRenderer.onRenderLevelStage(event);
        }

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            TranslucentBlockRenderer.renderFadeBlocks(event);
            DestinationHighlightRenderer.onRenderLevelStage(event);
            PlacementRenderer.onRenderLevelStage(event);
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            TargetHighlightRenderer.onRenderLevelStage(event);
            SignHoverRenderer.onRenderLevelStage(event);
            InteractionPromptRenderer.onRenderLevelStage(event);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ModState.SPACE_DEBUG.isEnabled()) {
            SpaceDebugRenderer.onRenderGui(event);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        SignHoverRenderer.onRenderGuiPost(event);
    }
}
