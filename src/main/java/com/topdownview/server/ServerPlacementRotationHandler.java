package com.topdownview.server;

import com.topdownview.network.PacketHandler;
import com.topdownview.network.ReachSyncPacket;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側: プレイヤーごとのブロック配置方向を保持
 *
 * クライアントから PlacementRotationPacket を受信してマップを更新し、
 * BlockItemPlacementMixin が getPlacementState 時に getFacing(player) で参照する。
 *
 * プレイヤーログアウト/サーバー停止時にマップをクリアする。
 * 専用サーバーでのみ動作（シングルプレイ時はクライアント側 PlacementRotationState を直接参照）。
 */
@Mod.EventBusSubscriber(modid = "topdown_view", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerPlacementRotationHandler {

    private static final Map<UUID, Direction> playerFacings = new ConcurrentHashMap<>();

    private ServerPlacementRotationHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean isDedicatedServer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isDedicatedServer();
    }

    /**
     * パケット受信: プレイヤーの配置向きを更新（null でクリア）
     */
    public static void onRotationReceived(ServerPlayer player, @Nullable Direction facing) {
        if (player == null) return;
        if (!isDedicatedServer(player)) return;

        if (facing == null) {
            playerFacings.remove(player.getUUID());
        } else {
            playerFacings.put(player.getUUID(), facing);
        }
    }

    /**
     * BlockItemPlacementMixin から呼び出される: 指定プレイヤーの現在の配置向きを取得
     * @return 指定向き（null = オーバーライドなし、Vanillaの視線ベース）
     */
    @Nullable
    public static Direction getFacing(ServerPlayer player) {
        return playerFacings.get(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        playerFacings.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        playerFacings.clear();
    }
}
