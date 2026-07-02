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
        int tabCount = 6;
        int topPadding = 10;
        int bottomButtonTop = leftPanelY + leftPanelH - 54;
        int bottomGap = 4;
        int availableForTabs = bottomButtonTop - bottomGap - (leftPanelY + topPadding);
        int gap = 2;
        int tabH = Math.min(20, (availableForTabs - (tabCount - 1) * gap) / tabCount);

        int tabY = leftPanelY + topPadding;
        for (int i = 0; i < tabCount; i++) {
            final int index = i;
            Button btn = Button.builder(getTabComponent(i), b -> switchTab(index))
                    .bounds(leftPanelX + 10, tabY, leftPanelW - 20, tabH)
                    .build();
            btn.active = (currentTab != i);
            leftWidgets.add(btn);
            this.addRenderableWidget(btn);
            tabY += tabH + gap;
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
        y += sp;

        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.ignore_leaves_in_raycast", Config.isIgnoreLeavesInRaycast()), btn -> {
                    Config.setIgnoreLeavesInRaycast(!Config.isIgnoreLeavesInRaycast());
                    btn.setMessage(getOnOffComponent("topdown_view.config.ignore_leaves_in_raycast", Config.isIgnoreLeavesInRaycast()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.ignore_leaves_in_raycast.tooltip")))
                        .build());
        y += sp;

        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.scroll_only_zoom_enabled", Config.isScrollOnlyZoomEnabled()), btn -> {
                    Config.setScrollOnlyZoomEnabled(!Config.isScrollOnlyZoomEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.scroll_only_zoom_enabled", Config.isScrollOnlyZoomEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.scroll_only_zoom_enabled.tooltip")))
                        .build());
        y += sp;

        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.click_position_placement_enabled",
                        Config.isClickPositionPlacementEnabled()), btn -> {
                    Config.setClickPositionPlacementEnabled(!Config.isClickPositionPlacementEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.click_position_placement_enabled",
                            Config.isClickPositionPlacementEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable(
                                "topdown_view.config.click_position_placement_enabled.tooltip")))
                        .build());
        y += sp;

        y = addSection(y, "topdown_view.config.section.target_lock", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.target_lock_enabled", Config.isTargetLockEnabled()), btn -> {
                    Config.setTargetLockEnabled(!Config.isTargetLockEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.target_lock_enabled", Config.isTargetLockEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable("topdown_view.config.target_lock_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.target_lock_duration",
                Config.getTargetLockDuration(), 0, 600, val -> Config.setTargetLockDuration(val)));
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.target_hitbox_expansion",
                Config.getTargetHitboxExpansion(), 0.0, 5.0, val -> Config.setTargetHitboxExpansion(val), 1));
        y += sp;

        y = addSection(y, "topdown_view.config.section.screen_reach", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.screen_reach_enabled", Config.isScreenReachEnabled()),
                btn -> {
                    Config.setScreenReachEnabled(!Config.isScreenReachEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.screen_reach_enabled",
                            Config.isScreenReachEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.screen_reach_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.reach_distance",
                Config.getReachDistance(), 1.0, 100.0, val -> Config.setReachDistance(val), 0));
        y += sp;

        contentHeight = (y + sp) - (30 - (int) scrollOffset) + sp;
    }

    private void buildCullingTab(int x, int y, int w, int h, int sp, int tx) {
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
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.mob_culling_enabled", Config.isMobCullingEnabled()),
                btn -> {
                    Config.setMobCullingEnabled(!Config.isMobCullingEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.mob_culling_enabled",
                            Config.isMobCullingEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.mob_culling_enabled.tooltip")))
                .build());
        y += sp;

        y = addSection(y, "topdown_view.config.section.staircase_exclusion", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.staircase_exclusion_enabled",
                        Config.isStaircaseExclusionEnabled()),
                btn -> {
                    Config.setStaircaseExclusionEnabled(!Config.isStaircaseExclusionEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.staircase_exclusion_enabled",
                            Config.isStaircaseExclusionEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable(
                        "topdown_view.config.staircase_exclusion_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.staircase_exclusion_height",
                Config.getStaircaseExclusionHeight(), 1, 10,
                val -> Config.setStaircaseExclusionHeight(val)));
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.staircase_occlude_enabled",
                        Config.isStaircaseOccludeEnabled()),
                btn -> {
                    Config.setStaircaseOccludeEnabled(!Config.isStaircaseOccludeEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.staircase_occlude_enabled",
                            Config.isStaircaseOccludeEnabled()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable(
                        "topdown_view.config.staircase_occlude_enabled.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.staircase_occlude_alpha",
                Config.getStaircaseOccludeAlpha(), 0.0, 1.0,
                val -> Config.setStaircaseOccludeAlpha(val)));
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
        addRightWidget(new IntConfigSlider(x, y, w, h, "topdown_view.config.top_down_fov",
                Config.getTopDownFov(), 30, 110, val -> Config.setTopDownFov(val)));
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

        // 配置方向指定
        y = addSection(y, "topdown_view.config.section.placement_rotation", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.placement_rotation_enabled",
                        Config.isPlacementRotationEnabled()), btn -> {
                    Config.setPlacementRotationEnabled(!Config.isPlacementRotationEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.placement_rotation_enabled",
                             Config.isPlacementRotationEnabled()));
                    com.topdownview.client.PlacementRotationController.onConfigChanged();
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable(
                                "topdown_view.config.placement_rotation_enabled.tooltip")))
                        .build());
        y += sp;

        // 操作プロンプト設定
        y = addSection(y, "topdown_view.config.section.interaction_prompt", tx);
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.show_interaction_prompt", Config.isShowInteractionPrompt()),
                btn -> {
                    Config.setShowInteractionPrompt(!Config.isShowInteractionPrompt());
                    btn.setMessage(getOnOffComponent("topdown_view.config.show_interaction_prompt",
                            Config.isShowInteractionPrompt()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.show_interaction_prompt.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.interaction_prompt_scale",
                Config.getInteractionPromptScale(), 0.0, 1.0,
                val -> Config.setInteractionPromptScale(val), 1));
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.show_interaction_prompt_shadow", Config.isInteractionPromptShadow()),
                btn -> {
                    Config.setInteractionPromptShadow(!Config.isInteractionPromptShadow());
                    btn.setMessage(getOnOffComponent("topdown_view.config.show_interaction_prompt_shadow",
                            Config.isInteractionPromptShadow()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.show_interaction_prompt_shadow.tooltip")))
                .build());
        y += sp;

        // 空間プロンプト設定 (吹き出し表示)
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.show_spatial_prompt", Config.isShowSpatialPrompt()),
                btn -> {
                    Config.setShowSpatialPrompt(!Config.isShowSpatialPrompt());
                    btn.setMessage(getOnOffComponent("topdown_view.config.show_spatial_prompt",
                            Config.isShowSpatialPrompt()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.show_spatial_prompt.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.spatial_prompt_radius",
                Config.getSpatialPromptRadius(), 1.0, 16.0,
                val -> Config.setSpatialPromptRadius(val), 1));
        y += sp;
        addRightWidget(Button.builder(
                getOnOffComponent("topdown_view.config.spatial_prompt_all_blocks", Config.isSpatialPromptAllBlocks()),
                btn -> {
                    Config.setSpatialPromptAllBlocks(!Config.isSpatialPromptAllBlocks());
                    btn.setMessage(getOnOffComponent("topdown_view.config.spatial_prompt_all_blocks",
                            Config.isSpatialPromptAllBlocks()));
                }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.spatial_prompt_all_blocks.tooltip")))
                .build());
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

        y = addSection(y, "topdown_view.config.section.mob_translucency", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.mob_translucency_enabled",
                        Config.isMobTranslucencyEnabled()), btn -> {
                    Config.setMobTranslucencyEnabled(!Config.isMobTranslucencyEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.mob_translucency_enabled",
                            Config.isMobTranslucencyEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable(
                                "topdown_view.config.mob_translucency_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.mob_translucency_alpha",
                Config.getMobTranslucencyAlpha(), 0.0, 1.0,
                val -> Config.setMobTranslucencyAlpha(val)));
        y += sp;

        y = addSection(y, "topdown_view.config.section.placement_preview", tx);
        addRightWidget(
                Button.builder(getOnOffComponent("topdown_view.config.placement_preview_enabled",
                        Config.isPlacementPreviewEnabled()), btn -> {
                    Config.setPlacementPreviewEnabled(!Config.isPlacementPreviewEnabled());
                    btn.setMessage(getOnOffComponent("topdown_view.config.placement_preview_enabled",
                            Config.isPlacementPreviewEnabled()));
                }).bounds(x, y, w, h)
                        .tooltip(Tooltip.create(Component.translatable(
                                "topdown_view.config.placement_preview_enabled.tooltip")))
                        .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.placement_transparency",
                Config.getPlacementTransparency(), 0.1, 0.9,
                val -> Config.setPlacementTransparency(val)));
        y += sp;

        y = addSection(y, "topdown_view.config.section.sign_hover", tx);
        addRightWidget(Button.builder(getSignHoverModeComponent(Config.getSignHoverDisplayMode()), btn -> {
            Config.setSignHoverDisplayMode((Config.getSignHoverDisplayMode() + 1) % 3);
            btn.setMessage(getSignHoverModeComponent(Config.getSignHoverDisplayMode()));
        }).bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable("topdown_view.config.sign_hover_display_mode.tooltip")))
                .build());
        y += sp;
        addRightWidget(new ConfigSlider(x, y, w, h, "topdown_view.config.sign_hover_scale", Config.getSignHoverScale(), 0.0,
                1.0, val -> Config.setSignHoverScale(val), 1));
        y += sp;

        contentHeight = y - (30 - (int) scrollOffset) + sp;
    }

    private Component getSignHoverModeComponent(int mode) {
        String modeKey = switch (mode) {
            case 1 -> "mode_world";
            case 2 -> "mode_tooltip";
            default -> "mode_none";
        };
        return Component.translatable("topdown_view.config.sign_hover_display_mode",
                Component.translatable("topdown_view.config.sign_hover_display_mode." + modeKey).getString());
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
        Config.resetToDefaults();
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
        saveConfig();
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
