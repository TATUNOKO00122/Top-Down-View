package com.topdownview.server;

import com.topdownview.Config;
import com.topdownview.network.PacketHandler;
import com.topdownview.network.ReachSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "topdown_view", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerReachHandler {

    private static final UUID BLOCK_REACH_UUID = UUID.fromString("d5a0e8b3-1f2c-4a7d-9b3e-5c6f8a2d1e04");
    private static final UUID ENTITY_REACH_UUID = UUID.fromString("a7c3f9d2-4e5b-8f1a-6d2c-7b9e0f3a4c51");
    private static final double ATTRIBUTE_BASE = 5.0;

    private static final Map<UUID, Boolean> playerEnabled = new ConcurrentHashMap<>();

    private ServerReachHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean isDedicatedServer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isDedicatedServer();
    }

    public static void onToggleReceived(ServerPlayer player, boolean enabled) {
        if (player == null) return;
        if (!isDedicatedServer(player)) return;

        playerEnabled.put(player.getUUID(), enabled);
        if (enabled) {
            applyReach(player);
        } else {
            removeReach(player);
        }
        sendReachSync(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!isDedicatedServer(serverPlayer)) return;

        sendReachSync(serverPlayer);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        playerEnabled.remove(serverPlayer.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!isDedicatedServer(serverPlayer)) return;

        Boolean enabled = playerEnabled.get(serverPlayer.getUUID());
        if (enabled != null && enabled) {
            applyReach(serverPlayer);
            sendReachSync(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        playerEnabled.clear();
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!isDedicatedServer(serverPlayer)) return;

        Boolean enabled = playerEnabled.get(serverPlayer.getUUID());
        if (enabled != null && enabled) {
            applyReach(serverPlayer);
            sendReachSync(serverPlayer);
        }
    }

    private static void applyReach(ServerPlayer player) {
        double reachDistance = Config.getServerReachDistance();
        double extendedReach = reachDistance - ATTRIBUTE_BASE;

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

    private static void removeReach(ServerPlayer player) {
        AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (blockReach != null) {
            blockReach.removeModifier(BLOCK_REACH_UUID);
        }

        AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (entityReach != null) {
            entityReach.removeModifier(ENTITY_REACH_UUID);
        }
    }

    private static void sendReachSync(ServerPlayer player) {
        PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ReachSyncPacket(Config.getServerReachDistance()));
    }
}