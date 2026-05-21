package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClickActionHandler {

    private static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean isLeftClickDown() {
        return isLeftClickDown;
    }

    public static void onInput(int button, int action, Minecraft mc) {
        int attackButton = mc.options.keyAttack.getKey().getValue();
        int useButton = mc.options.keyUse.getKey().getValue();

        if (button == attackButton) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled()) {
                if (action != 0 && !wasDown) {
                    handleLeftClickPress(mc);
                }
            }
        } else if (button == useButton) {
            boolean wasDown = isRightClickDown;
            isRightClickDown = (action != 0);

            if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled()) {
                if (action != 0 && !wasDown) {
                    ((com.topdownview.mixin.MinecraftInvoker) Minecraft.getInstance()).invokeStartUseItem();
                }
            }
        }
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
            Direction direction = blockHit.getDirection();
            ClickToMoveController.startDestroyBlock(blockPos, direction);
        }
    }
}