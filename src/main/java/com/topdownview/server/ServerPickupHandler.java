package com.topdownview.server;

import com.topdownview.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手動アイテム取得（自動取得の停止と、クリックによる取得要求）のサーバー側処理。
 * 手動モードのプレイヤーに限り {@link ItemEntity#playerTouch} を抑止し、
 * クリック要求パケットを受けて対象を検証したうえで取得を実行する。
 */
@Mod.EventBusSubscriber(modid = "topdown_view", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerPickupHandler {

    private static final Set<UUID> manualPickupPlayers = ConcurrentHashMap.newKeySet();
    // クリック取得で playerTouch を呼んでいる間だけ抑止を解除する（サーバースレッド上でのみ操作）
    private static boolean manualPickupInProgress = false;

    private ServerPickupHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onToggleReceived(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return;
        }
        if (enabled) {
            manualPickupPlayers.add(player.getUUID());
        } else {
            manualPickupPlayers.remove(player.getUUID());
        }
    }

    public static void onPickupRequest(ServerPlayer player, int entityId) {
        if (player == null || !manualPickupPlayers.contains(player.getUUID())) {
            return;
        }

        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof ItemEntity item) || item.isRemoved()) {
            return;
        }
        double range = Config.getEffectiveManualPickupDistance();
        if (player.distanceToSqr(item) > range * range) {
            return;
        }

        // 手動取得はプレイヤーの明示的な操作なので、バニラの取得ディレイ（自分のドロップ直後は約2秒）を
        // 無視して即座に取得できるようにする。所有者判定（target）は playerTouch 側で維持される。
        item.setNoPickUpDelay();

        manualPickupInProgress = true;
        try {
            item.playerTouch(player);
        } finally {
            manualPickupInProgress = false;
        }
    }

    /** 自動取得を抑止すべきか。手動取得の実行中は抑止しない。 */
    public static boolean shouldSuppressAutoPickup(Player player) {
        if (player.level().isClientSide() || manualPickupInProgress) {
            return false;
        }
        return manualPickupPlayers.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            manualPickupPlayers.remove(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        manualPickupPlayers.clear();
        manualPickupInProgress = false;
    }
}
