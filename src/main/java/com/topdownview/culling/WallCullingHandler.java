package com.topdownview.culling;

import com.topdownview.Config;
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

import java.util.ArrayList;
import java.util.List;

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

    /** これ未満のプレイヤー〜カメラ水平距離²では扇形判定しない (カメラが真上付近)。 */
    private static final double MIN_SECTOR_LEN_SQ = 1.0E-4;

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
    private double lastRayCamX = 0.0;
    private double lastRayCamY = 0.0;
    private double lastRayCamZ = 0.0;
    private double lastRayPlayerX = 0.0;
    private double lastRayPlayerY = 0.0;
    private double lastRayPlayerZ = 0.0;
    private boolean classificationDirty = true;

    /** 要素別モード: WALL を連結パネル単位で判定する。 */
    private boolean elementMode = false;
    private boolean lastElementMode = false;

    /** 連結した壁面パネル (法線が同方向で面内連結な WALL セルの集合)。 */
    private final List<Panel> panels = new ArrayList<>();

    /** WALL セル → パネル番号。パネル外は -1。 */
    private final Long2IntOpenHashMap panelOf = new Long2IntOpenHashMap();

    /** 手前壁集合を再構築するたびに進む世代番号。チャンク再構築のトリガに使う。 */
    private volatile long occludingGeneration = 0;

    private static final class Panel {
        final LongOpenHashSet cells;

        Panel(LongOpenHashSet cells) {
            this.cells = cells;
        }
    }

    public void clearCache() {
        classification = BuildingClassifier.Result.EMPTY;
        airCells = null;
        wallCells = new LongOpenHashSet();
        wallNormalX = new Long2FloatOpenHashMap();
        wallNormalZ = new Long2FloatOpenHashMap();
        occludingWallPositions = new LongOpenHashSet();
        panels.clear();
        panelOf.clear();
        hasRoomKey = false;
        lastElementMode = false;
        lastViewDirX = 0.0;
        lastViewDirZ = 0.0;
        lastRayCamX = 0.0;
        lastRayCamY = 0.0;
        lastRayCamZ = 0.0;
        lastRayPlayerX = 0.0;
        lastRayPlayerY = 0.0;
        lastRayPlayerZ = 0.0;
        classificationDirty = true;
        occludingGeneration++;
    }

    /** 手前壁集合の世代番号。値が変わればカリング結果が変わった可能性がある。 */
    public long getOccludingGeneration() {
        return occludingGeneration;
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
        if (!occludingWallPositions.isEmpty()) {
            occludingWallPositions = new LongOpenHashSet();
            occludingGeneration++;
        }
    }

    /**
     * 部屋殻を再分類し、WALL ブロックとその内向き法線を構築する。
     *
     * <p>同じ部屋にいる間は建物構造が変わらないため、部屋の AABB + 階が変化したときだけ再分類する。
     * 2ブロック移動ごとに classify を走らせる従来方式に比べ、歩行中の再分類回数を大幅に削減できる。
     *
     * @param playerRoom  プレイヤーが属する部屋。{@code null} の場合は部屋空気全体を法線計算に使う。
     * @param elementMode true なら WALL を連結パネルへ分割する (屋内要素別カリング)。
     */
    public void updateClassification(RoomFloodFill.Result room, boolean enclosed,
            RoomSegmentation.Room playerRoom, BlockMap blockMap, boolean elementMode) {
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
                || storey != roomStorey
                || elementMode != lastElementMode;
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
        lastElementMode = elementMode;
        this.elementMode = elementMode;

        long tClassify = System.nanoTime();
        classification = BuildingClassifier.classify(room, blockMap);
        SpaceProfiler.CLASSIFY.add(System.nanoTime() - tClassify);
        // 法線はプレイヤーの部屋の空気だけを基準にする。隣室に面する壁を手前壁と誤判定しない。
        airCells = (playerRoom != null) ? playerRoom.getAirCells() : room.getAirCells();
        buildWallData();
        if (elementMode) {
            buildPanels();
        } else {
            panels.clear();
            panelOf.clear();
        }
        classificationDirty = true;
    }

    /**
     * 視線に基づき手前壁集合を再構築する。方向がほぼ変わっていなければ再計算しない。
     *
     * <p>要素別モードではカメラ→プレイヤーの視線が貫通する壁面パネルを全て対象にする。
     * 従来モードでは内向き法線が視線方向を向く壁を対象にする。
     */
    public void refreshOccluding(double camX, double camY, double camZ,
            double playerX, double playerY, double playerZ) {
        double vx = playerX - camX;
        double vz = playerZ - camZ;
        double lenSq = vx * vx + vz * vz;
        if (lenSq < MIN_VIEW_DIR_LEN_SQ) {
            // カメラが真上付近: 水平の手前壁は無い。ただし要素別モードでは鉛直の視線も見るため継続する。
            if (!elementMode) {
                clearOccludingWalls();
                lastViewDirX = 0.0;
                lastViewDirZ = 0.0;
                return;
            }
        }
        double invLen = lenSq > 0.0 ? 1.0 / Math.sqrt(lenSq) : 0.0;
        vx *= invLen;
        vz *= invLen;

        // 視線方向だけでなく、レイの両端点(カメラ/プレイヤーのブロック位置)が動いた時も再構築する。
        boolean changed = classificationDirty
                || Math.abs(vx - lastViewDirX) >= VIEW_DIR_EPSILON
                || Math.abs(vz - lastViewDirZ) >= VIEW_DIR_EPSILON
                || Math.abs(camX - lastRayCamX) >= VIEW_DIR_EPSILON
                || Math.abs(camY - lastRayCamY) >= VIEW_DIR_EPSILON
                || Math.abs(camZ - lastRayCamZ) >= VIEW_DIR_EPSILON
                || Math.abs(playerX - lastRayPlayerX) >= VIEW_DIR_EPSILON
                || Math.abs(playerY - lastRayPlayerY) >= VIEW_DIR_EPSILON
                || Math.abs(playerZ - lastRayPlayerZ) >= VIEW_DIR_EPSILON;
        if (!changed) {
            return;
        }
        lastViewDirX = vx;
        lastViewDirZ = vz;
        lastRayCamX = camX;
        lastRayCamY = camY;
        lastRayCamZ = camZ;
        lastRayPlayerX = playerX;
        lastRayPlayerY = playerY;
        lastRayPlayerZ = playerZ;
        classificationDirty = false;

        if (elementMode) {
            buildOccludingSetFromSector(camX, camZ, playerX, playerZ);
        } else {
            buildOccludingSet(vx, vz);
        }
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
     * 内向き法線が同方向 (8方位に量子化) で面内連結な WALL セルを1つの壁面パネルへまとめる。
     *
     * <p>軸直交する壁は同じ法線のセルが連結して1パネルになる。斜め壁は45度単位で分類される。
     * 法線を持たない (直接空気に接しない) 厚み方向のセルはパネル外とし、後段の伝播で扱う。
     */
    private void buildPanels() {
        panels.clear();
        panelOf.clear();
        panelOf.defaultReturnValue(-1);
        if (!classification.isValid() || wallCells.isEmpty()) {
            return;
        }
        LongArrayList queue = new LongArrayList();
        LongIterator it = wallCells.iterator();
        while (it.hasNext()) {
            long start = it.nextLong();
            if (panelOf.containsKey(start)) {
                continue;
            }
            float snx = wallNormalX.get(start);
            float snz = wallNormalZ.get(start);
            if (snx == 0.0f && snz == 0.0f) {
                continue;
            }
            int dir = quantizeDirection(snx, snz);
            int panelIndex = panels.size();
            LongOpenHashSet cells = new LongOpenHashSet();
            queue.clear();
            queue.add(start);
            panelOf.put(start, panelIndex);
            int head = 0;
            while (head < queue.size()) {
                long cur = queue.getLong(head++);
                cells.add(cur);
                int cx = BlockPos.getX(cur);
                int cy = BlockPos.getY(cur);
                int cz = BlockPos.getZ(cur);
                for (Direction d : ALL6) {
                    long nlong = BlockPos.asLong(cx + d.getStepX(), cy + d.getStepY(), cz + d.getStepZ());
                    if (panelOf.containsKey(nlong) || !wallCells.contains(nlong)) {
                        continue;
                    }
                    float nnx = wallNormalX.get(nlong);
                    float nnz = wallNormalZ.get(nlong);
                    if ((nnx == 0.0f && nnz == 0.0f) || quantizeDirection(nnx, nnz) != dir) {
                        continue;
                    }
                    panelOf.put(nlong, panelIndex);
                    queue.add(nlong);
                }
            }
            panels.add(new Panel(cells));
        }
    }

    private static int quantizeDirection(float nx, float nz) {
        return (int) Math.round(Math.atan2(nz, nx) / (Math.PI / 4.0)) & 7;
    }

    /**
     * 内向き法線が視線方向を向く WALL セルを手前壁シードとし、厚い壁の外層へ厚み
     * {@link #MAX_PROPAGATION_DEPTH} まで伝播させて手前壁集合を確定する。
     */
    private void buildOccludingSet(double vx, double vz) {
        if (!classification.isValid()) {
            applyOccludingSet(new LongOpenHashSet());
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
            applyOccludingSet(new LongOpenHashSet());
            return;
        }

        applyOccludingSet(resolveOccludingByDistance(occludingSeeds, protectedSeeds));
    }

    /**
     * プレイヤーを頂点に、カメラ方向へ水平に開く扇形 (fan) の中にある WALL セルを全て手前壁シードと
     * する。扇形の半径はプレイヤー〜カメラの水平距離、半角は {@code indoorWallCullingHalfAngle}。
     *
     * <p>カメラ側の壁だけを消すため、プレイヤーが壁に近づいても反対側の壁は消えない。立体の
     * トンネルではなく「水平な扇形」なので、プレイヤー周囲の側面・背面の壁を巻き込みにくい。
     * 対象セルが属する壁面パネルは丸ごとカリングし、パネル外 (厚み方向) のセルは直接シードにする。
     * 非対象パネルを保護シードとして距離伝播で厚い壁の外層まで取り込む。
     */
    private void buildOccludingSetFromSector(double camX, double camZ,
            double playerX, double playerZ) {
        if (!classification.isValid() || wallCells.isEmpty()) {
            applyOccludingSet(new LongOpenHashSet());
            return;
        }

        double dirX = camX - playerX;
        double dirZ = camZ - playerZ;
        double radiusSq = dirX * dirX + dirZ * dirZ;
        if (radiusSq < MIN_SECTOR_LEN_SQ) {
            // カメラが真上付近: 水平の遮蔽壁は無いため全壁保護
            applyOccludingSet(new LongOpenHashSet());
            return;
        }
        double radius = Math.sqrt(radiusSq);
        dirX /= radius;
        dirZ /= radius;
        double cosHalfAngle = Math.cos(Math.toRadians(Config.getIndoorWallCullingHalfAngle()));

        LongOpenHashSet hitPanels = new LongOpenHashSet();
        LongOpenHashSet occludingSeeds = new LongOpenHashSet();
        LongIterator it = wallCells.iterator();
        while (it.hasNext()) {
            long b = it.nextLong();
            double dx = BlockPos.getX(b) + 0.5 - playerX;
            double dz = BlockPos.getZ(b) + 0.5 - playerZ;
            double distSq = dx * dx + dz * dz;
            if (distSq > radiusSq) {
                continue;
            }
            if (distSq > MIN_SECTOR_LEN_SQ) {
                double dist = Math.sqrt(distSq);
                if ((dx * dirX + dz * dirZ) / dist < cosHalfAngle) {
                    continue;
                }
            }
            int panel = panelOf.get(b);
            if (panel >= 0) {
                hitPanels.add(panel);
            } else {
                occludingSeeds.add(b);
            }
        }

        if (hitPanels.isEmpty() && occludingSeeds.isEmpty()) {
            applyOccludingSet(new LongOpenHashSet());
            return;
        }

        LongOpenHashSet protectedSeeds = new LongOpenHashSet();
        for (int i = 0, n = panels.size(); i < n; i++) {
            Panel panel = panels.get(i);
            if (hitPanels.contains(i)) {
                occludingSeeds.addAll(panel.cells);
            } else {
                protectedSeeds.addAll(panel.cells);
            }
        }

        applyOccludingSet(resolveOccludingByDistance(occludingSeeds, protectedSeeds));
    }

    /**
     * 手前壁シードと保護シードへの距離を比べ、手前壁側に近い WALL セルだけをカリング対象にする。
     * どちらにも到達しない外層セルは保護側に倒す。
     */
    private LongOpenHashSet resolveOccludingByDistance(LongOpenHashSet occludingSeeds, LongOpenHashSet protectedSeeds) {
        Long2IntOpenHashMap distOccluding = bfsDistance(wallCells, occludingSeeds);
        Long2IntOpenHashMap distProtected = bfsDistance(wallCells, protectedSeeds);

        LongOpenHashSet next = new LongOpenHashSet();
        LongIterator it = wallCells.iterator();
        while (it.hasNext()) {
            long b = it.nextLong();
            int dOcc = distOccluding.get(b);
            if (dOcc < 0) {
                continue;
            }
            int dProt = distProtected.get(b);
            if (dProt < 0 || dOcc < dProt) {
                next.add(b);
            }
        }
        return next;
    }

    private void applyOccludingSet(LongOpenHashSet set) {
        if (set.equals(occludingWallPositions)) {
            return;
        }
        occludingWallPositions = set;
        occludingGeneration++;
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
