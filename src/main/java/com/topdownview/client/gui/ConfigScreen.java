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
    private static final int TAB_CULLING = 0;
    private static final int TAB_MOVEMENT = 1;
    private static final int TAB_VISUAL = 2;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("topdown_view.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int width = 200;
        int height = 20;
        int spacing = 24;
        int startY = 60;
        int x = this.width / 2 - width / 2;
        int tabWidth = 70;

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.culling"),
                (button) -> switchTab(TAB_CULLING))
                .bounds(this.width / 2 - tabWidth * 3 / 2 - 2, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.movement"),
                (button) -> switchTab(TAB_MOVEMENT))
                .bounds(this.width / 2 - tabWidth / 2, 25, tabWidth, height).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.tab.visual"),
                (button) -> switchTab(TAB_VISUAL))
                .bounds(this.width / 2 + tabWidth / 2 + 2, 25, tabWidth, height).build());

        if (currentTab == TAB_CULLING) {
            initCullingTab(x, startY, width, height, spacing);
        } else if (currentTab == TAB_MOVEMENT) {
            initMovementTab(x, startY, width, height, spacing);
        } else {
            initVisualTab(x, startY, width, height, spacing);
        }
    }

    private void initCullingTab(int x, int startY, int width, int height, int spacing) {
        this.addRenderableWidget(
                new IntConfigSlider(x, startY, width, height, "topdown_view.config.cylinder_radius_horizontal",
                        Config.cylinderRadiusHorizontal, 1, 10, (value) -> Config.cylinderRadiusHorizontal = value));

        this.addRenderableWidget(
                new IntConfigSlider(x, startY + spacing, width, height, "topdown_view.config.cylinder_radius_vertical",
                        Config.cylinderRadiusVertical, 1, 10, (value) -> Config.cylinderRadiusVertical = value));

        this.addRenderableWidget(new IntConfigSlider(x, startY + spacing * 2, width, height,
                "topdown_view.config.cylinder_forward_shift",
                Config.cylinderForwardShift, 0, 10, (value) -> Config.cylinderForwardShift = value));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(x, startY + spacing * 4, width, height).build());
    }

    private void initMovementTab(int x, int startY, int width, int height, int spacing) {
        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.click_to_move",
                        Config.clickToMoveEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.clickToMoveEnabled = !Config.clickToMoveEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.click_to_move",
                            Config.clickToMoveEnabled ? "ON" : "OFF"));
                }).bounds(x, startY, width, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.click_to_move.tooltip")))
                .build());

        this.addRenderableWidget(
                new ConfigSlider(x, startY + spacing, width, height, "topdown_view.config.arrival_threshold",
                        Config.arrivalThreshold, 0.5, 5.0, (value) -> Config.arrivalThreshold = value));

        this.addRenderableWidget(
                new ConfigSlider(x, startY + spacing * 2, width, height, "topdown_view.config.attack_range",
                        Config.attackRange, 1.0, 6.0, (value) -> Config.attackRange = value));

        // Pathfinding UI disabled - straight-line movement only
        // this.addRenderableWidget(Button.builder(
        // Component.translatable("topdown_view.config.pathfinding",
        // Config.pathfindingEnabled ? "ON" : "OFF"),
        // (button) -> {
        // Config.pathfindingEnabled = !Config.pathfindingEnabled;
        // button.setMessage(Component.translatable("topdown_view.config.pathfinding",
        // Config.pathfindingEnabled ? "ON" : "OFF"));
        // }).bounds(x, startY + spacing * 3, width, height).build());
        //
        // this.addRenderableWidget(new ConfigSlider(x, startY + spacing * 4, width,
        // height, "topdown_view.config.avoidance_radius",
        // Config.avoidanceRadius, 1.0, 5.0, (value) -> Config.avoidanceRadius =
        // value));
        //
        // this.addRenderableWidget(new IntConfigSlider(x, startY + spacing * 5, width,
        // height, "topdown_view.config.pathfinding_range",
        // Config.pathfindingRange, 8, 64, (value) -> Config.pathfindingRange = value));
        //
        // this.addRenderableWidget(new IntConfigSlider(x, startY + spacing * 6, width,
        // height, "topdown_view.config.path_recalc_cooldown",
        // Config.pathRecalcCooldown, 5, 100, (value) -> Config.pathRecalcCooldown =
        // value));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(x, startY + spacing * 4, width, height).build());
    }

    private void initVisualTab(int x, int startY, int width, int height, int spacing) {
        this.addRenderableWidget(Button.builder(
                Component.translatable("topdown_view.config.fade_enabled",
                        Config.fadeEnabled ? "ON" : "OFF"),
                (button) -> {
                    Config.fadeEnabled = !Config.fadeEnabled;
                    button.setMessage(Component.translatable("topdown_view.config.fade_enabled",
                            Config.fadeEnabled ? "ON" : "OFF"));
                }).bounds(x, startY, width, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.fade_enabled.tooltip")))
                .build());

        this.addRenderableWidget(new ConfigSlider(x, startY + spacing, width, height, "topdown_view.config.fade_start",
                Config.fadeStart, 0.0, 0.9, (value) -> Config.fadeStart = value));

        this.addRenderableWidget(
                new ConfigSlider(x, startY + spacing * 2, width, height, "topdown_view.config.fade_near_alpha",
                        Config.fadeNearAlpha, 0.0, 1.0, (value) -> Config.fadeNearAlpha = value));

        this.addRenderableWidget(Button.builder(
                getRotateModeComponent(Config.rotateAngleMode),
                (button) -> {
                    Config.rotateAngleMode = (Config.rotateAngleMode + 1) % 3;
                    button.setMessage(getRotateModeComponent(Config.rotateAngleMode));
                }).bounds(x, startY + spacing * 3, width, height)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.rotate_angle_mode.tooltip")))
                .build());

        this.addRenderableWidget(
                new ConfigSlider(x, startY + spacing * 4, width, height, "topdown_view.config.camera_pitch",
                        Config.cameraPitch, 10.0, 90.0, (value) -> Config.cameraPitch = value));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(x, startY + spacing * 6, width, height).build());
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
