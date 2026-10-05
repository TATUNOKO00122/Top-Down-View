package com.topdownview.culling.geometry;

import net.minecraft.core.BlockPos;

/**
 * 逆ピラミッド保護領域の計算を行うユーティリティクラス。
 * プレイヤー足元から前方へ広がる保護領域の保護係数(0.0-1.0)を計算する。
 * 
 * <p>保護領域はプレイヤー位置を中心とする逆ピラミッド形状:
 * <ul>
 *   <li>プレイヤー足元は完全保護(1.0)</li>
 *   <li>水平距離が離れるほど保護高さが増加</li>
 *   <li>前方(camera→player方向)のみ保護</li>
 * </ul>
 */
public final class PyramidProtectionCalc {

    private static final double MAX_PROTECTION_HEIGHT = 3.0;
    private static final double FADE_BOUNDARY_THICKNESS = 1.5;
    private static final double MIN_DIR_LENGTH_SQ = 1.0E-16;

    private PyramidProtectionCalc() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 指定ブロックの保護係数を計算する。
     *
     * @param pos 判定対象のブロック位置
     * @param playerX プレイヤーX座標
     * @param playerY プレイヤーY座標
     * @param playerZ プレイヤーZ座標
     * @param cameraX カメラX座標
     * @param cameraZ カメラZ座標
     * @return 保護係数 (0.0=保護なし, 1.0=完全保護)
     */
    public static double calculateProtectionFactor(BlockPos pos,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraZ) {
        double diff = calculateBoundaryDiff(pos, playerX, playerY, playerZ, cameraX, cameraZ);
        if (diff >= 0.0) {
            return 1.0;
        }
        if (diff >= -FADE_BOUNDARY_THICKNESS) {
            double t = (FADE_BOUNDARY_THICKNESS + diff) / FADE_BOUNDARY_THICKNESS;
            return t;
        }
        return 0.0;
    }

    /**
     * ピラミッド保護境界とブロックとの距離(diff)を返す。
     *
     * <p>diff ≥ 0 は完全保護側、diff &lt; 0 はカリング側。前方(dot &lt; 0)のブロックは
     * ピラミッド対象外のため大きな負値を返す。境界は {@code floor(playerY) + sqrt(dx²+dz²)}
     * の傾斜で、プレイヤーの歩行(±1ブロック)で近接リングの diff が ±0 を跨ぐため、
     * 単純な 0 枚のしきい値では境界ブロックがカリング/保護を往復する。この diff を
     * ヒステリシス帳簿(採用 0 / 解放 −1.0)に渡すのが {@code TopDownCuller} 側の役割。
     *
     * @return ピラミッド境界との差(ブロック単位)。対象外なら -1000.0。
     */
    public static double calculateBoundaryDiff(BlockPos pos,
            double playerX, double playerY, double playerZ, double cameraX, double cameraZ) {
        double dirX = playerX - cameraX;
        double dirZ = playerZ - cameraZ;
        double blockToPlayerX = pos.getX() + 0.5 - playerX;
        double blockToPlayerZ = pos.getZ() + 0.5 - playerZ;

        double dirLengthSq = dirX * dirX + dirZ * dirZ;
        if (dirLengthSq > MIN_DIR_LENGTH_SQ) {
            double invDirLength = 1.0 / Math.sqrt(dirLengthSq);
            double dot = blockToPlayerX * (dirX * invDirLength)
                    + blockToPlayerZ * (dirZ * invDirLength);
            if (dot < 0.0) {
                return -1000.0;
            }
        }

        double distXZ = Math.sqrt(blockToPlayerX * blockToPlayerX + blockToPlayerZ * blockToPlayerZ);
        double protectedHeight = Math.min(distXZ, MAX_PROTECTION_HEIGHT);

        double groundY = Math.floor(playerY) - 1.0;
        double targetY = groundY + protectedHeight;

        return targetY - (pos.getY() + 0.5);
    }
}