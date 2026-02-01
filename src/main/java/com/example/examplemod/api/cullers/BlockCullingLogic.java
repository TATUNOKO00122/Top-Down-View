package com.example.examplemod.api.cullers;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * ブロックカリングロジック
 * プレイヤーとカメラの間にあるブロックをカリングする
 */
public class BlockCullingLogic {

    /**
     * ブロックをカリングすべきか判定
     * プレイヤーより上、かつカメラ方向にあるブロックをカリング
     */
    public static boolean shouldCull(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            return false;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 playerPos = mc.player.position();
        Vec3 blockCenter = Vec3.atCenterOf(pos);

        // プレイヤーより下のブロックはカリングしない（地面保護）
        if (blockCenter.y <= playerPos.y + 0.1) {
            return false;
        }

        // カメラより上のブロックはカリングしない
        if (blockCenter.y > cameraPos.y) {
            return false;
        }

        // プレイヤーからカメラへの方向
        Vec3 playerToCamera = cameraPos.subtract(playerPos).normalize();

        // プレイヤーからブロックへの方向
        Vec3 playerToBlock = blockCenter.subtract(playerPos).normalize();

        // ブロックがカメラ方向にあるかチェック（内積が正なら同じ方向）
        double dot = playerToCamera.dot(playerToBlock);

        // カメラ方向±45度以内のブロックをカリング対象
        if (dot < 0.7) {
            return false;
        }

        // 水平距離によるフィルタリング
        double horizontalDistSq = (blockCenter.x - playerPos.x) * (blockCenter.x - playerPos.x)
                + (blockCenter.z - playerPos.z) * (blockCenter.z - playerPos.z);

        // プレイヤーからカメラまでの水平距離の範囲内のみカリング
        double cameraHorizontalDistSq = (cameraPos.x - playerPos.x) * (cameraPos.x - playerPos.x)
                + (cameraPos.z - playerPos.z) * (cameraPos.z - playerPos.z);

        // カメラまでの距離 + マージン以内
        if (horizontalDistSq > cameraHorizontalDistSq + 25) { // 5ブロックマージン
            return false;
        }

        return true;
    }
}
