package com.topdownview.client;

import com.mojang.logging.LogUtils;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import com.topdownview.Config;
import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forgeバスのクライアントサイドイベントハンドラ
 * ポーズ画面へのボタン追加など
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_SPACING = 5;

    private ClientForgeEvents() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("[TopDownView] Player joined world, resetting state");
        ModState.resetAll();
        ModState.STATUS.setEnabled(Config.isDefaultEnabled());
        
        if (Minecraft.getInstance().player != null) {
            PlayerRotationController.initializeFromPlayer(Minecraft.getInstance().player);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) {
            return;
        }

        int x = screen.width / 2 - 102 - BUTTON_SIZE - 4;
        int y = screen.height / 4 + 72 - 21;

        Button configButton = Button.builder(
                Component.literal("⚙"),
                (button) -> Minecraft.getInstance().setScreen(new ConfigScreen(screen)))
                .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                .build();

        event.addListener(configButton);
    }
}
