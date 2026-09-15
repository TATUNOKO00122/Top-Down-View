package com.topdownview.culling;

import com.topdownview.spatial.BlockMap;
import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.BuildingClassifier.Label;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.util.SpaceProfiler;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 屋内壁のカリング処理。{@link BuildingClassifier} が部屋殻を ROOF/WALL/FLOOR に分類した結果から
 * WALL ブロックを抽出し、内向き法線がカメラ→プレイヤー方向を向く「手前側の壁」だけをカリング対象にする。
 *
 * <p>判定はプレイヤー足元からの垂直オフセットに依存しないため、天井や壁が高くても機能する。
 * 軸上視点では手前の壁1枚、斜め視点では隣接する2枚が同時に対象となり、カメラが真上付近のときは
 * 水平方向の視線がほぼ無いため全壁を保護する。
 *
 * <p>マルチスレッド: カリング集合はチャンクビルドワーカーから読まれるため、再構築した集合を
 * volatile 参照ごと差し替えて競合を避ける。
 */
public final class WallCullingHandler {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static final Direction[] ALL6 = Direction.values();

    /**
     * 内向き法線と視線方向のなす角が 45° 以内の壁だけを手前側とみなす。
     *
     * <p>しきい値 0 だと数度回転しただけで隣接する2枚の壁が同時に対象になり、視点をわずかに
     * 動かすたびに壁が2枚消えてしまう。しきい値を cos45° にすると、軸直交する4枚の壁のうち
     * 同時に対象になるのは常に1枚以下になり、正面に近い壁だけが消える。
     */
    private static final double OCCLUDING_DOT_THRESHOLD = Math.cos(Math.PI / 4.0);

    /** カメラ→プレイヤーの水平距離がこれ未満なら真上視点とみなし、全壁を保護する。 */
    private static final double MIN_VIEW_DIR_LEN_SQ = 1.0E-4;

    /** 再構築をスキップする視線方向の変化量（成分ごと）。 */
    private static final double VIEW_DIR_EPSILON = 1.0E-3;

    /** 厚い壁の外層へ法線を伝播させる最大ホップ数。{@link BuildingClassifier#T_MAX} に合わせる。 */
    private static final int MAX_PROPAGATION_DEPTH = BuildingClassifier.T_MAX;

    private volatile LongOpenHashSet occludingWallPositions = new LongOpenHashSet();

    private BuildingClassifier.Result classification = BuildingClassifier.Result.EMPTY;
    private LongSet airCells = null;

    private LongOpenHashSet wallCells = new LongOpenHashSet();
    private Long2FloatOpenHashMap wallNormalX = new Long2FloatOpenHashMap();
    private Long2FloatOpenHashMap wallNormalZ = new Long2FloatOpenHashMap();

    /** 再分類の基準にした部屋の AABB + 階。同じ部屋にいる間は建物構造が不変なので再分類しない。 */
    private boolean hasRoomKey = false;
    private int roomMinX;
    private int roomMinY;
    private int roomMinZ;
    private int roomMaxX;
    private int roomMaxY;
    private int roomMaxZ;
    private int roomStorey;

    private double lastViewDirX = 0.0;
    private double lastViewDirZ = 0.0;
    private boolean classificationDirty = true;

    public void clearCache() {
        classification = BuildingClassifier.Result.EMPTY;
        airCells = null;
        wallCells = new LongOpenHashSet();
        wallNormalX = new Long2FloatOpenHashMap();
        wallNormalZ = new Long2FloatOpenHashMap();
        occludingWallPositions = new LongOpenHashSet();
        hasRoomKey = false;
        lastViewDirX = 0.0;
        lastViewDirZ = 0.0;
        classificationDirty = true;
    }

    public boolean isOccludingWall(long posLong) {
        LongOpenHashSet set = occludingWallPositions;
        return !set.isEmpty() && set.contains(posLong);
    }

    /** 直近の分類結果。未分類なら {@link BuildingClassifier.Result#EMPTY}。 */
    public BuildingClassifier.Result getClassification() {
        return classification;
    }

