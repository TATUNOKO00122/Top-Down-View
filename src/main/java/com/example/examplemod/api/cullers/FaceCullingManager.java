package com.example.examplemod.api.cullers;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 面カリング判定
 * カメラ方向を向いている面を非表示にする
 */
public class FaceCullingManager {

    private static final FaceCullingManager INSTANCE = new FaceCullingManager();
    private boolean enabled = false;

    public static FaceCullingManager getInstance() {
        return INSTANCE;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 面をカリングすべきか判定
     */
    public boolean shouldCullFace(BlockPos pos, Direction direction) {
        if (!enabled)
            return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null)
            return false;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 playerPos = mc.player.position();
        Vec3 blockCenter = Vec3.atCenterOf(pos);

        // プレイヤーより下のブロックはカリングしない（地面保護）
        if (blockCenter.y <= playerPos.y + 0.5) {
            return false;
        }

        // カメラからブロックへの方向
        Vec3 cameraToBlock = blockCenter.subtract(cameraPos).normalize();

        // プレイヤーからカメラへの方向（視線方向）
        Vec3 playerToCamera = cameraPos.subtract(playerPos).normalize();

        // ブロックがカメラ方向にあるかチェック
        Vec3 playerToBlock = blockCenter.subtract(playerPos).normalize();
        // プレイヤーから見てカメラ方向±60度以内のブロックのみ対象
        if (playerToCamera.dot(playerToBlock) < 0.5) {
            return false;
        }

        // 面の法線ベクトル
        Vec3 faceNormal = new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());

        // カメラに向いている面かチェック (内積 > 0 なら向いている)
        double dot = cameraToBlock.dot(faceNormal);

        // 面がカメラ方向を向いている場合のみカリング
        switch (direction) {
            case DOWN:
                // 下面: カメラが上にある時、カメラに向いていればカリング
                return cameraPos.y > blockCenter.y && dot > 0.3;

            case UP:
                // 上面: カメラより上にあるブロックの上面はカリング
                return blockCenter.y > cameraPos.y;

            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                // 側面: カメラに向いていて、プレイヤーより上ならカリング
                return dot > 0.4;

            default:
                return false;
        }
    }

    // 互換性のための残骸メソッド（エラー回避）
    public void clear() {
    }

    public void updateFromBlockSet(java.util.Set<BlockPos> blocks) {
    }

    public boolean shouldIgnoreBlockPick(BlockPos pos) {
        return false;
    }
}
