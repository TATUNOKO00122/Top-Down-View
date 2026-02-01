package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class ClickActionHandler {

    private static boolean isLeftClickDown = false;
    private static boolean isRightClickDown = false;

    /**
     * マウスクリックイベントを処理する
     * 
     * @param button 0: Left, 1: Right
     * @param action 1: Press, 0: Release
     */
    public static void onInput(int button, int action, Minecraft mc) {
        if (button == 0) {
            isLeftClickDown = (action != 0); // Press or Repeat
            if (isLeftClickDown) {
                handleLeftClick(mc);
            }
        } else if (button == 1) {
            isRightClickDown = (action != 0);
            if (isRightClickDown) {
                handleRightClick(mc);
            }
        }
    }

    public static void onClientTick(Minecraft mc) {
        // 左クリック長押しによるブロック破壊の継続処理
        if (isLeftClickDown && mc.level != null && mc.player != null) {
            handleBlockMining(mc);
        }
    }

    private static void handleLeftClick(Minecraft mc) {
        if (mc.level == null || mc.player == null)
            return;

        // カスタムリーチ距離を使用（カリング距離と同期）
        double reach = MouseRaycast.getCustomReachDistance();
        net.minecraft.world.phys.HitResult result = MouseRaycast.getHitResult(mc, 1.0f, reach);

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            // エンティティを攻撃
            mc.gameMode.attack(mc.player, ((EntityHitResult) result).getEntity());
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // ブロック破壊開始
            BlockHitResult blockHit = (BlockHitResult) result;
            mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void handleRightClick(Minecraft mc) {
        if (mc.level == null || mc.player == null)
            return;

        // カスタムリーチ距離を使用（カリング距離と同期）
        double reach = MouseRaycast.getCustomReachDistance();
        net.minecraft.world.phys.HitResult result = MouseRaycast.getHitResult(mc, 1.0f, reach);

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            // エンティティにインタラクト
            mc.gameMode.interact(mc.player, ((EntityHitResult) result).getEntity(), InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // ブロックにインタラクト（設置など）
            BlockHitResult blockHit = (BlockHitResult) result;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            // アイテム使用（弓など）
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    private static void handleBlockMining(Minecraft mc) {
        // 継続的なブロック破壊
        net.minecraft.world.phys.HitResult result = MouseRaycast.getHitResult(mc, 1.0f,
                MouseRaycast.getCustomReachDistance());
        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
        }
    }
}
