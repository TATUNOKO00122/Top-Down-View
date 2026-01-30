package com.topdownview.spatial;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * 認識された空間領域データ。
 *
 * <p>BFS探索で集めた空気ブロック集合・壁ブロック集合・開口部リスト・
 * 包含境界ボックス・空間タイプ・シード位置を持つ。
 * 不変オブジェクト。
 */
public final class SpaceRegion {

    private final Set<BlockPos> airBlocks;
    private final Set<BlockPos> wallBlocks;
    private final Set<Opening> openings;
    private final BlockPos seed;
    private final SpaceType type;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public SpaceRegion(Set<BlockPos> airBlocks,
                       Set<BlockPos> wallBlocks,
                       Set<Opening> openings,
                       BlockPos seed,
                       SpaceType type) {
        this.airBlocks = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(airBlocks, "airBlocks")));
        this.wallBlocks = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(wallBlocks, "wallBlocks")));
        this.openings = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(openings, "openings")));
        this.seed = Objects.requireNonNull(seed, "seed").immutable();
        this.type = Objects.requireNonNull(type, "type");

        if (airBlocks.isEmpty()) {
            this.minX = this.minY = this.minZ = 0;
            this.maxX = this.maxY = this.maxZ = -1;
        } else {
            int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, lz = Integer.MAX_VALUE;
            int hx = Integer.MIN_VALUE, hy = Integer.MIN_VALUE, hz = Integer.MIN_VALUE;
            for (BlockPos p : airBlocks) {
                int x = p.getX(), y = p.getY(), z = p.getZ();
                if (x < lx) lx = x;
                if (y < ly) ly = y;
                if (z < lz) lz = z;
                if (x > hx) hx = x;
                if (y > hy) hy = y;
                if (z > hz) hz = z;
            }
            this.minX = lx; this.minY = ly; this.minZ = lz;
            this.maxX = hx; this.maxY = hy; this.maxZ = hz;
        }
    }

    public Set<BlockPos> getAirBlocks() {
        return airBlocks;
    }

    public Set<BlockPos> getWallBlocks() {
        return wallBlocks;
    }

    public Set<Opening> getOpenings() {
        return openings;
    }

    public BlockPos getSeed() {
        return seed;
    }

    public SpaceType getType() {
        return type;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    /**
     * type だけ差し替えた新しい SpaceRegion を返す。
     *
     * <p>内部の Set は不変（unmodifiable）のため、このインスタンスと共有して
     * コピーを省略できる。SpaceAnalyzer が UNKNOWN → 確定タイプに遷移する際に
     * 全 Set の再コピーを回避するために使用。
     */
    public SpaceRegion withType(SpaceType newType) {
        if (this.type == newType) return this;
        return new SpaceRegion(airBlocks, wallBlocks, openings, seed, newType,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 既存の不変 Set と境界ボックスをそのまま共有する軽量コンストラクタ */
    private SpaceRegion(Set<BlockPos> airBlocks, Set<BlockPos> wallBlocks, Set<Opening> openings,
                        BlockPos seed, SpaceType type,
                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.airBlocks = airBlocks;
        this.wallBlocks = wallBlocks;
        this.openings = openings;
        this.seed = seed;
        this.type = type;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public int getAirBlockCount() {
        return airBlocks.size();
    }

    public int getWallBlockCount() {
        return wallBlocks.size();
    }

    /** 境界ボックスの体積（air + 壁込みの外枠） */
    public int getBoundsVolume() {
        if (airBlocks.isEmpty()) return 0;
        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;
        return dx * dy * dz;
    }

    /** 空間が有効か（空気ブロックが1個以上あるか） */
    public boolean isValid() {
        return !airBlocks.isEmpty();
    }

    @Override
    public String toString() {
        return "SpaceRegion{type=" + type + ", air=" + airBlocks.size()
                + ", walls=" + wallBlocks.size() + ", openings=" + openings.size()
                + ", bounds=[" + minX + "," + minY + "," + minZ
                + "]->[" + maxX + "," + maxY + "," + maxZ + "]}";
    }
}
