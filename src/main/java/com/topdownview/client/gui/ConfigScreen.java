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
        for (int i = 0; i < 6; i++) {
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
            case 5 -> Component.translatable("topdown_view.config.tab.experimental");
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
            case 5 -> buildExperimentalTab(colX, y, colW, btnH, spacing, titleX);
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
                Button.builder(getOnOffComponent("topdown_view.config.default_enabled", Config.isDefaultEnabled()), btn -> {
                    Config.setDefaultEnabled(!Config.isDefaultEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.default_enabled", Config.isDefaultEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.default_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.target_glow_enabled", Config.isTargetGlowEnabled()), btn -> {
                    Config.setTargetGlowEnabled(!Config.isTargetGlowEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.target_glow_enabled", Config.isTargetGlowEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.target_glow_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.head_body_rotation_enabled", Config.isHeadBodyRotationEnabled()), btn -> {
                    Config.setHeadBodyRotationEnabled(!Config.isHeadBodyRotationEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.head_body_rotation_enabled", Config.isHeadBodyRotationEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.head_body_rotation_enabled.tooltip")))
                        .build());
        contentHeight = (y + sp) - (30 - (int) scrollOffset) + sp;
    }

    private void buildCullingTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.entity_culling", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.mob_culling_enabled", Config.isMobCullingEnabled()),
                btn -> {
                    Config.setMobCullingEnabled(!Config.isMobCullingEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.mob_culling_enabled", Config.isMobCullingEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.mob_culling_enabled.tooltip")))
                .build());
        y += sp;

        y = addSection(y, "topdown_view.config.section.culling", tx);
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_radius_horizontal",
                Config.getCylinderRadiusHorizontal(), 1, 10, val -> Config.setCylinderRadiusHorizontal(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_radius_vertical",
                Config.getCylinderRadiusVertical(), 1, 10, val -> Config.setCylinderRadiusVertical(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.cylinder_forward_shift",
                Config.getCylinderForwardShift(), 0, 10, val -> Config.setCylinderForwardShift(val)));
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildMovementTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.click_to_move", tx);
        addRightWidget(Button
                .builder(getOnOffComponent("topdown_view.config.click_to_move", Config.isClickToMoveEnabled()), btn -> {
                    Config.setClickToMoveEnabled(!Config.isClickToMoveEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.click_to_move", Config.isClickToMoveEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.click_to_move.tooltip"))).build());
        y += sp;
        addRightWidget(Button
                .builder(getOnOffComponent("topdown_view.config.destination_highlight", Config.isDestinationHighlightEnabled()), btn -> {
                    Config.setDestinationHighlightEnabled(!Config.isDestinationHighlightEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.destination_highlight", Config.isDestinationHighlightEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.destination_highlight.tooltip"))).build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.arrival_threshold",
                Config.getArrivalThreshold(), 0.5, 5.0, val -> Config.setArrivalThreshold(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.sprint_distance_threshold",
                Config.getSprintDistanceThreshold(), 1.0, 50.0, val -> Config.setSprintDistanceThreshold(val)));
        y += sp;

        y = addSection(y, "topdown_view.config.section.auto_jump", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.force_auto_jump", Config.isForceAutoJump()), btn -> {
                    Config.setForceAutoJump(!Config.isForceAutoJump());
                    btn.setMessage(getOnOffComponent("topdown_view.config.force_auto_jump", Config.isForceAutoJump()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.force_auto_jump.tooltip")))
                        .build());
        contentHeight = (y += sp) - (30 - (int) scrollOffset) + sp;
    }

    private void buildCameraTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.camera_angle", tx);
        addRightWidget(Button.builder(getRotateModeComponent(Config.getRotateAngleMode()), btn -> {
            Config.setRotateAngleMode((Config.getRotateAngleMode() + 1) % 3);
            btn.setMessage(getRotateModeComponent(Config.getRotateAngleMode()));
        }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.rotate_angle_mode.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_snap_rotation_speed",
                Config.getCameraSnapRotationSpeed(), 0.05, 0.5, val -> Config.setCameraSnapRotationSpeed(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_pitch", Config.getCameraPitch(), 10.0, 90.0,
                val -> Config.setCameraPitch(val), 0));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.player_screen_offset", Config.getPlayerScreenOffset(), -10.0, 10.0,
                val -> Config.setPlayerScreenOffset(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.max_camera_distance", Config.getMaxCameraDistance(), 0.0, 200.0,
                val -> {
                    Config.setMaxCameraDistance(val);
                    if (Config.getDefaultCameraDistance() > val) {
                        Config.setDefaultCameraDistance(val);
                    }
                }, 0));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.default_camera_distance", Config.getDefaultCameraDistance(), 0.0, 200.0,
                val -> {
                    if (val > Config.getMaxCameraDistance()) {
                        Config.setDefaultCameraDistance(Config.getMaxCameraDistance());
                    } else {
                        Config.setDefaultCameraDistance(val);
                    }
                }, 0));
        y += sp;

        y = addSection(y, "topdown_view.config.section.drag_rotation", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.drag_rotation_enabled", Config.isDragRotationEnabled()),
                btn -> {
                    Config.setDragRotationEnabled(!Config.isDragRotationEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.drag_rotation_enabled",
                            Config.isDragRotationEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.drag_rotation_enabled.tooltip")))
                .build());
        y += sp;

        y = addSection(y, "topdown_view.config.section.auto_align", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.auto_align_to_movement", Config.isAutoAlignToMovementEnabled()),
                btn -> {
                    Config.setAutoAlignToMovementEnabled(!Config.isAutoAlignToMovementEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.auto_align_to_movement",
                            Config.isAutoAlignToMovementEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.auto_align_to_movement.tooltip")))
                .build());
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.auto_align_angle_threshold",
                Config.getAutoAlignAngleThreshold(), 0, 90, val -> Config.setAutoAlignAngleThreshold(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.auto_align_cooldown_ticks",
                Config.getAutoAlignCooldownTicks(), 0, 100, val -> Config.setAutoAlignCooldownTicks(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.stable_direction_angle",
                Config.getStableDirectionAngle(), 5, 60, val -> Config.setStableDirectionAngle(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.stable_direction_ticks",
                Config.getStableDirectionTicks(), 5, 60, val -> Config.setStableDirectionTicks(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.auto_align_animation_speed",
                Config.getAutoAlignAnimationSpeed(), 0.05, 0.5, val -> Config.setAutoAlignAnimationSpeed(val)));
        y += sp;

        y = addSection(y, "topdown_view.config.section.camera_follow_delay", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.follow_delay_while_mounted", Config.isFollowDelayWhileMounted()),
                btn -> {
                    Config.setFollowDelayWhileMounted(!Config.isFollowDelayWhileMounted());
                    btn.setMessage(getOnOffComponent("topdown_view.config.follow_delay_while_mounted",
                            Config.isFollowDelayWhileMounted()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.follow_delay_while_mounted.tooltip")))
                .build());
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.camera_y_follow_delay_enabled", Config.isCameraYFollowDelayEnabled()),
                btn -> {
                    Config.setCameraYFollowDelayEnabled(!Config.isCameraYFollowDelayEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.camera_y_follow_delay_enabled",
                            Config.isCameraYFollowDelayEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.camera_y_follow_delay_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_y_follow_delay",
                Config.getCameraYFollowDelay(), 0.0, 4.0, val -> Config.setCameraYFollowDelay(val)));
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.camera_x_follow_delay_enabled", Config.isCameraXFollowDelayEnabled()),
                btn -> {
                    Config.setCameraXFollowDelayEnabled(!Config.isCameraXFollowDelayEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.camera_x_follow_delay_enabled",
                            Config.isCameraXFollowDelayEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.camera_x_follow_delay_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_x_follow_delay",
                Config.getCameraXFollowDelay(), 0.0, 4.0, val -> Config.setCameraXFollowDelay(val)));
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.camera_z_follow_delay_enabled", Config.isCameraZFollowDelayEnabled()),
                btn -> {
                    Config.setCameraZFollowDelayEnabled(!Config.isCameraZFollowDelayEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.camera_z_follow_delay_enabled",
                            Config.isCameraZFollowDelayEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.camera_z_follow_delay_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.camera_z_follow_delay",
                Config.getCameraZFollowDelay(), 0.0, 4.0, val -> Config.setCameraZFollowDelay(val)));
        y += sp;
        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildExperimentalTab(int x, int y, int w, int h, int sp, int tx) {
        // 警告ヘッダー
        y = addSection(y, "topdown_view.config.section.experimental_warning", tx);
        y += sp;

        // マイニングモード設定
        y = addSection(y, "topdown_view.config.section.mining_mode", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.mining_mode_enabled", Config.isMiningModeEnabled()),
                btn -> {
                    Config.setMiningModeEnabled(!Config.isMiningModeEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.mining_mode_enabled",
                            Config.isMiningModeEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.mining_mode_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.mining_mode_pitch", Config.getMiningModePitch(), 10.0, 90.0,
                val -> Config.setMiningModePitch(val), 0));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.mining_cylinder_radius",
                Config.getMiningCylinderRadius(), 1, 16, val -> Config.setMiningCylinderRadius(val)));
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.mining_cylinder_forward_shift",
                Config.getMiningCylinderForwardShift(), 0, 10, val -> Config.setMiningCylinderForwardShift(val)));
        y += sp;

        // 射程設定
        y = addSection(y, "topdown_view.config.section.weapon_range", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.range_indicator_enabled", Config.isRangeIndicatorEnabled()),
                btn -> {
                    Config.setRangeIndicatorEnabled(!Config.isRangeIndicatorEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.range_indicator_enabled",
                            Config.isRangeIndicatorEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.range_indicator_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_empty_hand",
                Config.getRangeEmptyHand(), 1.0, 10.0, val -> Config.setRangeEmptyHand(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_sword",
                Config.getRangeSword(), 1.0, 10.0, val -> Config.setRangeSword(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_axe",
                Config.getRangeAxe(), 1.0, 10.0, val -> Config.setRangeAxe(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_pickaxe",
                Config.getRangePickaxe(), 1.0, 10.0, val -> Config.setRangePickaxe(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_shovel",
                Config.getRangeShovel(), 1.0, 10.0, val -> Config.setRangeShovel(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.range_other",
                Config.getRangeOther(), 1.0, 10.0, val -> Config.setRangeOther(val)));
        y += sp;

        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private void buildVisualTab(int x, int y, int w, int h, int sp, int tx) {
        y = addSection(y, "topdown_view.config.section.fade", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.fade_enabled", Config.isFadeEnabled()), btn -> {
                    Config.setFadeEnabled(!Config.isFadeEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.fade_enabled", Config.isFadeEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.fade_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.fade_block_hit_threshold", Config.getFadeBlockHitThreshold(), 0.0,
                1.0, val -> Config.setFadeBlockHitThreshold(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.fade_start", Config.getFadeStart(), 0.0, 0.9,
                val -> Config.setFadeStart(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.fade_near_alpha", Config.getFadeNearAlpha(), 0.0,
                1.0, val -> Config.setFadeNearAlpha(val)));
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
        com.topdownview.TopDownViewMod.getLogger().info("[TopDownView][ConfigScreen] Saving config - maxCameraDistance: {}, defaultCameraDistance: {}", 
                Config.getMaxCameraDistance(), Config.getDefaultCameraDistance());
        Config.save();
        com.topdownview.TopDownViewMod.getLogger().info("[TopDownView][ConfigScreen] Config saved");
    }

    private void resetToDefaults() {
        Config.setDefaultEnabled(true);

        Config.setMobCullingEnabled(false);
        Config.setCylinderRadiusHorizontal(5);
        Config.setCylinderRadiusVertical(5);
        Config.setCylinderForwardShift(1);

        Config.setMiningCylinderRadius(5);
        Config.setMiningCylinderForwardShift(0);
        Config.setMiningModeEnabled(false);

        Config.setClickToMoveEnabled(false);
        Config.setDestinationHighlightEnabled(true);
        Config.setArrivalThreshold(1.5);
        Config.setSprintDistanceThreshold(5.0);
        Config.setForceAutoJump(true);

        Config.setRangeIndicatorEnabled(false);
        Config.setRangeEmptyHand(3.0);
        Config.setRangeSword(3.0);
        Config.setRangeAxe(3.0);
        Config.setRangePickaxe(3.0);
        Config.setRangeShovel(3.0);
        Config.setRangeOther(3.0);

        Config.setDragRotationEnabled(true);

        Config.setAutoAlignToMovementEnabled(false);
        Config.setAutoAlignAngleThreshold(45);
        Config.setAutoAlignCooldownTicks(30);
        Config.setStableDirectionAngle(15);
        Config.setStableDirectionTicks(20);
        Config.setAutoAlignAnimationSpeed(0.1);

        Config.setTrapdoorTranslucencyEnabled(false);
        Config.setTrapdoorTransparency(0.3);

        Config.setFadeEnabled(true);
        Config.setFadeBlockHitThreshold(0.5);
        Config.setFadeStart(0.7);
        Config.setFadeNearAlpha(0.0);

        Config.setRotateAngleMode(0);
        Config.setCameraSnapRotationSpeed(0.2);
        Config.setCameraPitch(45.0);
        Config.setMiningModePitch(45.0);
        Config.setMaxCameraDistance(50.0);
        Config.setDefaultCameraDistance(9.0);

        Config.setPlayerScreenOffset(2.0);

        Config.setHeadBodyRotationEnabled(true);

        Config.setCameraYFollowDelayEnabled(true);
        Config.setCameraYFollowDelay(1.0);
        Config.setFollowDelayWhileMounted(false);

        Config.setCameraXFollowDelayEnabled(false);
        Config.setCameraXFollowDelay(1.0);

        Config.setCameraZFollowDelayEnabled(false);
        Config.setCameraZFollowDelay(1.0);

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
        private final int decimalPlaces;

        public ConfigSlider(int x, int y, int width, int height, String translationKey, double current, double min,
                double max, java.util.function.Consumer<Double> setter) {
            this(x, y, width, height, translationKey, current, min, max, setter, 1);
        }

        public ConfigSlider(int x, int y, int width, int height, String translationKey, double current, double min,
                double max, java.util.function.Consumer<Double> setter, int decimalPlaces) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.decimalPlaces = decimalPlaces;
            this.setTooltip(Tooltip.create(Component.translatable(translationKey + ".tooltip")));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double value = min + (max - min) * this.value;
            String format = "%." + decimalPlaces + "f";
            this.setMessage(Component.translatable(translationKey, String.format(format, value)));
        }

        @Override
        protected void applyValue() {
            double newValue = min + (max - min) * this.value;
            double factor = Math.pow(10, decimalPlaces);
            newValue = Math.round(newValue * factor) / factor;
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
