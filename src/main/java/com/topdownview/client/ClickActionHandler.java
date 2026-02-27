package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class ClickActionHandler {

    private static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;
    private static int leftClickHoldTicks = 0;
    private static int rightClickHoldTicks = 0;
    private static final int LONG_PRESS_THRESHOLD = 5;

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onInput(int button, int action, Minecraft mc) {
        if (button == 0) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (isLeftClickDown && !wasDown) {
                leftClickHoldTicks = 0;
            } else if (!isLeftClickDown && wasDown) {
                onLeftClickRelease(mc);
                leftClickHoldTicks = 0;
            }
        } else if (button == 1) {
            boolean wasDown = isRightClickDown;
            isRightClickDown = (action != 0);

            if (isRightClickDown && !wasDown) {
                rightClickHoldTicks = 0;
                handleRightClick(mc);
            } else if (!isRightClickDown && wasDown) {
                onRightClickRelease(mc);
                rightClickHoldTicks = 0;
            }
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (isLeftClickDown) {
            leftClickHoldTicks++;
            if (leftClickHoldTicks >= LONG_PRESS_THRESHOLD) {
                handleLeftClickLongPress(mc);
            }
        }

        if (isRightClickDown) {
            rightClickHoldTicks++;
            if (rightClickHoldTicks >= LONG_PRESS_THRESHOLD) {
                handleRightClickLongPress(mc);
            }
        }
    }

    private static void onLeftClickRelease(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (!ClientForgeEvents.isTopDownView()) return;
        if (!Config.clickToMoveEnabled) return;

        if (leftClickHoldTicks < LONG_PRESS_THRESHOLD) {
            handleLeftClickSingle(mc);
        }

        ClickToMoveController.stopLongPressFollow();
    }

    private static void handleLeftClickSingle(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        var result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null) return;

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) result).getEntity();
            ClickToMoveController.setTargetEntity(entity);
        } else if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            ClickToMoveController.setDestination(blockHit.getLocation());
        }
    }

    private static void handleLeftClickLongPress(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            ClickToMoveController.startLongPressFollow();
        }
    }

    private static void handleRightClick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        if (!ClientForgeEvents.isTopDownView()) return;

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        var result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null) return;

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            mc.gameMode.interact(mc.player, ((EntityHitResult) result).getEntity(), InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    private static void handleRightClickLongPress(Minecraft mc) {
        if (!ClientForgeEvents.isTopDownView()) return;
        if (!Config.clickToMoveEnabled) return;

        if (!ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            ClickToMoveController.startLongPressFollow();
        }
    }

    private static void onRightClickRelease(Minecraft mc) {
        ClickToMoveController.stopLongPressFollow();
    }
}
