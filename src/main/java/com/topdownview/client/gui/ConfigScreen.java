package com.topdownview.client.gui;

import com.topdownview.Config;
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
        super(Component.translatable("topdown_view.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int width = 200;
        int height = 20;
        int spacing = 24;
        int startY = this.height / 4;
        int x = this.width / 2 - width / 2;

        // Culling Range Slider
        this.addRenderableWidget(new ConfigSlider(x, startY, width, height, "topdown_view.config.culling_range",
                Config.cullingRange, 1.0, 100.0, (value) -> {
                    Config.cullingRange = value;
                }));

        // Culling Height Threshold Slider (integer)
        this.addRenderableWidget(new IntConfigSlider(x, startY + spacing, width, height, "topdown_view.config.culling_height_threshold",
                Config.cullingHeightThreshold, 0, 10, (value) -> {
                    Config.cullingHeightThreshold = value;
                }));

        // Save & Done Button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(x, startY + spacing * 2, width, height).build());
    }

    private void saveConfig() {
        // 現在のランタイム値をConfigSpecに反映させて保存
        com.topdownview.Config.save();
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
        private final String translationKey;
        private final double min;
        private final double max;
        private final java.util.function.Consumer<Double> setter;

        public ConfigSlider(int x, int y, int width, int height, String translationKey, double current, double min, double max,
                java.util.function.Consumer<Double> setter) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double value = min + (max - min) * this.value;
            this.setMessage(Component.translatable(translationKey, String.format("%.1f", value)));
        }

        @Override
        protected void applyValue() {
            double newValue = min + (max - min) * this.value;
            setter.accept(newValue);
        }
    }

    private static class IntConfigSlider extends AbstractSliderButton {
        private final String translationKey;
        private final int min;
        private final int max;
        private final java.util.function.Consumer<Integer> setter;

        public IntConfigSlider(int x, int y, int width, int height, String translationKey, int current, int min, int max,
                java.util.function.Consumer<Integer> setter) {
            super(x, y, width, height, Component.empty(), (double) (current - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = min + (int) Math.round((max - min) * this.value);
            this.setMessage(Component.translatable(translationKey, value));
        }

        @Override
        protected void applyValue() {
            int newValue = min + (int) Math.round((max - min) * this.value);
            setter.accept(newValue);
        }
    }
}
