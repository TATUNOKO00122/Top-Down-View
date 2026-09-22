package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * ドールハウス表示用の可視ビットマスクを構築する。
 *
 * <p>{@link RoomSegmentation} のプレイヤーの部屋の空気セルと、その6近傍にある壁殻セル
 * （1ブロック厚のシェル）をプレイヤー起点の固定ボリュームへラスタライズし、GPU へ渡す
 * 1byte/セルの 2D テクスチャへ詰め込む。それ以外のブロックは「不可視」として黒く落とされる。
 */
public final class DollhouseMask {

    /** フラッドフィルの XZ 最大半径と一致させる。 */
    public static final int RADIUS_XZ = 24;
    /** フラッドフィルの Y 最大半径と一致させる。 */
    public static final int RADIUS_Y = 12;

    public static final int SIZE_X = RADIUS_XZ * 2 + 1;
    public static final int SIZE_Y = RADIUS_Y * 2 + 1;
    public static final int SIZE_Z = SIZE_X;

    /** 1セル1テクセルで詰め込む 2D テクスチャの寸法。 */
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 64;

    private DollhouseMask() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 構築結果（マスク本体とワールド座標のアンカー）。 */
    public static final class Snapshot {
        public final byte[] data;
        public final int anchorX;
        public final int anchorY;
        public final int anchorZ;

        Snapshot(byte[] data, int anchorX, int anchorY, int anchorZ) {
            this.data = data;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
        }
    }

    public static Snapshot build(RoomFloodFill.Result space, RoomSegmentation.Room room) {
        BlockPos seed = space.getSeed();
        int ax = seed.getX() - RADIUS_XZ;
        int ay = seed.getY() - RADIUS_Y;
        int az = seed.getZ() - RADIUS_XZ;
        byte[] data = new byte[WIDTH * HEIGHT];
        // プレイヤーの部屋の空気セルと、その6近傍にある壁殻セルだけを可視にする。
        // 空間全体の shell には他の部屋の壁も含まれるため、隣接するものだけを拾う。
        LongSet shell = space.getShellCells();
        LongIterator it = room.getAirCells().iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            mark(data, cell, ax, ay, az);
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            for (Direction dir : Direction.values()) {
                long neighbor = BlockPos.asLong(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                if (shell.contains(neighbor)) {
                    mark(data, neighbor, ax, ay, az);
                }
            }
        }
        return new Snapshot(data, ax, ay, az);
    }

    private static void mark(byte[] data, long cell, int ax, int ay, int az) {
        int lx = BlockPos.getX(cell) - ax;
        int ly = BlockPos.getY(cell) - ay;
        int lz = BlockPos.getZ(cell) - az;
        if (lx < 0 || lx >= SIZE_X || ly < 0 || ly >= SIZE_Y || lz < 0 || lz >= SIZE_Z) {
            return;
        }
        data[lx + lz * SIZE_X + ly * (SIZE_X * SIZE_Z)] = (byte) 0xFF;
    }
}
