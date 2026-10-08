package com.topdownview.spatial;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
 * <p>階の判定: 下が空間の空気である幅広い壁殻セル（スラブの下面）を起点に、連続する固体層の
 * 上面 Y を求め、Y ごとに集計して複数列に現れる Y を床レベルとする。各空気セルの階インデックスは、
 * その下にある床レベルの数。厚い床や厚い橋デッキでも1つのレベルになり、階段の開口部はその列に
 * 床が無いだけなので、周囲の列のスラブから床レベルが検出され、開口を挟んだ上階側の空気も
 * 正しい階に割り当てられる。
 * 下面基準なので、上に覆いが無い開けた橋のデッキも床レベルになり、橋の下が別階に分かれて
 * デッキが天井と誤認されない。
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
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** 床レベルとみなす最小列数。浮いた小さなスラブ (平台/梁) を床と誤認しないための閾値。 */
    private static final int MIN_FLOOR_COLUMNS = 2;

    /** スラブとみなす固体層の最大厚。これより厚い塊(壁・柱)は床レベルにしない。 */
    private static final int MAX_SLAB_THICKNESS = 8;

    /** 部屋とみなす最小クリアランス高さ（ブロック）。プレイヤーの全身（2ブロック）を収めるために必須。 */
    public static final int MIN_ROOM_HEIGHT = 2;

    /** 独立した階 (storey) とみなす最小床レベル間隔（ブロック）。天井高2ブロック＋床厚1ブロック。 */
    private static final int MIN_STOREY_CLEARANCE = 3;

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

        public static final Result EMPTY = new Result(List.of(), -1, 0, new Long2IntOpenHashMap());

        private final List<Room> rooms;
        private final int playerRoomIndex;
        private final int storeyCount;
        private final Long2IntOpenHashMap storeyOf;

        Result(List<Room> rooms, int playerRoomIndex, int storeyCount, Long2IntOpenHashMap storeyOf) {
            this.rooms = List.copyOf(rooms);
            this.playerRoomIndex = playerRoomIndex;
            this.storeyCount = storeyCount;
            this.storeyOf = storeyOf;
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
     * @param room     {@link RoomFloodFill} の結果。{@code null}/非閉空間なら {@link Result#EMPTY}。
     * @param seed     プレイヤー位置 (所属部屋の特定に使用)。{@code null} なら {@code room.getSeed()}。
     * @param blockMap プローブのブロック判定キャッシュ。スラブ下面から連続する固体層の上面を
     *                 求めるために使う（厚い床・厚い橋デッキも床レベルとして検出する）。
     *                 {@code null} なら下面セルの Y をそのまま床レベルにする。
     * @return 分割結果。
     */
    public static Result analyze(RoomFloodFill.Result room, BlockPos seed, BlockMap blockMap) {
        if (room == null || !room.isEnclosed()) {
            return Result.EMPTY;
        }
        final LongSet air = room.getAirCells();
        final LongSet shell = room.getShellCells();
        if (air.isEmpty()) {
            return Result.EMPTY;
        }
        final BlockPos roomSeed = room.getSeed();

        // ==================== 階: 空間全体の床レベルを検出する ====================
        // 「下が空間の空気」で「幅広い」スラブ下面をスラブとみなし、その固体層の上面 Y を Y ごとに
        // 数えて、複数列に現れる Y を床レベルとする。厚い床・厚い橋デッキでも1つのレベルになり、
        // 階段の開口部はその列に床が無いだけで周囲の列にはあるため上階側も正しい階に割り当てられる。
        // 上面 Y を記録するのは、梁と天井が連続する場合でも同じ Y にまとまり、下の部屋を分割しないため。
        final Long2IntOpenHashMap sepCountByY = new Long2IntOpenHashMap();
        for (long cell : shell) {
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            if (!air.contains(BlockPos.asLong(x, y - 1, z)) || !isWideSlab(shell, x, y, z)) {
                continue;
            }
            if (blockMap == null) {
                sepCountByY.addTo(y, 1);
                continue;
            }
            // 連続する固体層の上面まで登る。厚すぎる塊(壁など)はスラブではないので除外する。
            int top = y;
            int limit = y + MAX_SLAB_THICKNESS;
            while (top < limit && blockMap.isSolid(x, top + 1, z)) {
                top++;
            }
            if (!blockMap.isSolid(x, top + 1, z)) {
                sepCountByY.addTo(top, 1);
            }
        }
        final LongArrayList floorLevels = new LongArrayList();
        for (var entry : sepCountByY.long2IntEntrySet()) {
            if (entry.getIntValue() >= MIN_FLOOR_COLUMNS) {
                floorLevels.add(entry.getLongKey());
            }
        }
        floorLevels.sort(null);

        // 家具や段差による近接した床レベルを除去し、十分なクリアランスを持つ床のみを階とする
        final LongArrayList filteredFloorLevels = new LongArrayList();
        int lastFloorY = Integer.MIN_VALUE;
        for (int i = 0; i < floorLevels.size(); i++) {
            int y = (int) floorLevels.getLong(i);
            if (lastFloorY == Integer.MIN_VALUE || y - lastFloorY >= MIN_STOREY_CLEARANCE) {
                filteredFloorLevels.add(y);
                lastFloorY = y;
            }
        }

        final Long2IntOpenHashMap storeyOf = new Long2IntOpenHashMap(air.size());
        storeyOf.defaultReturnValue(0);
        int maxStorey = 0;
        for (long cell : air) {
            int y = BlockPos.getY(cell);
            int storey = 0;
            for (int i = 0; i < filteredFloorLevels.size(); i++) {
                if (filteredFloorLevels.getLong(i) < y) {
                    storey++;
                } else {
                    break;
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

        List<LongOpenHashSet> validComponents = new ArrayList<>();
        List<LongOpenHashSet> invalidComponents = new ArrayList<>();
        for (var entry : byStorey.long2ObjectEntrySet()) {
            LongOpenHashSet cells = entry.getValue();
            if (isRoomHeightValid(cells, boundsMin(cells), boundsMax(cells))) {
                validComponents.add(cells);
            } else {
                invalidComponents.add(cells);
            }
        }
        if (validComponents.isEmpty() && !invalidComponents.isEmpty()) {
            invalidComponents.sort(Comparator.comparingInt(LongOpenHashSet::size).reversed());
            validComponents.add(invalidComponents.remove(0));
        }
        if (!validComponents.isEmpty() && !invalidComponents.isEmpty()) {
            mergeInvalidComponents(validComponents, invalidComponents);
        }

        List<Room> rooms = new ArrayList<>(validComponents.size());
        for (LongOpenHashSet cells : validComponents) {
            int storey = storeyOf.get(cells.iterator().nextLong());
            rooms.add(new Room(cells, storey, boundsMin(cells), boundsMax(cells)));
        }
        rooms.sort(Comparator.comparingInt(Room::size).reversed());
        int playerRoomIndex = indexOfPlayerRoom(rooms, seed != null ? seed : roomSeed);
        return new Result(rooms, playerRoomIndex, maxStorey + 1, storeyOf);
    }

    private static Result buildRooms(LongSet air, Long2IntOpenHashMap componentOf,
            Long2IntOpenHashMap storeyOf, int componentCount, BlockPos roomSeed, BlockPos seed) {
        Long2ObjectOpenHashMap<LongOpenHashSet> cellsByComponent = new Long2ObjectOpenHashMap<>(componentCount);
        for (long cell : air) {
            int component = componentOf.get(cell);
            cellsByComponent.computeIfAbsent(component, k -> new LongOpenHashSet()).add(cell);
        }

        List<LongOpenHashSet> validComponents = new ArrayList<>();
        List<LongOpenHashSet> invalidComponents = new ArrayList<>();
        for (var entry : cellsByComponent.long2ObjectEntrySet()) {
            LongOpenHashSet cells = entry.getValue();
            if (isRoomHeightValid(cells, boundsMin(cells), boundsMax(cells))) {
                validComponents.add(cells);
            } else {
                invalidComponents.add(cells);
            }
        }
        if (validComponents.isEmpty() && !invalidComponents.isEmpty()) {
            invalidComponents.sort(Comparator.comparingInt(LongOpenHashSet::size).reversed());
            validComponents.add(invalidComponents.remove(0));
        }
        if (!validComponents.isEmpty() && !invalidComponents.isEmpty()) {
            mergeInvalidComponents(validComponents, invalidComponents);
        }

        List<Room> rooms = new ArrayList<>(validComponents.size());
        for (LongOpenHashSet cells : validComponents) {
            int storey = storeyOf.get(cells.iterator().nextLong());
            rooms.add(new Room(cells, storey, boundsMin(cells), boundsMax(cells)));
        }
        rooms.sort(Comparator.comparingInt(Room::size).reversed());
        int playerRoomIndex = indexOfPlayerRoom(rooms, seed != null ? seed : roomSeed);
        int storeyCount = 0;
        for (Room room : rooms) {
            if (room.getStorey() + 1 > storeyCount) {
                storeyCount = room.getStorey() + 1;
            }
        }
        return new Result(rooms, playerRoomIndex, storeyCount, storeyOf);
    }

    /**
     * 水平に広がる面(スラブ)か。上下を空気に挟まれた殻セルでも、梁のような細い遮蔽物は
     * 水平4近傍のうち3つ以上が殻にならないため面とみなさない(階を分割しない)。
     */
    private static boolean isWideSlab(LongSet shell, int x, int y, int z) {
        int solid = 0;
        for (Direction d : HORIZONTAL) {
            if (shell.contains(BlockPos.asLong(x + d.getStepX(), y, z + d.getStepZ()))) {
                solid++;
            }
        }
        return solid >= 3;
    }

    /**
     * 部屋が人間（プレイヤー）の入れる最低高さ（2ブロック以上）を満たしているか判定する。
     * 部屋のバウンディングボックスの高さが {@link #MIN_ROOM_HEIGHT} 以上で、かつ
     * 部屋内に上下連続する {@link #MIN_ROOM_HEIGHT} ブロック以上の気柱が少なくとも1箇所存在することを要求する。
     */
    private static boolean isRoomHeightValid(LongSet cells, BlockPos minPos, BlockPos maxPos) {
        if (maxPos.getY() - minPos.getY() + 1 < MIN_ROOM_HEIGHT) {
            return false;
        }
        for (long cell : cells) {
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            if (cells.contains(BlockPos.asLong(x, y + 1, z))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 高さ2ブロック未満の成分（床の段差や家具下など）を、隣接する有効な部屋にマージする。
     */
    private static void mergeInvalidComponents(List<LongOpenHashSet> validList,
                                               List<LongOpenHashSet> invalidList) {
        Long2IntOpenHashMap cellToValidIdx = new Long2IntOpenHashMap();
        cellToValidIdx.defaultReturnValue(-1);
        for (int v = 0; v < validList.size(); v++) {
            for (long c : validList.get(v)) {
                cellToValidIdx.put(c, v);
            }
        }

        boolean mergedAny = true;
        while (mergedAny && !invalidList.isEmpty()) {
            mergedAny = false;
            for (int i = invalidList.size() - 1; i >= 0; i--) {
                LongOpenHashSet invalid = invalidList.get(i);
                int bestValidIdx = -1;
                int maxContact = 0;
                Int2IntOpenHashMap contacts = new Int2IntOpenHashMap();

                for (long c : invalid) {
                    int cx = BlockPos.getX(c);
                    int cy = BlockPos.getY(c);
                    int cz = BlockPos.getZ(c);
                    for (Direction dir : ALL6) {
                        long nb = BlockPos.asLong(
                                cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                        int vIdx = cellToValidIdx.get(nb);
                        if (vIdx >= 0) {
                            int cnt = contacts.addTo(vIdx, 1) + 1;
                            if (cnt > maxContact) {
                                maxContact = cnt;
                                bestValidIdx = vIdx;
                            }
                        }
                    }
                }

                if (bestValidIdx >= 0) {
                    LongOpenHashSet target = validList.get(bestValidIdx);
                    target.addAll(invalid);
                    for (long c : invalid) {
                        cellToValidIdx.put(c, bestValidIdx);
                    }
                    invalidList.remove(i);
                    mergedAny = true;
                }
            }
        }
    }

    /**
     * プレイヤーが存在する部屋のインデックスを返す。
     * 全身（足元と頭の両方）が入っている部屋を最優先し、
     * 次いで頭（視線位置）が入っている部屋、足元が入っている部屋の順で検索する。
     */
    private static int indexOfPlayerRoom(List<Room> rooms, BlockPos seed) {
        if (seed == null || rooms.isEmpty()) {
            return -1;
        }
        long feet = seed.asLong();
        long head = seed.above().asLong();

        // 1. 全身（足元と頭の両方）が入っている部屋を最優先
        for (int i = 0; i < rooms.size(); i++) {
            LongSet cells = rooms.get(i).getAirCells();
            if (cells.contains(feet) && cells.contains(head)) {
                return i;
            }
        }

        // 2. 全身が入る部屋が見つからない場合、頭（視線位置）が入っている部屋を優先
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getAirCells().contains(head)) {
                return i;
            }
        }

        // 3. フォールバック: 足元が入っている部屋
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getAirCells().contains(feet)) {
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
