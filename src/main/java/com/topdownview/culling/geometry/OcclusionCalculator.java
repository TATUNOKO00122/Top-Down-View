package com.topdownview.culling.geometry;

import net.minecraft.core.BlockPos;

/**
 * ブロックがカメラからプレイヤーへの視線を遮るか判定するユーティリティ。
 */
public final class OcclusionCalculator {

    private static final double MIN_HORIZONTAL_LENGTH_SQ = 1.0E-8;
    private static final double MIN_CONE_LENGTH_SQ = 1.0E-6;

    private OcclusionCalculator() {}

    /**
     * ブロックがプレイヤーを原点とする「カメラ側の視界コーン(楔)」の内側かを、
     * 水平面の角度で判定する。
     *
     * <p>プレイヤーからブロックへの水平ベクトルと、視線軸方向(カメラ→プレイヤーの
     * 単位ベクトル)のなす角が半角以内で、かつブロックがカメラ側(軸の手前側)にある場合に
     * true を返す。つまり手前の壁だけがカリング対象となり、プレイヤーより奥の壁や
     * 真横の構造物は対象外(保護)になる。
     * 真上・真下付近(水平長さ≈0)はカリング寄りとして true を返す。
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
        double invLen = 1.0 / Math.sqrt(lenSq);
        double cos = (toBX * dirX + toBZ * dirZ) * invLen;
        return cos <= -cosHalfAngle;
    }

    /**
     * ブロックがカメラ→プレイヤーの視線上に張る「前景コーン(3D)」の内側かを判定する。
     *
     * <p>カメラ側の開口角とプレイヤー側の半角の両方を満たすブロックだけを対象にするため、
     * カメラとプレイヤーの間にある手前の壁・天井だけが消え、プレイヤーより奥や真横の構造物は
     * 残る。dungeons_iso の 2 角度判定と同じ考え方。
     *
     * @param cosCameraHalfAngle カメラ側の半角のコサイン(視野の開口)
     * @param cosPlayerHalfAngle プレイヤー側の半角のコサイン
     * @return true = コーン内(カリング対象) / false = コーン外
     */
    public static boolean isWithinForegroundCone(
            double bX, double bY, double bZ,
            double cX, double cY, double cZ,
            double pX, double pY, double pZ,
            double cosCameraHalfAngle, double cosPlayerHalfAngle) {
        double cpX = pX - cX, cpY = pY - cY, cpZ = pZ - cZ;
        double cbX = bX - cX, cbY = bY - cY, cbZ = bZ - cZ;
        double cpLenSq = cpX * cpX + cpY * cpY + cpZ * cpZ;
        double cbLenSq = cbX * cbX + cbY * cbY + cbZ * cbZ;
        if (cbLenSq < MIN_CONE_LENGTH_SQ) {
            return true; // カメラがブロック内: 視界を遮るため消す
        }
        if (cpLenSq < MIN_CONE_LENGTH_SQ) {
            return false; // カメラとプレイヤーが同一位置(退化)
        }
        double cosTheta = (cpX * cbX + cpY * cbY + cpZ * cbZ) / Math.sqrt(cpLenSq * cbLenSq);
        if (cosTheta < cosCameraHalfAngle) {
            return false;
        }

        double pbX = bX - pX, pbY = bY - pY, pbZ = bZ - pZ;
        double pcX = cX - pX, pcY = cY - pY, pcZ = cZ - pZ;
        double pbLenSq = pbX * pbX + pbY * pbY + pbZ * pbZ;
        if (pbLenSq < MIN_CONE_LENGTH_SQ) {
            return true; // プレイヤーがブロック内
        }
        double cosPhi = (pbX * pcX + pbY * pcY + pbZ * pcZ) / Math.sqrt(pbLenSq * cpLenSq);
        return cosPhi >= cosPlayerHalfAngle;
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

        double[] t = {0.0, 1.0};
        if (!slabIntersect(cX, dirX, minX, maxX, t)) return false;
        if (!slabIntersect(cY, dirY, minY, maxY, t)) return false;
        if (!slabIntersect(cZ, dirZ, minZ, maxZ, t)) return false;

        return t[1] < 0.999;
    }

    private static boolean slabIntersect(double origin, double dir, double min, double max, double[] t) {
        if (Math.abs(dir) < 1.0E-9) {
            return origin >= min && origin <= max;
        }
        double invDir = 1.0 / dir;
        double t1 = (min - origin) * invDir;
        double t2 = (max - origin) * invDir;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        t[0] = Math.max(t[0], t1);
        t[1] = Math.min(t[1], t2);
        return t[0] <= t[1];
    }
}
