package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link RoomFloodFill} の閉空間を「階 (storey)」と「部屋 (room)」に分割する。
 *
 * <p>階の判定: 同じ列で上下を空気に挟まれた壁殻セル（水平スラブ = 天井/床）を分離層とみなし、
 * 各空気セルより下にある分離層の数を階インデックスとする。階段の開口部では列に分離層が無いため
 * 上下が同じ階に属し、スラブ上へ登ったセルだけが上の階に振り分けられる。
 *
 * <p>部屋の判定: 水平 4 方向のうち対向する 2 方向が空気でないセルを「狭窄セル (ドア/通路)」として
 * 除外し、残った広いセルの 6 連結成分を部屋とする。除外した狭窄セルは隣接する部屋へ再割り当てする。
 * これにより開放的な大部屋は 1 部屋、1〜2 ブロック幅のドアで仕切られた区画は別部屋になる。
 *
 * <p>スレッドセーフではありません。単一スレッドから呼び出してください。
 */
public final class RoomSegmentation {

    private RoomSegmentation() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static final Direction[] ALL6 = Direction.values();

    /** 分割された 1 部屋。 */
    public static final class Room {
        private final LongSet airCells;
        private final int storey;
        private final BlockPos minPos;
        private final BlockPos maxPos;

        Room(LongSet airCells, int storey, BlockPos minPos, BlockPos maxPos) {
            this.airCells = airCells;
            this.storey = storey;
            this.minPos = minPos;
            this.maxPos = maxPos;
        }

        /** 部屋内部の空気セル集合 (packed long)。 */
        public LongSet getAirCells() {
            return airCells;
        }

        /** 所属する階インデックス (0 = 最下層)。 */
        public int getStorey() {
            return storey;
        }

        /** 部屋のバウンディングボックス最小座標。 */
        public BlockPos getMinPos() {
            return minPos;
        }

        /** 部屋のバウンディングボックス最大座標。 */
        public BlockPos getMaxPos() {
            return maxPos;
        }

        /** 空気セル数。 */
        public int size() {
            return airCells.size();
        }
    }

    /** 分割結果。 */
    public static final class Result {

        public static final Result EMPTY = new Result(List.of(), -1, 0);

        private final List<Room> rooms;
        private final int playerRoomIndex;
        private final int storeyCount;

        Result(List<Room> rooms, int playerRoomIndex, int storeyCount) {
            this.rooms = List.copyOf(rooms);
            this.playerRoomIndex = playerRoomIndex;
            this.storeyCount = storeyCount;
        }

        /** 分割された全部屋 (空気セル数の降順)。 */
        public List<Room> getRooms() {
            return rooms;
        }

        /** プレイヤーがいる部屋のインデックス。見つからない場合は -1。 */
        public int getPlayerRoomIndex() {
            return playerRoomIndex;
        }

        /** プレイヤーがいる部屋。見つからない場合は {@code null}。 */
        public Room getPlayerRoom() {
            return (playerRoomIndex >= 0 && playerRoomIndex < rooms.size())
                    ? rooms.get(playerRoomIndex) : null;
        }

        /** 検出した階数。 */
        public int getStoreyCount() {
            return storeyCount;
        }

        /** 有効な分割結果か。 */
        public boolean isValid() {
            return !rooms.isEmpty();
        }
    }

