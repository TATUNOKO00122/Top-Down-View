package com.topdownview.client;

import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントサイドイベントハンドラ
 * ポーズ画面への設定ボタン追加など
 */
@Mod.EventBusSubscriber(modid = com.topdownview.TopDownViewMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {

    // Configボタン用のアイコンテクスチャ（バニラのアクセシビリティアイコンを流用）
    private static final ResourceLocation CONFIG_ICON = new ResourceLocation(
            "minecraft", "textures/gui/accessibility.png");

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            // Optionsボタンの横に配置
            // PauseScreenのデフォルトのOptionsボタンは width / 2 - 102, height / 4 + 72 + -16
            // そのすぐ左に配置する
            int buttonSize = 20;
            int x = pauseScreen.width / 2 - 102 - buttonSize - 4; // Optionsボタンの左隣、4pxのマージン
            int y = pauseScreen.height / 4 + 72 + -16; // PauseScreen固有のボタン配置Y座標ベース

            ImageButton configButton = new ImageButton(
                    x, y, buttonSize, buttonSize,
                    0, 0, 20,
                    CONFIG_ICON, 32, 64,
                    (button) -> {
                        pauseScreen.getMinecraft().setScreen(new ConfigScreen(pauseScreen));
                    },
                    Component.translatable("topdown_view.config.title"));

            configButton.setTooltip(Tooltip.create(Component.translatable("topdown_view.config.title")));

            event.addListener(configButton);
        }
    }
}
