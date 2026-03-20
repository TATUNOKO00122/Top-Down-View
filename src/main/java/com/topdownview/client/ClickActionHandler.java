package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClickActionHandler {

    private static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;
    private static int leftClickHoldTicks = 0;
    private static final int HOLD_THRESHOLD_TICKS = 5;

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean isLeftClickDown() {
        return isLeftClickDown;
    }

    public static boolean onInput(int button, int action, Minecraft mc) {
        int attackButton = mc.options.keyAttack.getKey().getValue();
        int useButton = mc.options.keyUse.getKey().getValue();

        if (button == attackButton) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (isLeftClickDown && !wasDown) {
                leftClickHoldTicks = 0;
            }

            if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled()) {
                if (isLeftClickDown && !wasDown) {
                    handleLeftClickPress(mc);
                }
                return true;
            }
            return false;
        } else if (button == useButton) {
            boolean wasDown = isRightClickDown;
            isRightClickDown = (action != 0);
        }
        return false;
    }

    private static void handleLeftClickPress(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

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
                    ClickToMoveController.startFollowAndAttack(entity);
                    return;
                }
                case INTERACT -> {
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
        if (mc.level == null || mc.player == null) return;

        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            Entity entity = entityHit.getEntity();

            ClickToMoveController.EntityAction action = ClickToMoveController.getEntityAction(entity);

            if (action == ClickToMoveController.EntityAction.ATTACK) {
                ClickToMoveController.startFollowAndAttack(entity);
                return;
            } else if (action == ClickToMoveController.EntityAction.INTERACT) {
                ClickToMoveController.startInteractEntity(entity);
                return;
            }
        }

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos blockPos = blockHit.getBlockPos();
            ClickToMoveController.startDestroyBlock(blockPos);
        }
    }

    public static void onClientTick(Minecraft mc) {
        ModState.CLICK_TO_MOVE.tickAttackCooldown();

        if (isLeftClickDown) {
            leftClickHoldTicks++;
        }

        // 左クリック離上時の処理
        if (!isLeftClickDown && leftClickHoldTicks > 0) {
            boolean wasHold = leftClickHoldTicks >= HOLD_THRESHOLD_TICKS;

            if (wasHold) {
                // ホールド離上時の処理
                if (ModState.CLICK_TO_MOVE.isInteracting()) {
                    // 商人・村人/インタラクト可能ブロック: 停止
                    ClickToMoveController.stop();
                } else if (ModState.CLICK_TO_MOVE.isDestroying()) {
                    // 破壊: 停止
                    ClickToMoveController.stop();
                }
                // 敵攻撃ホールド: 追従継続
                // 通常ブロック移動: 追従継続
            } else {
                // 単発クリック離上時: 移動継続、到達時に自動実行
                ModState.CLICK_TO_MOVE.setHoldMode(false);
            }
            leftClickHoldTicks = 0;
        }

        if (isLeftClickDown && ModState.CLICK_TO_MOVE.isMoving()) {
            // 地形ホールド追従
            if (!ModState.CLICK_TO_MOVE.isAttacking() &&
                !ModState.CLICK_TO_MOVE.isDestroying() &&
                !ModState.CLICK_TO_MOVE.isInteracting()) {
                ClickToMoveController.tickTerrainFollow(mc);
            }
        }

        if (isLeftClickDown) {
            if (ModState.CLICK_TO_MOVE.isAttacking()) {
                ClickToMoveController.tickAttackFollow(mc);
            } else if (ModState.CLICK_TO_MOVE.isDestroying()) {
                ClickToMoveController.tickDestroyFollow(mc);
            }
        }
    }
}