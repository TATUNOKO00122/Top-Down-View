package com.topdownview.culling.geometry;

import net.minecraft.core.BlockPos;

/**
 * 変更されたブロック位置のバウンディングボックスを蓄積する可変ボックス。
 *
 * <p>要素カリング集合(壁パネル/天井スライス)の差分セルだけをチャンク再構築の対象にするために使う。
 * 再構築を消費するまで差分を union し続ける。呼び出しはティック/描画スレッド単一を前提とする。
 */
public final class BlockChangeBox {

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private boolean empty = true;

    public void reset() {
        empty = true;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void include(int x, int y, int z) {
        if (empty) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
            empty = false;
            return;
        }
        if (x < minX) {
            minX = x;
        } else if (x > maxX) {
            maxX = x;
        }
        if (y < minY) {
            minY = y;
        } else if (y > maxY) {
            maxY = y;
        }
        if (z < minZ) {
            minZ = z;
        } else if (z > maxZ) {
            maxZ = z;
        }
    }

    public void includeCell(long packedPos) {
        include(BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos));
    }

    /** 他のボックスの範囲を取り込む(両端のセルを含めるだけで十分)。 */
    public void includeBox(BlockChangeBox other) {
        if (other.empty) {
            return;
        }
        include(other.minX, other.minY, other.minZ);
        include(other.maxX, other.maxY, other.maxZ);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }
}
