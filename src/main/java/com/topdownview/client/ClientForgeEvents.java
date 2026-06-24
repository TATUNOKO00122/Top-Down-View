package com.topdownview.client;

import com.mojang.logging.LogUtils;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import com.topdownview.Config;
import com.topdownview.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.topdownview.placement.PlacementPreviewManager;
import org.slf4j.Logger;

/**
 * Forgeバスのクライアントサイドイベントハンドラ
 * ポーズ画面へのボタン追加など
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BUTTON_SIZE = 20;

    private ClientForgeEvents() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ReachManager.onClientTick();
        PlacementPreviewManager.getInstance().onClientTick();
        OpenedContainerTracker.onTick();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            PlayerRotationController.onRenderTick(mc, event.renderTickTime);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("[TopDownView] Player joined world, resetting state");
        ModState.resetAll();
        ModState.STATUS.setEnabled(Config.isDefaultEnabled());
        ReachManager.forceUpdate();
        OpenedContainerTracker.init();
        
        if (Minecraft.getInstance().player != null) {
            PlayerRotationController.initializeFromPlayer(Minecraft.getInstance().player);
            if (ModState.STATUS.isEnabled()) {
                float playerYaw = Minecraft.getInstance().player.getYRot();
                ModState.CAMERA.setYaw(playerYaw);
                ModState.CAMERA.updatePrevYaw();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Config.clearSyncedServerReach();
        PlacementPreviewManager.getInstance().reset();
        OpenedContainerTracker.saveCurrentDimension();
        OpenedContainerTracker.clearAll();
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        net.minecraft.world.level.block.Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof net.minecraft.world.level.block.ChestBlock ||
            block instanceof net.minecraft.world.level.block.BarrelBlock ||
            block instanceof net.minecraft.world.level.block.ShulkerBoxBlock ||
            block instanceof net.minecraft.world.level.block.EnderChestBlock) {
            OpenedContainerTracker.markOpened(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) {
            return;
        }

        Component optionsLabel = Component.translatable("menu.options");

        screen.renderables.stream()
                .filter(renderable -> renderable instanceof Button)
                .map(renderable -> (Button) renderable)
                .filter(button -> button.getMessage().equals(optionsLabel))
                .findFirst()
                .ifPresent(optionsButton -> {
                    int x = optionsButton.getX() - BUTTON_SIZE - 4;
                    int y = optionsButton.getY() - 24;

                    Button configButton = Button.builder(
                            Component.literal("⚙"),
                            (btn) -> Minecraft.getInstance().setScreen(new ConfigScreen(screen)))
                            .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                            .tooltip(Tooltip.create(Component.translatable("topdown_view.pause.config_button.tooltip")))
                            .build();

                    event.addListener(configButton);
                });
    }
}
