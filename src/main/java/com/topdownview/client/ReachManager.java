package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.network.PacketHandler;
import com.topdownview.network.ReachTogglePacket;
import com.topdownview.state.ModStatus;
import net.minecraft.client.Minecraft;
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
    private static final double ATTRIBUTE_BASE = 5.0;

    private static boolean applied = false;
    private static double lastAppliedReach = -1;
    private static int tickCounter = 0;

    private ReachManager() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean isReachAllowed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) return true;
        return Config.hasSyncedServerReach();
    }

    public static void onClientTick() {
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean shouldApply = ModStatus.INSTANCE.isEnabled()
                && Config.isScreenReachEnabled()
                && isReachAllowed();
        double effectiveReach = Config.getEffectiveReachDistance();

        if (shouldApply && (!applied || lastAppliedReach != effectiveReach)) {
            applyAll();
        } else if (!shouldApply && applied) {
            removeAll();
        }
    }

    public static void forceUpdate() {
        applied = false;
        lastAppliedReach = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean shouldApply = ModStatus.INSTANCE.isEnabled()
                && Config.isScreenReachEnabled()
                && isReachAllowed();
        if (shouldApply) {
            applyAll();
        } else {
            removeAll();
        }
    }

    public static void sendTogglePacket(boolean enabled) {
        PacketHandler.CHANNEL.sendToServer(new ReachTogglePacket(enabled));
    }

    private static void applyAll() {
        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null) return;

        double effectiveReach = Config.getEffectiveReachDistance();
        double extendedReach = effectiveReach - ATTRIBUTE_BASE;

        applyToPlayer(localPlayer, extendedReach);

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(localPlayer.getUUID());
                if (serverPlayer != null) {
                    applyToPlayer(serverPlayer, extendedReach);
                }
            });
        } else {
            sendTogglePacket(true);
        }

        applied = true;
        lastAppliedReach = effectiveReach;
        LOGGER.info("[TopDownView] Screen reach modifiers applied (reach={}, extended={})",
                effectiveReach, extendedReach);
    }

    private static void removeAll() {
        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer != null) {
            removeFromPlayer(localPlayer);
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && localPlayer != null) {
            server.execute(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(localPlayer.getUUID());
                if (serverPlayer != null) {
                    removeFromPlayer(serverPlayer);
                }
            });
        } else {
            sendTogglePacket(false);
        }

        applied = false;
        LOGGER.info("[TopDownView] Screen reach modifiers removed");
    }

    private static void applyToPlayer(Player player, double extendedReach) {
        AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (blockReach != null) {
            if (blockReach.getModifier(BLOCK_REACH_UUID) != null) {
                blockReach.removeModifier(BLOCK_REACH_UUID);
            }
            blockReach.addPermanentModifier(new AttributeModifier(
                    BLOCK_REACH_UUID, "topdown_view.block_reach",
                    extendedReach, AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (entityReach != null) {
            if (entityReach.getModifier(ENTITY_REACH_UUID) != null) {
                entityReach.removeModifier(ENTITY_REACH_UUID);
            }
            entityReach.addPermanentModifier(new AttributeModifier(
                    ENTITY_REACH_UUID, "topdown_view.entity_reach",
                    extendedReach, AttributeModifier.Operation.ADDITION));
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