package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.UUID;

public final class ReachManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID BLOCK_REACH_UUID = UUID.fromString("d5a0e8b3-1f2c-4a7d-9b3e-5c6f8a2d1e04");
    private static final UUID ENTITY_REACH_UUID = UUID.fromString("a7c3f9d2-4e5b-8f1a-6d2c-7b9e0f3a4c51");
    private static final double EXTENDED_REACH = 507.5;

    private static boolean applied = false;
    private static int tickCounter = 0;

    private ReachManager() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onClientTick() {
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        boolean shouldApply = ModStatus.INSTANCE.isEnabled() && Config.isScreenReachEnabled();

        if (shouldApply && !applied) {
            applyAll();
        } else if (!shouldApply && applied) {
            removeAll();
        }
    }

    public static void forceUpdate() {
        applied = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        boolean shouldApply = ModStatus.INSTANCE.isEnabled() && Config.isScreenReachEnabled();
        if (shouldApply) {
            applyAll();
        } else {
            removeAll();
        }
    }

    private static void applyAll() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) return;

        applyToPlayer(localPlayer);

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(localPlayer.getUUID());
                if (serverPlayer != null) {
                    applyToPlayer(serverPlayer);
                    LOGGER.info("[TopDownView] Screen reach modifiers applied to ServerPlayer");
                }
            });
        }

        applied = true;
        LOGGER.info("[TopDownView] Screen reach modifiers applied (block={}, entity={})",
                localPlayer.getBlockReach(), localPlayer.getEntityReach());
    }

    private static void removeAll() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer != null) {
            removeFromPlayer(localPlayer);
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && localPlayer != null) {
            server.execute(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(localPlayer.getUUID());
                if (serverPlayer != null) {
                    removeFromPlayer(serverPlayer);
                    LOGGER.info("[TopDownView] Screen reach modifiers removed from ServerPlayer");
                }
            });
        }

        applied = false;
        LOGGER.info("[TopDownView] Screen reach modifiers removed");
    }

    private static void applyToPlayer(Player player) {
        AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (blockReach != null) {
            if (blockReach.getModifier(BLOCK_REACH_UUID) != null) {
                blockReach.removeModifier(BLOCK_REACH_UUID);
            }
            blockReach.addPermanentModifier(new AttributeModifier(
                    BLOCK_REACH_UUID, "topdown_view.block_reach",
                    EXTENDED_REACH, AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (entityReach != null) {
            if (entityReach.getModifier(ENTITY_REACH_UUID) != null) {
                entityReach.removeModifier(ENTITY_REACH_UUID);
            }
            entityReach.addPermanentModifier(new AttributeModifier(
                    ENTITY_REACH_UUID, "topdown_view.entity_reach",
                    EXTENDED_REACH, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void removeFromPlayer(Player player) {
        AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (blockReach != null) {
            blockReach.removeModifier(BLOCK_REACH_UUID);
        }

        AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (entityReach != null) {
            entityReach.removeModifier(ENTITY_REACH_UUID);
        }
    }
}