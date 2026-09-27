package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClickActionHandler {

    /** タップと長押しを区別する閾値(ms)。これ未満は従来の1クリック移動。 */
    private static final long HOLD_THRESHOLD_MS = 200;

    private static boolean isLeftClickDown = false;
    private static boolean holdEligible = false;
    private static long holdStartMs = 0;
    // 拾得クリックの押下中は攻撃（ブロック破壊）を抑止する。ラベルが消えた後も同クリックで地面を掘らないため。
    private static boolean suppressAttack = false;

    private static void lockTarget(Entity entity) {
        if (!Config.isTargetLockEnabled()) return;
        int duration = Config.getTargetLockDuration();
        if (duration > 0) {
            ModState.TARGET_LOCK.lock(entity, duration);
        }
    }

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * level/player が揃っているか。トップダウン処理の前提チェック。
     */
    private static boolean hasWorldContext(Minecraft mc) {
        return mc.level != null && mc.player != null;
    }

    public static void onInput(int button, int action, Minecraft mc) {
        int attackButton = mc.options.keyAttack.getKey().getValue();
        if (button != attackButton) return;

        boolean wasDown = isLeftClickDown;
        boolean isDown = action != 0;

        if (!isDown) {
            // 長押し追従中に離したらその場で停止する（タップは従来通り到着まで継続）
            if (wasDown && isHoldMoveEngaged() && !ModState.CLICK_TO_MOVE.useBaritone()) {
                ClickToMoveController.stop();
            }
            isLeftClickDown = false;
            holdEligible = false;
            suppressAttack = false;
            return;
        }

        isLeftClickDown = true;
        // 押しっぱなし継続中はエッジ処理しない
        if (wasDown) return;

        holdEligible = false;
        holdStartMs = Util.getMillis();
        suppressAttack = false;

        // GUI表示中はクリック移動を開始しない（ボタン状態の追跡のみ行う）
        if (mc.screen != null || !ModState.STATUS.isEnabled()) return;

        // ラベル上のクリックは押下状態に関係なく取得を試みる（1回目が無反応になるのを防ぐ）
        if (Config.isManualItemPickup() && tryPickupItem(mc)) {
            suppressAttack = true;
            return;
        }
        // 別の操作を始めたら接近取得は中断する
        PickupApproachController.cancel();
        if (Config.isClickToMoveEnabled()) {
            handleLeftClickPress(mc);
        } else {
            handleTargetLockOnly(mc);
        }
    }

    /** 長押しによるカーソル追従移動が有効か。対象ブロックへの移動を開始した押下のみ対象。 */
    public static boolean isHoldMoveEngaged() {
        return isLeftClickDown && holdEligible
                && Util.getMillis() - holdStartMs >= HOLD_THRESHOLD_MS;
    }

    /** 長押しセッションを打ち切る（手動移動・ジャンプ・GUI表示など）。 */
    public static void cancelHoldSession() {
        holdEligible = false;
        holdStartMs = 0;
    }

    /** 入力セッションのリセット（ワールド出入り時）。 */
    public static void resetInput() {
        isLeftClickDown = false;
        cancelHoldSession();
        suppressAttack = false;
    }

    /** 拾得クリックの押下中か（この間はバニラの攻撃・ブロック破壊を抑止する）。 */
    public static boolean isAttackSuppressed() {
        return suppressAttack;
    }

    /**
     * カーソル下の取得対象（ラベル＝ネームタグ上）を返す。
     * アイテム本体は対象に含めない（多数のアイテムがある場合の誤爆を避けるため）。
     */
    public static ItemEntity findPickupTarget(Minecraft mc) {
        if (!hasWorldContext(mc)) return null;
        return DroppedItemLabelRenderer.findItemUnderCursor(mc);
    }

    /**
     * カーソル下の取得対象をクリックで取得する。取得対象があればクリックを消費する
     * （背後ブロックの破壊・攻撃を起こさない）。
     * 範囲内なら即取得、範囲外なら対象まで近づいてから取得する。
     */
    private static boolean tryPickupItem(Minecraft mc) {
        if (!hasWorldContext(mc)) return false;
        if (!ClientPickupHandler.serverSupportsManualPickup(mc)) return false;

        ItemEntity item = findPickupTarget(mc);
        if (item == null) return false;

        double range = Config.getEffectiveManualPickupDistance();
        if (mc.player.distanceToSqr(item) <= range * range) {
            ClientPickupHandler.sendPickup(item.getId());
            PickupApproachController.cancel();
        } else {
            PickupApproachController.start(mc, item);
        }
        return true;
    }

    private static void handleTargetLockOnly(Minecraft mc) {
        if (!hasWorldContext(mc)) return;

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null || result.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) result).getEntity();
        if (entity instanceof net.minecraft.world.entity.LivingEntity && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            lockTarget(entity);
        }
    }

    private static void handleLeftClickPress(Minecraft mc) {
        if (!hasWorldContext(mc)) return;

        boolean destroyMode = ClientModBusEvents.DESTROY_KEY.isDown();

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null || result.getType() == HitResult.Type.MISS) {
            return;
        }

        if (destroyMode) {
            handleDestroyMode(mc, result);
            return;
        }

        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            Entity entity = entityHit.getEntity();

            ClickToMoveController.EntityAction action = ClickToMoveController.getEntityAction(entity);

            switch (action) {
                case ATTACK -> {
                    lockTarget(entity);
                    ClickToMoveController.startFollowAndAttack(entity);
                    return;
                }
                case INTERACT -> {
                    lockTarget(entity);
                    ClickToMoveController.startInteractEntity(entity);
                    return;
                }
                case IGNORE -> {
                }
            }
        }

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos blockPos = blockHit.getBlockPos();

            boolean isInteractable = InteractableBlocks.isInteractable(
                    mc.level.getBlockState(blockPos), mc.level, blockPos);

            if (isInteractable) {
                ClickToMoveController.startInteractBlock(blockPos);
                return;
            }

            Vec3 destination = blockHit.getLocation();
            ClickToMoveController.setDestination(destination);
            holdEligible = true;
            if (Config.isDestinationHighlightEnabled()) {
                ModState.DESTINATION_HIGHLIGHT.startAnimation();
            }
        }
    }

    private static void handleDestroyMode(Minecraft mc, HitResult result) {
        if (!hasWorldContext(mc)) return;

        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            Entity entity = entityHit.getEntity();

            ClickToMoveController.EntityAction action = ClickToMoveController.getEntityAction(entity);

            if (action == ClickToMoveController.EntityAction.ATTACK) {
                lockTarget(entity);
                ClickToMoveController.startFollowAndAttack(entity);
                return;
            } else if (action == ClickToMoveController.EntityAction.INTERACT) {
                lockTarget(entity);
                ClickToMoveController.startInteractEntity(entity);
                return;
            }
        }

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos blockPos = blockHit.getBlockPos();
            Direction direction = blockHit.getDirection();
            ClickToMoveController.startDestroyBlock(blockPos, direction);
        }
    }
}