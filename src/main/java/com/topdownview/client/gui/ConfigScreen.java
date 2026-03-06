package com.topdownview.client.gui;

import com.topdownview.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Tooltip;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    private int currentTab = 0;
    private static final int TAB_GENERAL = 0;
    private static final int TAB_CULLING = 1;
    private static final int TAB_MOVEMENT = 2;
    private static final int TAB_CAMERA = 3;
    private static final int TAB_VISUAL = 4;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("topdown_view.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int contentWidth = 310;
        int columnWidth = 150;
        int height = 20;
        int spacing = 24;
        int startY = 60;
        int startX = this.width / 2 - contentWidth / 2;
        int tabCount = 5;
        int tabWidth = 60;

        // タブボタンを横並びに配置
        int tabStartX = this.width / 2 - (tabWidth * tabCount + (tabCount - 1) * 2) / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.general"),
                (button) -> switchTab(TAB_GENERAL))
                .bounds(tabStartX, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.culling"),
                (button) -> switchTab(TAB_CULLING))
                .bounds(tabStartX + tabWidth + 2, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.movement"),
                (button) -> switchTab(TAB_MOVEMENT))
                .bounds(tabStartX + (tabWidth + 2) * 2, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.camera"),
                (button) -> switchTab(TAB_CAMERA))
                .bounds(tabStartX + (tabWidth + 2) * 3, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.visual"),
                (button) -> switchTab(TAB_VISUAL))
                .bounds(tabStartX + (tabWidth + 2) * 4, 25, tabWidth, height).build());

        switch (currentTab) {
            case TAB_GENERAL -> initGeneralTab(startX, startY, columnWidth, height, spacing);
            case TAB_CULLING -> initCullingTab(startX, startY, columnWidth, height, spacing);
            case TAB_MOVEMENT -> initMovementTab(startX, startY, columnWidth, height, spacing);
            case TAB_CAMERA -> initCameraTab(startX, startY, columnWidth, height, spacing);
            case TAB_VISUAL -> initVisualTab(startX, startY, columnWidth, height, spacing);
        }
    }

    private void initGeneralTab(int startX, int startY, int columnWidth, int height, int spacing) {
        int y = startY;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.default_enabled",
                        Config.defaultEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.defaultEnabled = !Config.defaultEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.default_enabled",
                            Config.defaultEnabled ? "ON" : "OFF"));
                }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.default_enabled.tooltip")))
                .build());
        y += spacing * 2;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height).build());
    }

    private void initCullingTab(int startX, int startY, int columnWidth, int height, int spacing) {
        int y = startY;
        int col1 = startX;
        int col2 = startX + columnWidth + 10;

        this.addRenderableWidget(
                new IntConfigSlider(col1, y, columnWidth, height, "topdown_view.config.cylinder_radius_horizontal",
                        Config.cylinderRadiusHorizontal, 1, 10, (value) -> Config.cylinderRadiusHorizontal = value));
        this.addRenderableWidget(
                new IntConfigSlider(col2, y, columnWidth, height, "topdown_view.config.cylinder_radius_vertical",
                        Config.cylinderRadiusVertical, 1, 10, (value) -> Config.cylinderRadiusVertical = value));
        y += spacing;

        this.addRenderableWidget(new IntConfigSlider(col1, y, columnWidth, height,
                "topdown_view.config.cylinder_forward_shift",
                Config.cylinderForwardShift, 0, 10, (value) -> Config.cylinderForwardShift = value));
        y += spacing;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height).build());
    }

    private void initMovementTab(int startX, int startY, int columnWidth, int height, int spacing) {
        int y = startY;
        int col1 = startX;
        int col2 = startX + columnWidth + 10;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.click_to_move",
                        Config.clickToMoveEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.clickToMoveEnabled = !Config.clickToMoveEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.click_to_move",
                            Config.clickToMoveEnabled ? "ON" : "OFF"));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.click_to_move.tooltip")))
                .build());
        this.addRenderableWidget(
                new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.arrival_threshold",
                        Config.arrivalThreshold, 0.5, 5.0, (value) -> Config.arrivalThreshold = value));
        y += spacing;

        // 武器種別射程設定
        this.addRenderableWidget(
                new ConfigSlider(col1, y, columnWidth, height, "topdown_view.config.range_empty_hand",
                        Config.rangeEmptyHand, 1.0, 10.0, (value) -> Config.rangeEmptyHand = value));
        this.addRenderableWidget(
                new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.range_sword",
                        Config.rangeSword, 1.0, 10.0, (value) -> Config.rangeSword = value));
        y += spacing;

        this.addRenderableWidget(
                new ConfigSlider(col1, y, columnWidth, height, "topdown_view.config.range_axe",
                        Config.rangeAxe, 1.0, 10.0, (value) -> Config.rangeAxe = value));
        this.addRenderableWidget(
                new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.range_pickaxe",
                        Config.rangePickaxe, 1.0, 10.0, (value) -> Config.rangePickaxe = value));
        y += spacing;

        this.addRenderableWidget(
                new ConfigSlider(col1, y, columnWidth, height, "topdown_view.config.range_shovel",
                        Config.rangeShovel, 1.0, 10.0, (value) -> Config.rangeShovel = value));
        this.addRenderableWidget(
                new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.range_other",
                        Config.rangeOther, 1.0, 10.0, (value) -> Config.rangeOther = value));
        y += spacing;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height).build());
    }

    private void initCameraTab(int startX, int startY, int columnWidth, int height, int spacing) {
        int y = startY;
        int col1 = startX;
        int col2 = startX + columnWidth + 10;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.auto_align_to_movement",
                        Config.autoAlignToMovementEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.autoAlignToMovementEnabled = !Config.autoAlignToMovementEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.auto_align_to_movement",
                            Config.autoAlignToMovementEnabled ? "ON" : "OFF"));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.auto_align_to_movement.tooltip")))
                .build());
        this.addRenderableWidget(new IntConfigSlider(col2, y, columnWidth, height,
                "topdown_view.config.auto_align_angle_threshold",
                Config.autoAlignAngleThreshold, 0, 90, (value) -> Config.autoAlignAngleThreshold = value));
        y += spacing;

        this.addRenderableWidget(new IntConfigSlider(col1, y, columnWidth, height,
                "topdown_view.config.auto_align_cooldown_ticks",
                Config.autoAlignCooldownTicks, 0, 100, (value) -> Config.autoAlignCooldownTicks = value));
        this.addRenderableWidget(new IntConfigSlider(col2, y, columnWidth, height,
                "topdown_view.config.stable_direction_angle",
                Config.stableDirectionAngle, 5, 60, (value) -> Config.stableDirectionAngle = value));
        y += spacing;

        this.addRenderableWidget(new IntConfigSlider(col1, y, columnWidth, height,
                "topdown_view.config.stable_direction_ticks",
                Config.stableDirectionTicks, 5, 60, (value) -> Config.stableDirectionTicks = value));
        this.addRenderableWidget(new ConfigSlider(col2, y, columnWidth, height,
                "topdown_view.config.auto_align_animation_speed",
                Config.autoAlignAnimationSpeed, 0.05, 0.5, (value) -> Config.autoAlignAnimationSpeed = value));
        y += spacing;

        // ドラッグ回転設定
        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.drag_rotation_enabled",
                        Config.dragRotationEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.dragRotationEnabled = !Config.dragRotationEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.drag_rotation_enabled",
                            Config.dragRotationEnabled ? "ON" : "OFF"));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.drag_rotation_enabled.tooltip")))
                .build());
        this.addRenderableWidget(new ConfigSlider(col2, y, columnWidth, height,
                "topdown_view.config.drag_rotation_sensitivity",
                Config.dragRotationSensitivity, 0.1, 2.0, (value) -> Config.dragRotationSensitivity = value));
        y += spacing;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height).build());
    }

    private void initVisualTab(int startX, int startY, int columnWidth, int height, int spacing) {
        int y = startY;
        int col1 = startX;
        int col2 = startX + columnWidth + 10;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.fade_enabled",
                        Config.fadeEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.fadeEnabled = !Config.fadeEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.fade_enabled",
                            Config.fadeEnabled ? "ON" : "OFF"));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.fade_enabled.tooltip")))
                .build());
        this.addRenderableWidget(new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.fade_start",
                Config.fadeStart, 0.0, 0.9, (value) -> Config.fadeStart = value));
        y += spacing;

        this.addRenderableWidget(
                new ConfigSlider(col1, y, columnWidth, height, "topdown_view.config.fade_near_alpha",
                        Config.fadeNearAlpha, 0.0, 1.0, (value) -> Config.fadeNearAlpha = value));
        y += spacing;

        // 射程外ターゲット赤表示設定
        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.range_indicator_enabled",
                        Config.rangeIndicatorEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.rangeIndicatorEnabled = !Config.rangeIndicatorEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.range_indicator_enabled",
                            Config.rangeIndicatorEnabled ? "ON" : "OFF"));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.range_indicator_enabled.tooltip")))
                .build());
        y += spacing;

        this.addRenderableWidget(Button.builder(
                getRotateModeComponent(Config.rotateAngleMode),
                (button) -> {
                    Config.rotateAngleMode = (Config.rotateAngleMode + 1) % 3;
                    button.setMessage(getRotateModeComponent(Config.rotateAngleMode));
                }).bounds(col1, y, columnWidth, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.rotate_angle_mode.tooltip")))
                .build());
        this.addRenderableWidget(
                new ConfigSlider(col2, y, columnWidth, height, "topdown_view.config.camera_pitch",
                        Config.cameraPitch, 10.0, 90.0, (value) -> Config.cameraPitch = value));
        y += spacing;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - columnWidth / 2, y, columnWidth, height).build());
    }

    private Component getRotateModeComponent(int mode) {
        String modeKey = switch (mode) {
            case 1 -> "mode_45";
            case 2 -> "mode_15";
            default -> "mode_90";
        };
        return Component.translatable("topdown_view.config.rotate_angle_mode",
                Component.translatable("topdown_view.config.rotate_angle_mode." + modeKey).getString());
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        this.clearWidgets();
        this.init();
    }

    private void saveConfig() {
        Config.save();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private static class ConfigSlider extends AbstractSliderButton {
        private final String translationKey;
        private final double min;
        private final double max;
        private final java.util.function.Consumer<Double> setter;

        public ConfigSlider(int x, int y, int width, int height, String translationKey, double current, double min,
                double max,
                java.util.function.Consumer<Double> setter) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.setTooltip(Tooltip.create(Component.translatable(translationKey + ".tooltip")));
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
            newValue = Math.round(newValue * 10.0) / 10.0;
            setter.accept(newValue);
        }
    }

    private static class IntConfigSlider extends AbstractSliderButton {
        private final String translationKey;
        private final int min;
        private final int max;
        private final java.util.function.Consumer<Integer> setter;

        public IntConfigSlider(int x, int y, int width, int height, String translationKey, int current, int min,
                int max,
                java.util.function.Consumer<Integer> setter) {
            super(x, y, width, height, Component.empty(), (double) (current - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.setTooltip(Tooltip.create(Component.translatable(translationKey + ".tooltip")));
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
