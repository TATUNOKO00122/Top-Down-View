package com.topdownview.client.gui;

import com.topdownview.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    private int currentTab = 0;

    // スクロール機能
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;
    private boolean isDraggingScrollbar = false;

    // ウィジェットリスト
    private final List<net.minecraft.client.gui.components.AbstractWidget> leftWidgets = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> rightWidgets = new ArrayList<>();
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("topdown_view.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        leftWidgets.clear();
        rightWidgets.clear();
        sectionHeaders.clear();
        this.clearWidgets();

        int leftPanelW = 90;
        int leftPanelX = 20;
        int leftPanelY = 30;
        int leftPanelH = this.height - 40;

        int rightPanelX = leftPanelX + leftPanelW + 10;
        int rightPanelY = 30;
        int rightPanelW = this.width - rightPanelX - 20;
        int rightPanelH = this.height - 40;

        // --- タブの生成 (左パネル) ---
        int tabY = leftPanelY + 10;
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Button btn = Button.builder(getTabComponent(i), b -> switchTab(index))
                    .bounds(leftPanelX + 10, tabY, leftPanelW - 20, 20)
                    .build();
            btn.active = (currentTab != i);
            leftWidgets.add(btn);
            this.addRenderableWidget(btn); // 標準のイベントと描画
            tabY += 24;
        }

        // --- 下部ボタン (左パネルの下) ---
        Button btnDone = Button.builder(CommonComponents.GUI_DONE, b -> {
            saveConfig();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(leftPanelX + 10, leftPanelY + leftPanelH - 30, leftPanelW - 20, 20).build();

        Button btnReset = Button.builder(Component.translatable("topdown_view.config.reset"), b -> resetToDefaults())
                .bounds(leftPanelX + 10, leftPanelY + leftPanelH - 54, leftPanelW - 20, 20)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.reset.tooltip")))
                .build();

        leftWidgets.add(btnDone);
        this.addRenderableWidget(btnDone);
        leftWidgets.add(btnReset);
        this.addRenderableWidget(btnReset);

        // --- コンテンツの生成 (右パネル) ---
        int colW = Math.max(100, Math.min(260, rightPanelW - 20)); // Dynamically scale based on window width
        int colX = rightPanelX + rightPanelW / 2 - colW / 2;
        int startY = rightPanelY + 10 - (int) scrollOffset;

        buildTabContent(currentTab, colX, startY, colW, 20, 24, rightPanelX + 20);

        updateMaxScroll(rightPanelH);
        clampScrollOffset();
    }

    private Component getTabComponent(int index) {
        return switch (index) {
            case 0 -> Component.translatable("topdown_view.config.tab.general");
            case 1 -> Component.translatable("topdown_view.config.tab.culling");
            case 2 -> Component.translatable("topdown_view.config.tab.movement");
            case 3 -> Component.translatable("topdown_view.config.tab.camera");
            case 4 -> Component.translatable("topdown_view.config.tab.visual");
            default -> Component.empty();
        };
    }

    private void addRightWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
        rightWidgets.add(widget);
        this.addWidget(widget); // onClickなどのイベント用（描画は手動で行うため renderables には入れない）
    }

    private void buildTabContent(int tab, int colX, int y, int colW, int btnH, int spacing, int titleX) {
        switch (tab) {
            case 0 -> buildGeneralTab(colX, y, colW, btnH, spacing, titleX);
            case 1 -> buildCullingTab(colX, y, colW, btnH, spacing, titleX);
            case 2 -> buildMovementTab(colX, y, colW, btnH, spacing, titleX);
            case 3 -> buildCameraTab(colX, y, colW, btnH, spacing, titleX);
            case 4 -> buildVisualTab(colX, y, colW, btnH, spacing, titleX);
        }
    }

    private int addSection(int y, String titleKey, int titleX) {
        y += 10;
        sectionHeaders.add(new SectionHeader(titleX, y, Component.translatable(titleKey)));
        return y + 16;
    }

    private Component getOnOffComponent(String key, boolean enabled) {
        return Component.translatable(key,
                Component.translatable(enabled ? "topdown_view.config.on" : "topdown_view.config.off"));
    }

    private void buildGeneralTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.basic", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.default_enabled", Config.defaultEnabled), btn -> {
                    Config.defaultEnabled = !Config.defaultEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.default_enabled", Config.defaultEnabled));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.default_enabled.tooltip")))
                        .build());
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildCullingTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.culling", tx);
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_radius_horizontal",
                Config.cylinderRadiusHorizontal, 1, 10, val -> Config.cylinderRadiusHorizontal = val));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_radius_vertical",
                Config.cylinderRadiusVertical, 1, 10, val -> Config.cylinderRadiusVertical = val));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_forward_shift",
                Config.cylinderForwardShift, 0, 10, val -> Config.cylinderForwardShift = val));
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildMovementTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.click_to_move", tx);
        addRightWidget(Button
                .builder(getOnOffComponent("topdown_view.config.click_to_move", Config.clickToMoveEnabled), btn -> {
                    Config.clickToMoveEnabled = !Config.clickToMoveEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.click_to_move", Config.clickToMoveEnabled));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.click_to_move.tooltip"))).build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.arrival_threshold",
                Config.arrivalThreshold, 0.5, 5.0, val -> Config.arrivalThreshold = val));
        y += sp;

        y = addSection(y, "topdown_view.config.section.weapon_range", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.range_indicator_enabled", Config.rangeIndicatorEnabled),
                btn -> {
                    Config.rangeIndicatorEnabled = !Config.rangeIndicatorEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.range_indicator_enabled",
                            Config.rangeIndicatorEnabled));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.range_indicator_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_empty_hand",
                Config.rangeEmptyHand, 1.0, 10.0, val -> Config.rangeEmptyHand = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_sword",
                Config.rangeSword, 1.0, 10.0, val -> Config.rangeSword = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_axe",
                Config.rangeAxe, 1.0, 10.0, val -> Config.rangeAxe = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_pickaxe",
                Config.rangePickaxe, 1.0, 10.0, val -> Config.rangePickaxe = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_shovel",
                Config.rangeShovel, 1.0, 10.0, val -> Config.rangeShovel = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_other",
                Config.rangeOther, 1.0, 10.0, val -> Config.rangeOther = val));
        y += sp;
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.force_auto_jump", Config.forceAutoJump), btn -> {
                    Config.forceAutoJump = !Config.forceAutoJump;
                    btn.setMessage(getOnOffComponent("topdown_view.config.force_auto_jump", Config.forceAutoJump));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.force_auto_jump.tooltip")))
                        .build());
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildCameraTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.drag_rotation", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.drag_rotation_enabled", Config.dragRotationEnabled),
                btn -> {
                    Config.dragRotationEnabled = !Config.dragRotationEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.drag_rotation_enabled",
                            Config.dragRotationEnabled));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.drag_rotation_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.drag_rotation_sensitivity",
                Config.dragRotationSensitivity, 0.1, 2.0, val -> Config.dragRotationSensitivity = val));
        y += sp;

        y = addSection(y, "topdown_view.config.section.auto_align", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.auto_align_to_movement", Config.autoAlignToMovementEnabled),
                btn -> {
                    Config.autoAlignToMovementEnabled = !Config.autoAlignToMovementEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.auto_align_to_movement",
                            Config.autoAlignToMovementEnabled));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.auto_align_to_movement.tooltip")))
                .build());
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.auto_align_angle_threshold",
                Config.autoAlignAngleThreshold, 0, 90, val -> Config.autoAlignAngleThreshold = val));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.auto_align_cooldown_ticks",
                Config.autoAlignCooldownTicks, 0, 100, val -> Config.autoAlignCooldownTicks = val));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.stable_direction_angle",
                Config.stableDirectionAngle, 5, 60, val -> Config.stableDirectionAngle = val));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.stable_direction_ticks",
                Config.stableDirectionTicks, 5, 60, val -> Config.stableDirectionTicks = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.auto_align_animation_speed",
                Config.autoAlignAnimationSpeed, 0.05, 0.5, val -> Config.autoAlignAnimationSpeed = val));
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildVisualTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.fade", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.fade_enabled", Config.fadeEnabled), btn -> {
                    Config.fadeEnabled = !Config.fadeEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.fade_enabled", Config.fadeEnabled));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.fade_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.fade_start", Config.fadeStart, 0.0, 0.9,
                val -> Config.fadeStart = val));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.fade_near_alpha", Config.fadeNearAlpha, 0.0,
                1.0, val -> Config.fadeNearAlpha = val));
        y += sp;

        y = addSection(y, "topdown_view.config.section.trapdoor", tx);
        addRightWidget(Button.builder(getOnOffComponent("topdown_view.config.trapdoor_translucency_enabled",
                Config.trapdoorTranslucencyEnabled), btn -> {
                    Config.trapdoorTranslucencyEnabled = !Config.trapdoorTranslucencyEnabled;
                    btn.setMessage(getOnOffComponent("topdown_view.config.trapdoor_translucency_enabled",
                            Config.trapdoorTranslucencyEnabled));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip
                        .create(Component.translatable("topdown_view.config.trapdoor_translucency_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.trapdoor_transparency",
                Config.trapdoorTransparency, 0.0, 1.0, val -> Config.trapdoorTransparency = val));
        y += sp;

        y = addSection(y, "topdown_view.config.section.camera_angle", tx);
        addRightWidget(Button.builder(getRotateModeComponent(Config.rotateAngleMode), btn -> {
            Config.rotateAngleMode = (Config.rotateAngleMode + 1) % 3;
            btn.setMessage(getRotateModeComponent(Config.rotateAngleMode));
        }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.rotate_angle_mode.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_pitch", Config.cameraPitch, 10.0, 90.0,
                val -> Config.cameraPitch = val));
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
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

    private record SectionHeader(int x, int y, Component label) {
    }

    private void updateMaxScroll(int visibleHeight) {
        maxScroll = Math.max(0, contentHeight - visibleHeight);
    }

    private void clampScrollOffset() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        this.init();
    }

    private void saveConfig() {
        Config.save();
    }

    private void resetToDefaults() {
        Config.defaultEnabled = true;

        Config.cylinderRadiusHorizontal = 5;
        Config.cylinderRadiusVertical = 4;
        Config.cylinderForwardShift = 4;

        Config.clickToMoveEnabled = false;
        Config.arrivalThreshold = 1.5;
        Config.forceAutoJump = true;

        Config.rangeIndicatorEnabled = false;
        Config.rangeEmptyHand = 3.0;
        Config.rangeSword = 3.0;
        Config.rangeAxe = 3.0;
        Config.rangePickaxe = 3.0;
        Config.rangeShovel = 3.0;
        Config.rangeOther = 3.0;

        Config.dragRotationEnabled = true;
        Config.dragRotationSensitivity = 0.5;

        Config.autoAlignToMovementEnabled = false;
        Config.autoAlignAngleThreshold = 15;
        Config.autoAlignCooldownTicks = 30;
        Config.stableDirectionAngle = 15;
        Config.stableDirectionTicks = 20;
        Config.autoAlignAnimationSpeed = 0.1;

        Config.trapdoorTranslucencyEnabled = false;
        Config.trapdoorTransparency = 0.3;

        Config.fadeEnabled = true;
        Config.fadeStart = 0.7;
        Config.fadeNearAlpha = 0.0;

        Config.rotateAngleMode = 0;
        Config.cameraPitch = 45.0;

        this.init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset -= delta * 20;
            clampScrollOffset();
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll > 0) {
            int leftPanelW = 90;
            int rightPanelX = 20 + leftPanelW + 10;
            int rightPanelW = this.width - rightPanelX - 20;
            int rightPanelH = this.height - 40;
            int scrollBarX = rightPanelX + rightPanelW - 6;

            if (mouseX >= scrollBarX - 4 && mouseX <= scrollBarX + 8 && mouseY >= 30 && mouseY <= 30 + rightPanelH) {
                isDraggingScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && maxScroll > 0) {
            int rightPanelH = this.height - 40;
            int visibleHeight = rightPanelH;
            int scrollBarHeight = Math.max(20, (int) ((double) visibleHeight / (double) contentHeight * visibleHeight));
            double scrollFactor = (double) maxScroll / (visibleHeight - scrollBarHeight);
            scrollOffset += dragY * scrollFactor;
            clampScrollOffset();
            this.init();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int leftPanelW = 90;
        int leftPanelX = 20;
        int leftPanelY = 30;
        int leftPanelH = this.height - 40;

        int rightPanelX = leftPanelX + leftPanelW + 10;
        int rightPanelY = 30;
        int rightPanelW = this.width - rightPanelX - 20;
        int rightPanelH = this.height - 40;

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // 左パネル背景
        guiGraphics.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0x66000000);
        // 右パネル背景
        guiGraphics.fill(rightPanelX, rightPanelY, rightPanelX + rightPanelW, rightPanelY + rightPanelH, 0x44000000);

        // --- 右パネル（クリッピング領域） ---
        guiGraphics.enableScissor(rightPanelX, rightPanelY, rightPanelX + rightPanelW, rightPanelY + rightPanelH);

        for (var header : sectionHeaders) {
            guiGraphics.drawString(this.font, header.label(), header.x(), header.y(), 0xFFFFAA);
            guiGraphics.fill(header.x(), header.y() + 11, rightPanelX + rightPanelW - 20, header.y() + 12, 0x22FFFFFF);
        }

        for (var widget : rightWidgets) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.disableScissor();

        // スクロールバー
        renderScrollBar(guiGraphics, rightPanelX + rightPanelW - 6, rightPanelY, rightPanelH);

        // super.render で左パネルと下部ボタン（renderablesに入れたもの）、そしてツールチップ等を描画
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollBar(GuiGraphics guiGraphics, int x, int y, int height) {
        if (maxScroll <= 0)
            return;

        int visibleHeight = height;
        int scrollBarHeight = Math.max(20, (int) ((double) visibleHeight / contentHeight * visibleHeight));
        int scrollBarY = y + (int) ((double) scrollOffset / maxScroll * (visibleHeight - scrollBarHeight));

        guiGraphics.fill(x, y, x + 6, y + height, 0x88000000);
        guiGraphics.fill(x, scrollBarY, x + 6, scrollBarY + scrollBarHeight,
                isDraggingScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
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
                double max, java.util.function.Consumer<Double> setter) {
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
            newValue = Math.round(newValue * 100.0) / 100.0;
            setter.accept(newValue);
        }
    }

    private static class IntConfigSlider extends AbstractSliderButton {
        private final String translationKey;
        private final int min;
        private final int max;
        private final java.util.function.Consumer<Integer> setter;

        public IntConfigSlider(int x, int y, int width, int height, String translationKey, int current, int min,
                int max, java.util.function.Consumer<Integer> setter) {
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
