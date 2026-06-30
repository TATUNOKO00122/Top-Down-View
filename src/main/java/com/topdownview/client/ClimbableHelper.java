package com.topdownview.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;

/**
 * ハシゴやツタなどの昇降可能（Climbable）なブロックでの移動制御を補助するユーティリティクラス。
 */
public final class ClimbableHelper {

    private static volatile Direction climbingDirection = null;

    private ClimbableHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 現在プレイヤーが登ろうとしている壁の方向を取得する。
     */
    public static Direction getClimbingDirection() {
        return climbingDirection;
    }

    /**
     * 現在プレイヤーが登ろうとしている壁の方向を設定する。
     */
    public static void setClimbingDirection(Direction dir) {
        climbingDirection = dir;
    }

    /**
     * プレイヤーがハシゴやツタを「登る（壁に向かって移動する）」べき状態にあるか判定し、
     * 登るべき壁の方向（Direction）を返す。
     * 登るべき状態でない場合は null を返す。
     *
     * @param mc Minecraftインスタンス
     * @param forward キーボードの前進入力値
     * @param strafe キーボードの横移動入力値
     * @return 登るべき壁の方向、または null
     */
    public static Direction calculateClimbingWallDirection(Minecraft mc, float forward, float strafe) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (!mc.player.onClimbable()) {
            return null;
        }

        // 移動入力がない場合は登る方向を判定しない
        if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) {
            return null;
        }

        BlockPos pos = mc.player.blockPosition();
        BlockState state = mc.level.getBlockState(pos);
        
        // 足元のブロックがClimbableでない場合、念のため目の高さも確認する（バニラの挙動に合わせる）
        if (!state.is(BlockTags.CLIMBABLE)) {
            pos = pos.above();
            state = mc.level.getBlockState(pos);
            if (!state.is(BlockTags.CLIMBABLE)) {
                return null;
            }
        }

        Direction wallDir = null;

        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.getValue(LadderBlock.FACING);
            wallDir = facing.getOpposite();
        } else if (state.getBlock() instanceof VineBlock) {
            // ツタの場合、貼り付いている壁の方向のうち、プレイヤーの移動方向に一番近いものを選択する
            double maxDot = -1.0;
            Vec3 movementVec = getMovementDirectionVector(mc, forward, strafe);
            if (movementVec == null) {
                return null;
            }

            for (Direction dir : Direction.values()) {
                if (dir.getAxis().isHorizontal()) {
                    BooleanProperty prop = VineBlock.PROPERTY_BY_DIRECTION.get(dir);
                    if (prop != null && state.getValue(prop)) {
                        Vec3 wallVec = Vec3.atLowerCornerOf(dir.getNormal());
                        double dot = movementVec.dot(wallVec);
                        if (dot > maxDot) {
                            maxDot = dot;
                            wallDir = dir;
                        }
                    }
                }
            }
        }

        if (wallDir == null) {
            return null;
        }

        // プレイヤーの移動方向が、壁の方向を向いているか判定（角度差が 90度未満、すなわち内積が正）
        Vec3 movementVec = getMovementDirectionVector(mc, forward, strafe);
        if (movementVec == null) {
            return null;
        }
        Vec3 wallVec = Vec3.atLowerCornerOf(wallDir.getNormal());
        
        if (movementVec.dot(wallVec) > 0.01) {
            return wallDir;
        }

        return null;
    }

    /**
     * カメラ相対の移動入力から、世界座標系での水平移動方向ベクトルを取得する。
     */
    private static Vec3 getMovementDirectionVector(Minecraft mc, float forward, float strafe) {
        float cameraYaw = com.topdownview.state.ModState.CAMERA.getYaw();
        // W: forward=1, A: strafe=1, S: forward=-1, D: strafe=-1
        // MinecraftのInputでは、leftImpulse(strafe)は左が正なので、
        // 右に移動するときは strafe < 0 になる。
        // atan2(-strafe, forward) で、右に進むときは -strafe = 正 となり、右方向(90度)を指す。
        double inputAngleRad = Math.atan2(-strafe, forward);
        double worldAngleRad = Math.toRadians(cameraYaw) + inputAngleRad;
        
        // 世界座標系での方向ベクトル (X, Z)
        double dx = -Math.sin(worldAngleRad);
        double dz = Math.cos(worldAngleRad);
        
        return new Vec3(dx, 0.0, dz).normalize();
    }
}