    /**
     * 閉空間を階・部屋へ分割する。
     *
     * @param room {@link RoomFloodFill} の結果。{@code null}/非閉空間なら {@link Result#EMPTY}。
     * @param seed プレイヤー位置 (所属部屋の特定に使用)。{@code null} なら {@code room.getSeed()}。
     * @return 分割結果。
     */
    public static Result analyze(RoomFloodFill.Result room, BlockPos seed) {
        if (room == null || !room.isEnclosed()) {
            return Result.EMPTY;
        }
        final LongSet air = room.getAirCells();
        final LongSet shell = room.getShellCells();
        if (air.isEmpty()) {
            return Result.EMPTY;
        }
        final BlockPos roomSeed = room.getSeed();
        final int yLo = roomSeed.getY() - RoomFloodFill.MAX_RADIUS_Y;

        // ==================== 階: 上下を空気に挟まれた壁殻セルを列ごとの分離層 bitmask にする ====================
        final Long2LongOpenHashMap sepMaskByColumn = new Long2LongOpenHashMap();
        for (long cell : shell) {
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            if (air.contains(BlockPos.asLong(x, y - 1, z)) && air.contains(BlockPos.asLong(x, y + 1, z))) {
                int idx = y - yLo;
                if (idx >= 0 && idx < Long.SIZE) {
                    long column = BlockPos.asLong(x, 0, z);
                    sepMaskByColumn.put(column, sepMaskByColumn.get(column) | (1L << idx));
                }
            }
        }

        final Long2IntOpenHashMap storeyOf = new Long2IntOpenHashMap(air.size());
        storeyOf.defaultReturnValue(0);
        int maxStorey = 0;
        for (long cell : air) {
            int y = BlockPos.getY(cell);
            int idx = y - yLo;
            int storey = 0;
            if (idx > 0) {
                long sep = sepMaskByColumn.get(BlockPos.asLong(BlockPos.getX(cell), 0, BlockPos.getZ(cell)));
                if (sep != 0L) {
                    storey = Long.bitCount(sep & ((1L << idx) - 1L));
                }
            }
            storeyOf.put(cell, storey);
            if (storey > maxStorey) {
                maxStorey = storey;
            }
        }

        // ==================== 部屋: 狭窄セルを除いた広いセルで連結成分を作る ====================
        final LongOpenHashSet chokepoints = new LongOpenHashSet();
        for (long cell : air) {
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            boolean northSouth = !air.contains(BlockPos.asLong(x, y, z - 1))
                    && !air.contains(BlockPos.asLong(x, y, z + 1));
            boolean eastWest = !air.contains(BlockPos.asLong(x - 1, y, z))
                    && !air.contains(BlockPos.asLong(x + 1, y, z));
            if (northSouth || eastWest) {
                chokepoints.add(cell);
            }
        }

        LongOpenHashSet wide = new LongOpenHashSet(air.size());
        for (long cell : air) {
            if (!chokepoints.contains(cell)) {
                wide.add(cell);
            }
        }
        if (wide.isEmpty()) {
            // 全て狭窄 (1 幅の空間など) — 分割せず階ごとに 1 部屋ずつまとめる
            return buildStoreyRooms(air, storeyOf, maxStorey, roomSeed, seed);
        }

        final Long2IntOpenHashMap componentOf = new Long2IntOpenHashMap(air.size());
        componentOf.defaultReturnValue(-1);
        final LongArrayList queue = new LongArrayList();
        int componentCount = 0;

        for (long start : wide) {
            if (componentOf.containsKey(start)) {
                continue;
            }
            int component = componentCount++;
            componentOf.put(start, component);
            queue.clear();
            queue.add(start);
            int head = 0;
            int startStorey = storeyOf.get(start);
            while (head < queue.size()) {
                long cur = queue.getLong(head++);
                int cx = BlockPos.getX(cur);
                int cy = BlockPos.getY(cur);
                int cz = BlockPos.getZ(cur);
                for (Direction dir : ALL6) {
                    long nb = BlockPos.asLong(
                            cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                    if (!wide.contains(nb) || componentOf.containsKey(nb)) {
                        continue;
                    }
                    if (storeyOf.get(nb) != startStorey) {
                        continue;
                    }
                    componentOf.put(nb, component);
                    queue.add(nb);
                }
            }
        }

        // 狭窄セルを隣接する広いセルの成分へ BFS で取り込む (同階のみ)
        queue.clear();
        for (long cell : wide) {
            queue.add(cell);
        }
        int head = 0;
        while (head < queue.size()) {
            long cur = queue.getLong(head++);
            int component = componentOf.get(cur);
            int curStorey = storeyOf.get(cur);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : ALL6) {
                long nb = BlockPos.asLong(
                        cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                if (!chokepoints.contains(nb) || componentOf.containsKey(nb)) {
                    continue;
                }
                if (storeyOf.get(nb) != curStorey) {
                    continue;
                }
                componentOf.put(nb, component);
                queue.add(nb);
            }
        }

        // どの部屋にも取り込まれなかった孤立狭窄セルは単独部屋にする
        for (long cell : chokepoints) {
            if (!componentOf.containsKey(cell)) {
                componentOf.put(cell, componentCount++);
            }
        }

        return buildRooms(air, componentOf, storeyOf, componentCount, roomSeed, seed);
    }

    /** 全空気セルを階ごとの 1 部屋にまとめるフォールバック。 */
    private static Result buildStoreyRooms(LongSet air, Long2IntOpenHashMap storeyOf,
            int maxStorey, BlockPos roomSeed, BlockPos seed) {
        Long2ObjectOpenHashMap<LongOpenHashSet> byStorey = new Long2ObjectOpenHashMap<>();
        for (long cell : air) {
            int storey = storeyOf.get(cell);
            byStorey.computeIfAbsent(storey, k -> new LongOpenHashSet()).add(cell);
        }
        List<Room> rooms = new ArrayList<>(byStorey.size());
        for (var entry : byStorey.long2ObjectEntrySet()) {
            rooms.add(new Room(entry.getValue(), (int) entry.getLongKey(),
                    boundsMin(entry.getValue()), boundsMax(entry.getValue())));
        }
        rooms.sort(Comparator.comparingInt(Room::size).reversed());
        int playerRoomIndex = indexOfRoomContaining(rooms, seed != null ? seed : roomSeed);
        return new Result(rooms, playerRoomIndex, maxStorey + 1);
    }

    private static Result buildRooms(LongSet air, Long2IntOpenHashMap componentOf,
            Long2IntOpenHashMap storeyOf, int componentCount, BlockPos roomSeed, BlockPos seed) {
        Long2ObjectOpenHashMap<LongOpenHashSet> cellsByComponent = new Long2ObjectOpenHashMap<>(componentCount);
        for (long cell : air) {
            int component = componentOf.get(cell);
            cellsByComponent.computeIfAbsent(component, k -> new LongOpenHashSet()).add(cell);
        }

        List<Room> rooms = new ArrayList<>(componentCount);
        for (var entry : cellsByComponent.long2ObjectEntrySet()) {
            LongOpenHashSet cells = entry.getValue();
            int storey = storeyOf.get(cells.iterator().nextLong());
            rooms.add(new Room(cells, storey, boundsMin(cells), boundsMax(cells)));
        }
        rooms.sort(Comparator.comparingInt(Room::size).reversed());
        int playerRoomIndex = indexOfRoomContaining(rooms, seed != null ? seed : roomSeed);
        int storeyCount = 0;
        for (Room room : rooms) {
            if (room.getStorey() + 1 > storeyCount) {
                storeyCount = room.getStorey() + 1;
            }
        }
        return new Result(rooms, playerRoomIndex, storeyCount);
    }

    private static int indexOfRoomContaining(List<Room> rooms, BlockPos pos) {
        if (pos == null) {
            return -1;
        }
        long target = pos.asLong();
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getAirCells().contains(target)) {
                return i;
            }
        }
        return -1;
    }

    private static BlockPos boundsMin(LongSet cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (long c : cells) {
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static BlockPos boundsMax(LongSet cells) {
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return new BlockPos(maxX, maxY, maxZ);
    }
}
