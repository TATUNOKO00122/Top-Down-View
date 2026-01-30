package com.topdownview.spatial;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 空間の開口部データ。
 *
 * <p>壁や天井に空いた穴を表す。
 * 開口部を構成するブロック群、種類、サイズ、面方向を持つ。
 * 不変オブジェクト。
 */
public final class Opening {

    private final Set<BlockPos> blocks;
    private final OpeningType type;
    private final int size;
    private final Direction facing;

    public Opening(Set<BlockPos> blocks, OpeningType type, Direction facing) {
        this.blocks = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(blocks, "blocks")));
        this.type = Objects.requireNonNull(type, "type");
        this.facing = Objects.requireNonNull(facing, "facing");
        this.size = blocks.size();
    }

    public Set<BlockPos> getBlocks() {
        return blocks;
    }

    public OpeningType getType() {
        return type;
    }

    /** 開口部を構成するブロック数 */
    public int getSize() {
        return size;
    }

    /** 開口部がある面の方向（北/南/東/西/上/下） */
    public Direction getFacing() {
        return facing;
    }

    /** 開口部の代表位置（blocks 内の最小座標） */
    public BlockPos getRepresentativePos() {
        if (blocks.isEmpty()) {
            return BlockPos.ZERO;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
        }
        return new BlockPos(minX, minY, minZ);
    }

    @Override
    public String toString() {
        return "Opening{type=" + type + ", size=" + size + ", facing=" + facing
                + ", pos=" + getRepresentativePos() + "}";
    }
}
