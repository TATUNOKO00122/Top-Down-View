package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.network.ManualPickupTogglePacket;
import com.topdownview.network.PacketHandler;
import com.topdownview.network.PickupItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/**
 * 手動アイテム取得のクライアント側送信処理。
 * サーバーにMODが無い場合（バニラサーバー）は送信をスキップして通常動作を保つ。
 */
public final class ClientPickupHandler {

    private ClientPickupHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 接続先サーバーが本MODのチャンネルを持っているか。 */
    public static boolean serverSupportsManualPickup(Minecraft mc) {
        if (mc.getConnection() == null) {
            return false;
        }
        Connection connection = mc.getConnection().getConnection();
        return connection != null && PacketHandler.CHANNEL.isRemotePresent(connection);
    }

    /** 現在の設定値をサーバーへ同期する。 */
    public static void sendToggle() {
        Minecraft mc = Minecraft.getInstance();
        if (!serverSupportsManualPickup(mc)) {
            return;
        }
        PacketHandler.CHANNEL.sendToServer(new ManualPickupTogglePacket(Config.isManualItemPickup()));
    }

    /** 指定アイテムの取得をサーバーへ要求する。 */
    public static void sendPickup(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (!serverSupportsManualPickup(mc)) {
            return;
        }
        PacketHandler.CHANNEL.sendToServer(new PickupItemPacket(entityId));
    }
}
