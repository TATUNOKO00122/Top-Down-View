package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
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

    private static boolean isLeftClickDown = false;

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

        if (button == attackButton) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (ModState.STATUS.isEnabled() && action != 0) {
                // ラベル上のクリックは押下状態に関係なく取得を試みる（1回目が無反応になるのを防ぐ）
                if (Config.isManualItemPickup() && tryPickupItem(mc)) {
                    return;
                }
                // 別の操作を始めたら接近取得は中断する
                PickupApproachController.cancel();
                if (!wasDown) {
                    if (Config.isClickToMoveEnabled()) {
                        handleLeftClickPress(mc);
                    } else {
                        handleTargetLockOnly(mc);
                    }
                }
            }
        }
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