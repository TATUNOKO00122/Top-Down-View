package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.network.ManualPickupTogglePacket;
import com.topdownview.network.PacketHandler;
import com.topdownview.network.PickupItemPacket;
import com.topdownview.state.ModState;
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

    /**
     * 手動取得の有効状態をサーバーへ同期する。
     * クリック取得はトップダウン中しか動かないため、視点OFF中は自動取得停止も解除する。
     */
    public static void sendToggle() {
        Minecraft mc = Minecraft.getInstance();
        if (!serverSupportsManualPickup(mc)) {
            return;
        }
        boolean active = Config.isManualItemPickup() && ModState.STATUS.isEnabled();
        PacketHandler.CHANNEL.sendToServer(new ManualPickupTogglePacket(active));
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