    /**
     * 手前壁集合のみを空にする。分類結果は残すため、分類を共有する天井カリングを壊さない。
     */
    public void clearOccludingWalls() {
        occludingWallPositions = new LongOpenHashSet();
    }

    /**
     * 部屋殻を再分類し、WALL ブロックとその内向き法線を構築する。
     *
     * <p>同じ部屋にいる間は建物構造が変わらないため、部屋の AABB + 階が変化したときだけ再分類する。
     * 2ブロック移動ごとに classify を走らせる従来方式に比べ、歩行中の再分類回数を大幅に削減できる。
     *
     * @param playerRoom プレイヤーが属する部屋。{@code null} の場合は部屋空気全体を法線計算に使う。
     */
    public void updateClassification(RoomFloodFill.Result room, boolean enclosed,
            RoomSegmentation.Room playerRoom, BlockMap blockMap) {
        if (!enclosed || room == null || !room.isEnclosed() || blockMap == null) {
            if (classification.isValid() || !occludingWallPositions.isEmpty()) {
                clearCache();
            }
            return;
        }

        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final int storey;
        if (playerRoom != null) {
            BlockPos mn = playerRoom.getMinPos();
            BlockPos mx = playerRoom.getMaxPos();
            minX = mn.getX();
            minY = mn.getY();
            minZ = mn.getZ();
            maxX = mx.getX();
            maxY = mx.getY();
            maxZ = mx.getZ();
            storey = playerRoom.getStorey();
        } else {
            BlockPos mn = room.getMinPos();
            BlockPos mx = room.getMaxPos();
            minX = mn.getX();
            minY = mn.getY();
            minZ = mn.getZ();
            maxX = mx.getX();
            maxY = mx.getY();
            maxZ = mx.getZ();
            storey = -1;
        }

        boolean roomChanged = !hasRoomKey
                || minX != roomMinX || minY != roomMinY || minZ != roomMinZ
                || maxX != roomMaxX || maxY != roomMaxY || maxZ != roomMaxZ
                || storey != roomStorey;
        if (classification.isValid() && !roomChanged) {
            return;
        }
        hasRoomKey = true;
        roomMinX = minX;
        roomMinY = minY;
        roomMinZ = minZ;
        roomMaxX = maxX;
        roomMaxY = maxY;
        roomMaxZ = maxZ;
        roomStorey = storey;

        long tClassify = System.nanoTime();
        classification = BuildingClassifier.classify(room, blockMap);
        SpaceProfiler.CLASSIFY.add(System.nanoTime() - tClassify);
        // 法線はプレイヤーの部屋の空気だけを基準にする。隣室に面する壁を手前壁と誤判定しない。
        airCells = (playerRoom != null) ? playerRoom.getAirCells() : room.getAirCells();
        buildWallData();
        classificationDirty = true;
    }

    /**
     * 視線方向（カメラ→プレイヤー水平）に基づき手前壁集合を再構築する。
     * 方向がほぼ変わっていなければ再計算しない。
     */
    public void refreshOccluding(double camX, double camZ, double playerX, double playerZ) {
        double vx = playerX - camX;
        double vz = playerZ - camZ;
        double lenSq = vx * vx + vz * vz;
        if (lenSq < MIN_VIEW_DIR_LEN_SQ) {
            // カメラが真上付近: 手前壁は存在しないため全壁保護
            if (!occludingWallPositions.isEmpty()) {
                occludingWallPositions = new LongOpenHashSet();
            }
            lastViewDirX = 0.0;
            lastViewDirZ = 0.0;
            return;
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        vx *= invLen;
        vz *= invLen;

        if (!classificationDirty
                && Math.abs(vx - lastViewDirX) < VIEW_DIR_EPSILON
                && Math.abs(vz - lastViewDirZ) < VIEW_DIR_EPSILON) {
            return;
        }
        lastViewDirX = vx;
        lastViewDirZ = vz;
        classificationDirty = false;

        buildOccludingSet(vx, vz);
    }

    /**
     * WALL セルと、水平4方向の空気隣接から求めた内向き法線を事前計算する。
     * 直接空気に接しないセルは法線 (0,0) のままにし、後段の伝播で扱う。
     */
    private void buildWallData() {
        LongOpenHashSet cells = new LongOpenHashSet();
        Long2FloatOpenHashMap nxMap = new Long2FloatOpenHashMap();
        Long2FloatOpenHashMap nzMap = new Long2FloatOpenHashMap();

        if (classification.isValid() && airCells != null) {
            var it = classification.getLabels().long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getValue() != Label.WALL) {
                    continue;
                }
                long b = entry.getLongKey();
                cells.add(b);

                int bx = BlockPos.getX(b);
                int by = BlockPos.getY(b);
                int bz = BlockPos.getZ(b);
                double nx = 0.0;
                double nz = 0.0;
                for (Direction d : HORIZONTAL) {
                    if (airCells.contains(BlockPos.asLong(bx + d.getStepX(), by, bz + d.getStepZ()))) {
                        nx += d.getStepX();
                        nz += d.getStepZ();
                    }
                }
                double len = Math.sqrt(nx * nx + nz * nz);
                if (len < 1.0E-6) {
                    continue;
                }
                nxMap.put(b, (float) (nx / len));
                nzMap.put(b, (float) (nz / len));
            }
        }

