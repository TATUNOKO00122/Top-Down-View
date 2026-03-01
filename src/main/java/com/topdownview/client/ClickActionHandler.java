package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClickActionHandler {

    private static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;
    private static int leftClickHoldTicks = 0;
    private static int rightClickHoldTicks = 0;
    private static final int LONG_PRESS_THRESHOLD = 5;

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean onInput(int button, int action, Minecraft mc) {
        if (button == 0) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (isLeftClickDown && !wasDown) {
                leftClickHoldTicks = 0;
            } else if (!isLeftClickDown && wasDown) {
                leftClickHoldTicks = 0;
            }
            return false;
        } else if (button == 1) {
            boolean wasDown = isRightClickDown;
            isRightClickDown = (action != 0);

            if (isRightClickDown && !wasDown) {
                rightClickHoldTicks = 0;
                boolean handled = handleRightClick(mc);
                return handled;
            } else if (!isRightClickDown && wasDown) {
                onRightClickRelease(mc);
                rightClickHoldTicks = 0;
            }
        }
        return false;
    }

    public static void onClientTick(Minecraft mc) {
        if (isLeftClickDown) {
            leftClickHoldTicks++;
        }

        if (isRightClickDown) {
            rightClickHoldTicks++;
            if (rightClickHoldTicks >= LONG_PRESS_THRESHOLD) {
                handleRightClickLongPress(mc);
            }
        }
    }

    private static boolean handleRightClick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        if (!ModState.STATUS.isEnabled()) return false;

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null || result.getType() == HitResult.Type.MISS) return false;

        if (result.getType() == HitResult.Type.ENTITY) {
            if (Config.clickToMoveEnabled) {
                EntityHitResult entityHit = (EntityHitResult) result;
                Entity entity = entityHit.getEntity();
                ModState.CLICK_TO_MOVE.startFollowEntity(entity, mc.player.position());
            }
            return false;
        }

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos blockPos = blockHit.getBlockPos();

            boolean isInteractable = InteractableBlocks.isInteractable(
                    mc.level.getBlockState(blockPos), mc.level, blockPos);

            if (isInteractable) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return true;
            }

            if (Config.clickToMoveEnabled) {
                Vec3 destination = blockHit.getLocation();
                ModState.CLICK_TO_MOVE.startMoveTo(destination, mc.player.position());
            }
        }
        return false;
    }

    private static void handleRightClickLongPress(Minecraft mc) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!Config.clickToMoveEnabled) return;

        if (!ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            ModState.CLICK_TO_MOVE.setLongPressFollow(true);
        }
    }

    private static void onRightClickRelease(Minecraft mc) {
        ModState.CLICK_TO_MOVE.setLongPressFollow(false);
    }
}
