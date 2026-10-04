package com.topdownview.culling.geometry;

import com.topdownview.util.RayAabb;
import net.minecraft.core.BlockPos;

/**
 * ブロックがカメラからプレイヤーへの視線を遮るか判定するユーティリティ。
 */
public final class OcclusionCalculator {

    private static final double MIN_HORIZONTAL_LENGTH_SQ = 1.0E-8;

    // チャンク構築ワーカーからも呼ばれるため、スレッドごとに交差計算の作業配列を再利用する。
    private static final ThreadLocal<double[]> RAY_SCRATCH = ThreadLocal.withInitial(() -> new double[2]);

    private OcclusionCalculator() {}

    /**
     * ブロックがプレイヤーを原点とする「カメラ側の視界コーン(楔)」の内側かを、
     * 水平面の角度で判定する。角度制限ありの旧方式。
     *
     * <p>プレイヤーからブロックへの水平ベクトルと、視線軸方向(カメラ→プレイヤーの
     * 単位ベクトル)のなす角が半角以内で、かつブロックがカメラ側(軸の手前側)にある場合に
     * true を返す。真上・真下付近(水平長さ≈0)はカリング寄りとして true を返す。
     *
     * @param dirX 視線軸方向X(単位ベクトル、カメラ→プレイヤー)
     * @param dirZ 視線軸方向Z(単位ベクトル、カメラ→プレイヤー)
     * @param cosHalfAngle 半角のコサイン
     * @return true = コーン内(カリング対象) / false = コーン外(保護対象)
     */
    public static boolean isWithinViewWedge(double bX, double bZ,
            double pX, double pZ, double dirX, double dirZ, double cosHalfAngle) {
        double toBX = bX - pX;
        double toBZ = bZ - pZ;
        double lenSq = toBX * toBX + toBZ * toBZ;
        if (lenSq < MIN_HORIZONTAL_LENGTH_SQ) {
            return true;
        }
        // cos <= -cosHalfAngle を sqrt 無しで判定する。cosHalfAngle >= 0 なので dot>0 は常に外側。
        double dot = toBX * dirX + toBZ * dirZ;
        if (dot > 0.0) {
            return false;
        }
        return dot * dot >= cosHalfAngle * cosHalfAngle * lenSq;
    }

    /**
     * ブロックがプレイヤーより奥（水平方向）にあるかを判定する。
     *
     * <p>プレイヤーからブロックへの水平ベクトルと、視線軸方向(カメラ→プレイヤーの単位ベクトル)の
     * 内積が正なら、ブロックはプレイヤーを越えてカメラから遠ざかる側にある。角度制限は設けず、
     * 円柱内のカメラ側の半空間を丸ごとカリング対象にすることで左右の視野を確保する。
     *
     * @param dirX 視線軸方向X(単位ベクトル、カメラ→プレイヤー)
     * @param dirZ 視線軸方向Z(単位ベクトル、カメラ→プレイヤー)
     * @return true = プレイヤーより奥(保護対象) / false = カメラ側(カリング候補)
     */
    public static boolean isBeyondPlayerHorizontally(double bX, double bZ,
            double pX, double pZ, double dirX, double dirZ) {
        double toBX = bX - pX;
        double toBZ = bZ - pZ;
        return toBX * dirX + toBZ * dirZ > 0.0;
    }

    public static boolean isOccludingView(BlockPos pos, double cX, double cY, double cZ, double pX, double pY, double pZ) {
        double minX = pos.getX() - 0.5;
        double minY = pos.getY() - 0.5;
        double minZ = pos.getZ() - 0.5;
        double maxX = pos.getX() + 1.5;
        double maxY = pos.getY() + 1.5;
        double maxZ = pos.getZ() + 1.5;

        double dirX = pX - cX;
        double dirY = pY - cY;
        double dirZ = pZ - cZ;
        double rayLengthSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (rayLengthSq < 1.0E-12) {
            return false;
        }

        double[] t = RAY_SCRATCH.get();
        t[0] = 0.0;
        t[1] = 1.0;
        if (!RayAabb.clip(cX, dirX, minX, maxX, t)) return false;
        if (!RayAabb.clip(cY, dirY, minY, maxY, t)) return false;
        if (!RayAabb.clip(cZ, dirZ, minZ, maxZ, t)) return false;

        return t[1] < 0.999;
    }
}