        wallCells = cells;
        wallNormalX = nxMap;
        wallNormalZ = nzMap;
    }

    /**
     * 内向き法線が視線方向を向く WALL セルを手前壁シードとし、厚い壁の外層へ厚み
     * {@link #MAX_PROPAGATION_DEPTH} まで伝播させて手前壁集合を確定する。
     */
    private void buildOccludingSet(double vx, double vz) {
        if (!classification.isValid()) {
            occludingWallPositions = new LongOpenHashSet();
            return;
        }

        LongOpenHashSet occludingSeeds = new LongOpenHashSet();
        LongOpenHashSet protectedSeeds = new LongOpenHashSet();
        LongIterator it = wallCells.iterator();
        while (it.hasNext()) {
            long b = it.nextLong();
            float nx = wallNormalX.get(b);
            float nz = wallNormalZ.get(b);
            if (nx == 0.0f && nz == 0.0f) {
                continue;
            }
            double dot = nx * vx + nz * vz;
            if (dot > OCCLUDING_DOT_THRESHOLD) {
                occludingSeeds.add(b);
            } else {
                protectedSeeds.add(b);
            }
        }

        if (occludingSeeds.isEmpty()) {
            occludingWallPositions = new LongOpenHashSet();
            return;
        }

        Long2IntOpenHashMap distOccluding = bfsDistance(wallCells, occludingSeeds);
        Long2IntOpenHashMap distProtected = bfsDistance(wallCells, protectedSeeds);

        LongOpenHashSet next = new LongOpenHashSet();
        it = wallCells.iterator();
        while (it.hasNext()) {
            long b = it.nextLong();
            int dOcc = distOccluding.get(b);
            if (dOcc < 0) {
                // どちらのシードにも属さない外層セルは保護側に倒す
                continue;
            }
            int dProt = distProtected.get(b);
            if (dProt < 0 || dOcc < dProt) {
                next.add(b);
            }
        }
        occludingWallPositions = next;
    }

    private static Long2IntOpenHashMap bfsDistance(LongOpenHashSet cells, LongOpenHashSet seeds) {
        Long2IntOpenHashMap distance = new Long2IntOpenHashMap(Math.max(1, seeds.size()));
        distance.defaultReturnValue(-1);
        if (seeds.isEmpty()) {
            return distance;
        }

        LongArrayList queue = new LongArrayList();
        LongIterator it = seeds.iterator();
        while (it.hasNext()) {
            long s = it.nextLong();
            if (cells.contains(s) && distance.put(s, 0) == -1) {
                queue.add(s);
            }
        }

        int head = 0;
        while (head < queue.size()) {
            long cur = queue.getLong(head++);
            int cd = distance.get(cur);
            if (cd >= MAX_PROPAGATION_DEPTH) {
                continue;
            }
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : ALL6) {
                long nlong = BlockPos.asLong(cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                if (!cells.contains(nlong) || distance.containsKey(nlong)) {
                    continue;
                }
                distance.put(nlong, cd + 1);
                queue.add(nlong);
            }
        }
        return distance;
    }
}
