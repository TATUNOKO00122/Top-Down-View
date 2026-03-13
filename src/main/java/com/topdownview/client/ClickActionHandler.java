package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClickActionHandler {

    public static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;
    private static int leftClickHoldTicks = 0;
    private static int rightClickHoldTicks = 0;
    private static final int LONG_PRESS_THRESHOLD = 5;

    private ClickActionHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static boolean isRangedWeapon(Item item) {
        if (item instanceof ProjectileWeaponItem)
            return true;
        if (item instanceof SnowballItem)
            return true;
        if (item instanceof EnderpearlItem)
            return true;
        if (item instanceof SplashPotionItem)
            return true;
        if (item instanceof LingeringPotionItem)
            return true;
        if (item instanceof EggItem)
            return true;
        if (item instanceof ExperienceBottleItem)
            return true;
        if (item instanceof FireChargeItem)
            return true;
        return false;
    }

    private static boolean isBlockItem(Item item) {
        return item instanceof BlockItem;
    }

    public static boolean onInput(int button, int action, Minecraft mc) {
        // キー設定に基づいて攻撃/使用ボタンを判定
        int attackButton = mc.options.keyAttack.getKey().getValue();
        int useButton = mc.options.keyUse.getKey().getValue();

        if (button == attackButton) {
            boolean wasDown = isLeftClickDown;
            isLeftClickDown = (action != 0);

            if (isLeftClickDown && !wasDown) {
                leftClickHoldTicks = 0;
            } else if (!isLeftClickDown && wasDown) {
                leftClickHoldTicks = 0;
            }
            return false;
        } else if (button == useButton) {
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
        if (mc.level == null || mc.player == null)
            return false;
        if (!ModState.STATUS.isEnabled())
            return false;

        Item mainHandItem = mc.player.getMainHandItem().getItem();
        boolean hasRangedWeapon = isRangedWeapon(mainHandItem);
        boolean hasBlockItem = isBlockItem(mainHandItem);

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult result = MouseRaycast.INSTANCE.getLastHitResult();

        if (result == null || result.getType() == HitResult.Type.MISS)
            return false;

        if (result.getType() == HitResult.Type.ENTITY) {
            if (Config.isClickToMoveEnabled() && !hasRangedWeapon) {
                EntityHitResult entityHit = (EntityHitResult) result;
                Entity entity = entityHit.getEntity();
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ClickToMoveController.setTargetEntity(entity);
                }
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

            if (hasRangedWeapon) {
                return false;
            }

            if (hasBlockItem) {
                return false;
            }

            if (Config.isClickToMoveEnabled()) {
                Vec3 destination = blockHit.getLocation();
                ClickToMoveController.setDestination(destination);
                if (Config.isDestinationHighlightEnabled()) {
                    ModState.DESTINATION_HIGHLIGHT.startAnimation();
                }
            }
        }
        return false;
    }

    private static void handleRightClickLongPress(Minecraft mc) {
        if (!ModState.STATUS.isEnabled())
            return;
        if (!Config.isClickToMoveEnabled())
            return;

        if (!ModState.CLICK_TO_MOVE.isLongPressFollow()) {
            ClickToMoveController.startLongPressFollow();
        }
    }

    private static void onRightClickRelease(Minecraft mc) {
        ClickToMoveController.stopLongPressFollow();
    }
}
