package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 遠いドロップアイテムへ近づいて取得する専用の接近処理。
 * クリック移動とは独立して動作し、クリック移動が有効なときはその移動機構へ委譲する。
 */
public final class PickupApproachController {

    // 対象アイテムへ近づく際の最大待機tick（超えたら諦める）
    private static final int TIMEOUT_TICKS = 200;

    private static int targetId = -1;
    private static int ticks = 0;
    private static boolean delegatedToClickToMove = false;

    private PickupApproachController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean isActive() {
        return targetId >= 0;
    }

    public static void start(Minecraft mc, ItemEntity item) {
        targetId = item.getId();
        ticks = 0;
        delegatedToClickToMove = Config.isClickToMoveEnabled();
        if (delegatedToClickToMove) {
            // クリック移動が有効ならその経路移動を利用する
            ClickToMoveController.setDestination(item.position());
        }
    }

    public static void tick(Minecraft mc) {
        if (targetId < 0) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            cancel();
            return;
        }

        Entity entity = mc.level.getEntity(targetId);
        if (!(entity instanceof ItemEntity item) || item.isRemoved() || item.getItem().isEmpty()) {
            cancel();
            return;
        }

        double range = Config.getEffectiveManualPickupDistance();
        if (mc.player.distanceToSqr(item) <= range * range) {
            ClientPickupHandler.sendPickup(item.getId());
            ClickToMoveController.stop();
            cancel();
            return;
        }

        if (delegatedToClickToMove) {
            // アイテムが動いても追従できるよう目標位置を更新する（経路の再計算はしない）
            ModState.CLICK_TO_MOVE.setTargetPosition(item.position());
        }

        if (++ticks > TIMEOUT_TICKS) {
            cancel();
        }
    }

    /** クリック移動OFF時の自前移動で使う目標位置。 */
    public static Vec3 getTargetPosition(Minecraft mc) {
        if (targetId < 0 || mc.level == null) {
            return null;
        }
        Entity entity = mc.level.getEntity(targetId);
        return entity != null ? entity.position() : null;
    }

    public static void cancel() {
        targetId = -1;
        ticks = 0;
        delegatedToClickToMove = false;
    }
}
