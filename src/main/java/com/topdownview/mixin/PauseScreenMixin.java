package com.topdownview.mixin;

import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PauseScreenに設定ボタンを追加するMixin
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_SPACING = 5;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void onCreatePauseMenu(CallbackInfo ci) {
        this.renderables.stream()
            .filter(renderable -> renderable instanceof Button)
            .map(renderable -> (Button) renderable)
            .filter(button -> button.getX() > this.width / 2)
            .filter(button -> button.getY() > this.height - 60)
            .max(java.util.Comparator.comparingInt(Button::getX))
            .ifPresent(targetButton -> {
                int x = targetButton.getX() - BUTTON_SIZE - BUTTON_SPACING;
                int y = targetButton.getY();

                Button configButton = Button.builder(
                        Component.literal("⚙"),
                        (button) -> Minecraft.getInstance().setScreen(new ConfigScreen(this)))
                        .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                        .build();

                this.addRenderableWidget(configButton);
            });
    }
}
