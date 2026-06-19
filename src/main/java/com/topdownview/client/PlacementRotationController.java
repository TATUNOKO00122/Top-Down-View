package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.network.PacketHandler;
import com.topdownview.network.PlacementRotationPacket;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * ブロック配置方向手動指定のコントローラ
 *
 * キー入力から PlacementRotationState を操作する。
 * サーバーMOD有無判定とフィードバックメッセージを担当。
 *
 * 動作条件:
 *   - Config.placementRotationEnabled が true
 *   - トップダウンビューが有効
 *   - シングルプレイ or サーバーに TopDownView がロードされている
 */
public final class PlacementRotationController {

    private PlacementRotationController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 上下方向（UP/DOWN）をトグルする。
     */
    public static void toggleVertical() {
        if (!canUse()) return;
        ModState.PLACEMENT_ROTATION.toggleVertical();
        syncToServer();
        showFeedback();
    }

    /**
     * 水平方向を時計回りにサイクルする（NORTH → EAST → SOUTH → WEST → NORTH）。
     */
    public static void cycleHorizontal() {
        if (!canUse()) return;
        ModState.PLACEMENT_ROTATION.cycleHorizontalClockwise();
        syncToServer();
        showFeedback();
    }

    /**
     * 機能が使用可能か判定する。
     * 初回キー押下時に PlacementRotationState.enabled を true にする。
     */
    private static boolean canUse() {
        if (!Config.isPlacementRotationEnabled()) return false;
        if (!ModState.STATUS.isEnabled()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        // サーバーに TopDownView がロードされているか
        // シングルプレイ or ReachSync 受信済み
        if (mc.getSingleplayerServer() == null && !Config.hasSyncedServerReach()) {
            mc.player.displayClientMessage(
                    Component.translatable("topdown_view.placement_rotation.no_server_mod"), true);
            return false;
        }

        // 初回有効化
        if (!ModState.PLACEMENT_ROTATION.isEnabled()) {
            ModState.PLACEMENT_ROTATION.setEnabled(true);
        }
        return true;
    }

    /**
     * サーバーへ現在の向きを同期する。
     * シングルプレイ時は送信不要（統合サーバーが同じJVM内の ModState を参照するため）。
     */
    private static void syncToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.getSingleplayerServer() != null) return; // シングルプレイ時は不要

        Direction facing = ModState.PLACEMENT_ROTATION.getCurrentFacing();
        PacketHandler.CHANNEL.sendToServer(new PlacementRotationPacket(facing));
    }

    /**
     * 現在の向きをアクションバーに表示するフィードバック。
     */
    private static void showFeedback() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        Direction facing = ModState.PLACEMENT_ROTATION.getCurrentFacing();
        if (facing == null) {
            mc.player.displayClientMessage(
                    Component.translatable("topdown_view.placement_rotation.cleared"), true);
        } else {
            mc.player.displayClientMessage(
                    Component.translatable("topdown_view.placement_rotation.current",
                            Component.translatable("topdown_view.placement_rotation.facing." + facing.getName())), true);
        }
    }

    /**
     * Config 変更時に呼び出す。機能無効化時に State をクリア。
     */
    public static void onConfigChanged() {
        if (!Config.isPlacementRotationEnabled()) {
            ModState.PLACEMENT_ROTATION.setEnabled(false);
            syncToServer();
        }
    }
}
