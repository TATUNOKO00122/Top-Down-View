package com.topdownview.client;

import com.mojang.logging.LogUtils;
import com.topdownview.TopDownViewMod;
import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
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
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // PauseScreen以外は無視
        if (!(event.getScreen() instanceof PauseScreen screen)) {
            return;
        }
        
        LOGGER.info("PauseScreen initialized, screen size: {}x{}", screen.width, screen.height);
        
        // デバッグ: すべてのボタンをログ出力（childrenとrenderablesの両方を確認）
        LOGGER.info("Children count: {}", screen.children().size());
        LOGGER.info("Renderables count: {}", screen.renderables.size());
        
        // renderablesからボタンを探す（PauseScreenではこちらにボタンが入っている）
        screen.renderables.stream()
            .filter(renderable -> renderable instanceof Button)
            .map(renderable -> (Button) renderable)
            .forEach(button -> LOGGER.info("Button found: '{}' at ({}, {})", 
                button.getMessage().getString(), button.getX(), button.getY()));
        
        // 画面右下エリアのボタンを特定（renderablesから検索）
        screen.renderables.stream()
            .filter(renderable -> renderable instanceof Button)
            .map(renderable -> (Button) renderable)
            .filter(button -> button.getX() > screen.width / 2)  // 右半分のボタン
            .filter(button -> button.getY() > screen.height - 60)  // 下部のボタン
            .max(java.util.Comparator.comparingInt(Button::getX))  // 最も右にあるボタン
            .ifPresentOrElse(rightmostBottomButton -> {
                LOGGER.info("Target button found at ({}, {})", rightmostBottomButton.getX(), rightmostBottomButton.getY());
                // オプションボタンの左隣に設定ボタンを配置
                int x = rightmostBottomButton.getX() - BUTTON_SIZE - BUTTON_SPACING;
                int y = rightmostBottomButton.getY();
                
                LOGGER.info("Adding config button at ({}, {})", x, y);
                
                Button configButton = Button.builder(
                        Component.literal("⚙"),
                        (button) -> {
                            LOGGER.info("Config button clicked");
                            Minecraft.getInstance().setScreen(new ConfigScreen(screen));
                        })
                        .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                        .build();
                
                // ScreenEvent.Init.Post では addListener を使用してボタンを追加
                event.addListener(configButton);
                LOGGER.info("Config button added to screen");
            }, () -> LOGGER.warn("No suitable button found in bottom-right area"));
    }
}
