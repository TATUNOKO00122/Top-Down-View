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

    public static boolean isLeftClickDown() {
        return isLeftClickDown;
    }

    public static void onInput(int button, int action, Minecraft mc) {
        int attackButton = mc.options.keyAttack.getKey().getValue();
        int useButton = mc.options.keyUse.getKey().getValue();

        if (button == attackButton) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (ModState.STATUS.isEnabled() && action != 0 && !wasDown) {
                if (Config.isClickToMoveEnabled()) {
                    handleLeftClickPress(mc);
                } else {
                    handleTargetLockOnly(mc);
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