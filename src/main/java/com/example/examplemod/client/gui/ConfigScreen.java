package com.example.examplemod.client.gui;

import com.example.examplemod.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * ゲーム内設定画面
 * スライダーで設定値を変更可能
 */
public class ConfigScreen extends Screen {
    private final Screen lastScreen;

    public ConfigScreen(Screen lastScreen) {
        super(Component.literal("トップダウンビュー設定"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int width = 200;
        int height = 20;
        int spacing = 24;
        int startY = this.height / 4;
        int x = this.width / 2 - width / 2;

        // Camera Distance Slider
        this.addRenderableWidget(new ConfigSlider(x, startY, width, height, "デフォルト距離", Config.cameraDistance,
                1.0, 100.0, (value) -> {
                    Config.cameraDistance = value;
                }));

        // Min Distance Slider
        this.addRenderableWidget(new ConfigSlider(x, startY + spacing, width, height, "最小距離",
                Config.minCameraDistance, 1.0, 50.0, (value) -> {
                    Config.minCameraDistance = value;
                }));

        // Max Distance Slider
        this.addRenderableWidget(new ConfigSlider(x, startY + spacing * 2, width, height, "最大距離",
                Config.maxCameraDistance, 10.0, 200.0, (value) -> {
                    Config.maxCameraDistance = value;
                }));

        // Culling Range Slider
        this.addRenderableWidget(new ConfigSlider(x, startY + spacing * 3, width, height, "カリング範囲",
                Config.cullingRange, 1.0, 100.0, (value) -> {
                    Config.cullingRange = value;
                }));

        // Save & Done Button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(x, startY + spacing * 5, width, height).build());
    }

    private void saveConfig() {
        // 現在のランタイム値をConfigSpecに反映させて保存
        com.example.examplemod.Config.save();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    /**
     * 汎用的な設定用スライダー
     */
    private static class ConfigSlider extends AbstractSliderButton {
        private final String prefix;
        private final double min;
        private final double max;
        private final java.util.function.Consumer<Double> setter;

        public ConfigSlider(int x, int y, int width, int height, String prefix, double current, double min, double max,
                java.util.function.Consumer<Double> setter) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double value = min + (max - min) * this.value;
            this.setMessage(Component.literal(String.format("%s: %.1f", prefix, value)));
        }

        @Override
        protected void applyValue() {
            double newValue = min + (max - min) * this.value;
            setter.accept(newValue);
        }
    }
}
