package com.topdownview.mixin;

import com.mojang.logging.LogUtils;
import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PauseScreenに設定ボタンを追加するMixin
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_SPACING = 5;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void onCreatePauseMenu(CallbackInfo ci) {
        LOGGER.info("[TopDownView] PauseScreenMixin.onCreatePauseMenu called! Screen size: {}x{}", this.width, this.height);
        LOGGER.info("[TopDownView] Renderables count: {}", this.renderables.size());
        
        // 全てのボタンをログ出力
        this.renderables.stream()
            .filter(renderable -> renderable instanceof Button)
            .map(renderable -> (Button) renderable)
            .forEach(button -> LOGGER.info("[TopDownView] Button: '{}' at ({}, {})", 
                button.getMessage().getString(), button.getX(), button.getY()));
        
        // 画面右下エリアのボタンを特定
        var buttonOpt = this.renderables.stream()
            .filter(renderable -> renderable instanceof Button)
            .map(renderable -> (Button) renderable)
            .filter(button -> button.getX() > this.width / 2)  // 右半分
            .filter(button -> button.getY() > this.height - 60)  // 下部
            .max(java.util.Comparator.comparingInt(Button::getX));  // 最も右

        if (buttonOpt.isPresent()) {
            Button targetButton = buttonOpt.get();
            int x = targetButton.getX() - BUTTON_SIZE - BUTTON_SPACING;
            int y = targetButton.getY();
            
            LOGGER.info("[TopDownView] Adding config button at ({}, {})", x, y);

            Button configButton = Button.builder(
                    Component.literal("⚙"),
                    (button) -> {
                        LOGGER.info("[TopDownView] Config button clicked!");
                        Minecraft.getInstance().setScreen(new ConfigScreen(this));
                    })
                    .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                    .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                    .build();

            this.addRenderableWidget(configButton);
            LOGGER.info("[TopDownView] Config button added successfully!");
        } else {
            LOGGER.warn("[TopDownView] No suitable button found in bottom-right area");
        }
    }
}
