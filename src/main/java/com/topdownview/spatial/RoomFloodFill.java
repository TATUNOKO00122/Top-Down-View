package com.topdownview.spatial;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.Objects;

/**
 * 3D BFS (幅優先探索) による部屋形状・空間自動認識クラス。
 *
 * <p>プレイヤー足元を起点に空間をフラッドフィルスキャンし、
 * 通過可能な空気セル領域と、それを囲む1ブロック厚の壁殻 (Shell) セル領域を特定します。
 *
 * <p>各セルにおいて直上に固体の覆い(天井)があるかをローカルに判定し (葉ブロック等は透過)、
 * 覆いの無いセルで探索を打ち切ることで、密閉度ではなく「覆われた空気領域」を空間として抽出します。
 * 空(Heightmap)に依存しないため、複雑な形の家・中庭・洞窟も扱えます。
 *
 * <p>さらに「屋外で頭上に覆いがあるだけ」の空間 (オーバーハング・木陰・屋根付き広場) を
 * 屋内と誤認しないよう、覆いの無い横方向の空気に直接面するセル (露出セル) を領域から除外した上で、
 * 残った領域の水平境界に占める開放面の割合が {@link #MAX_OPEN_FACE_PERCENT}% を超えないことを
 * 屋内の条件にします。2方向以上が大きく開いた屋根付き構造は開放面が壁面を上回るため除外されます。
 *
 * <p>覆い判定は「直上から最初の固体に当たるまで」だけ縦走査します (天井が低いほど安価)。
 * 覆いが見つからなかったセルは {@link Scratch} に記録して再走査を避けます。
 */
public final class RoomFloodFill {

    private RoomFloodFill() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** XZ方向の最大探索半径（ブロック） */
    public static final int MAX_RADIUS_XZ = 24;

    /** Y方向の最大探索半径（ブロック） */
    public static final int MAX_RADIUS_Y = 12;

    /** BFSで探索する最大空気セル数（安全弁） */
    public static final int MAX_FLOOD_CELLS = 8000;

    /** 各セルで直上に覆い(固体天井)を探す走査距離 */
    public static final int CEILING_SCAN_HEIGHT = 24;

    /** 水平境界のうち開放面が占めてよい割合 (壁面に対する%)。超過した場合は屋外とみなす */
    public static final int MAX_OPEN_FACE_PERCENT = 40;

    /**
     * 「外気に開いた細長い連結路」を切り離す対象とする最小チェーン長さ
     * (チェーンのバウンディングボックスの最大辺)。これ未満のチェーン (扉・短い通路・
     * 2x2の小部屋) は切り離さない。
     */
    public static final int THIN_SEVER_MIN_EXTENT = 4;

    /**
     * チェーン切断の深さ。外気に面したセル (距離0) とそのチェーン内隣接セル (距離1) までを
     * 切り離し、それ奥の室内側セル (2幅の部屋・階段・廊下) は領域に残す。
     * 深さ1で軒2〜3段のストリップを完全に切断できる。
     */
    public static final int SEVER_CUT_DEPTH = 1;

    /**
     * 足元の高さの局所断面テストのパラメータ。
     *
     * <p>全域の開放率判定は領域内の壁面総数で希釈される。特に軒下リング (建物外壁に沿って
     * 覆いが連なり、その下を外周が通る空間) では家の内壁面が開放面を上回り、屋外に立って
     * いても屋内判定になる。そこで種の足元Y帯に限定した2つの直検を併用する:
     * 断面BFS (扉を壁扱いして辿った範囲の開放面の割合) と、巨大天蓋サンプル (覆いまでの
     * 垂直距離が人間スケールを超えるセルの割合)。
     */
    public static final int LOCAL_SECTION_RADIUS = 6;
    /** 断面BFSが辿るY帯のレイヤー数 (足元+頭の2層)。 */
    public static final int LOCAL_SECTION_HEIGHT = 2;
    /** 覆いが「近い」とみなす垂直距離。これを超える覆いは巨大天蓋とみなす。 */
    public static final int LOCAL_MAX_COVER_HEIGHT = 10;
    /** 近い覆いを持つセルの必要割合 (%)。下回った場合は屋外。 */
    public static final int LOCAL_MIN_COVERED_PERCENT = 75;
    /** 局所断面テストが成立する最小セル数。これ未満なら判定をスキップする。 */
    private static final int LOCAL_MIN_SAMPLES = 8;

    /** 探索を行う6方向 */
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * フラッドフィル中に再利用する作業バッファ。
     *
     * <p>呼び出し側がスレッドごとに1つ保持することで、毎tickの再確保を避けます。
     * スレッドセーフではないため、複数スレッドから同時に使わないでください。
     */
    public static final class Scratch {
        private final LongOpenHashSet visitedAir = new LongOpenHashSet();
        private final LongOpenHashSet visitedShell = new LongOpenHashSet();
        private final LongOpenHashSet noCover = new LongOpenHashSet();
        /** 開口 (覆いの無い横方向の空気) に直接面した非連結エアセル。後段で領域から除外する */
        private final LongOpenHashSet exposedAir = new LongOpenHashSet();
        private final LongArrayList queue = new LongArrayList();
        private final BlockMap blockMap = new BlockMap();

        public void clear() {
            visitedAir.clear();
            visitedShell.clear();
            noCover.clear();
            exposedAir.clear();
            queue.clear();
        }

        /** この探索で共有するブロック判定キャッシュ。 */
        public BlockMap getBlockMap() {
            return blockMap;
        }
    }

    /**
     * 指定された起点から空間フラッドフィルを実行する。
     *
     * @param level ワールド
     * @param seed  起点となるブロック座標（通常プレイヤー足元）
     * @return 空間探索結果
     */
    public static Result compute(BlockGetter level, BlockPos seed) {
        return compute(level, seed, null);
    }

    /**
     * 作業バッファを再利用して空間フラッドフィルを実行する。
     *
     * @param level   ワールド
     * @param seed    起点となるブロック座標
     * @param scratch 再利用バッファ。{@code null} の場合は内部で生成。
     * @return 空間探索結果
     */
    public static Result compute(BlockGetter level, BlockPos seed, Scratch scratch) {
        if (level == null || seed == null) {
            return Result.EMPTY;
        }
        final Scratch s = (scratch != null) ? scratch : new Scratch();
        s.clear();
        // 起点を中心にブロック判定キャッシュを構築 (flood/classifier/stair で共有)
        s.blockMap.reset(level, seed);

        // 起点が通過可能かチェック。壁に埋まっている場合は頭上などを探す
        BlockPos startPos = findValidSeed(s.blockMap, seed);
        if (startPos == null) {
            return Result.EMPTY;
        }

        final int seedX = startPos.getX();
        final int seedY = startPos.getY();
        final int seedZ = startPos.getZ();

        // 起点自体に直上の覆いが無い場合は屋外として扱う
        if (!isCovered(s, seedX, seedY, seedZ)) {
            return Result.EMPTY;
        }

        final LongOpenHashSet visitedAir = s.visitedAir;
        final LongOpenHashSet visitedShell = s.visitedShell;
        final LongArrayList queue = s.queue;

        long startLong = startPos.asLong();
        visitedAir.add(startLong);
        queue.add(startLong);

        int minX = seedX;
        int minY = seedY;
        int minZ = seedZ;
        int maxX = seedX;
        int maxY = seedY;
        int maxZ = seedZ;

        int head = 0;

        while (head < queue.size() && visitedAir.size() < MAX_FLOOD_CELLS) {
            long currentLong = queue.getLong(head++);
            int cx = BlockPos.getX(currentLong);
            int cy = BlockPos.getY(currentLong);
            int cz = BlockPos.getZ(currentLong);

            // AABB更新
            if (cx < minX) minX = cx;
            if (cy < minY) minY = cy;
            if (cz < minZ) minZ = cz;
            if (cx > maxX) maxX = cx;
            if (cy > maxY) maxY = cy;
            if (cz > maxZ) maxZ = cz;

            // 6方向へ隣接セルを探索
            for (Direction dir : DIRECTIONS) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();

                // 範囲制限チェック
                if (Math.abs(nx - seedX) > MAX_RADIUS_XZ ||
                    Math.abs(ny - seedY) > MAX_RADIUS_Y ||
                    Math.abs(nz - seedZ) > MAX_RADIUS_XZ) {
                    continue;
                }

                long nlong = BlockPos.asLong(nx, ny, nz);

                if (visitedAir.contains(nlong) || visitedShell.contains(nlong)) {
                    continue;
                }

                boolean horizontal = dir.getStepY() == 0;

                // 固体ブロック（壁・床・天井）か通過可能空間かを判定
                if (s.blockMap.isSolid(nx, ny, nz)) {
                    // 閉じた扉などの連結部は、直上に覆いがあるなら屋内の通路として空気同様に扱い
                    // 探索を継続する。これがないと閉じた扉で建物が部屋ごとに分断される。
                    // 屋外のフェンスゲートが外を建物内へ取り込まないよう covered を要求する。
                    if (s.blockMap.isConnector(nx, ny, nz) && isCovered(s, nx, ny, nz)) {
                        visitedAir.add(nlong);
                        queue.add(nlong);
                    } else {
                        visitedShell.add(nlong);
                    }
                } else if (isCovered(s, nx, ny, nz)) {
                    // 直上に固体の覆いがある空気セル → 屋内/洞窟内部として探索継続
                    visitedAir.add(nlong);
                    queue.add(nlong);
                } else if (horizontal) {
                    // 横方向に覆いの無い空気に面する非連結セルは「露出セル」。
                    // 屋外へ開いた軒先・庇の外殻であり、後段の境界再計算で領域から除外する。
                    // 連結部 (扉) は除外せず、開口を挟んだ部屋間の連結を維持する
                    if (!s.blockMap.isConnector(cx, cy, cz)) {
                        s.exposedAir.add(currentLong);
                    }
                }
                // 覆いの無いセルは屋外へ漏れるためキューに入れず打ち切る
            }
        }

        // 開口に面した露出セルを領域から除外してから水平境界を数え直す。これにより
        // 窓・開口の外側にある軒下空間への「漏れ」が切り離され、その外周が壁面として
        // 誤カウントされない。除外済みセルに面する境界は開放面として数える。
        visitedAir.removeAll(s.exposedAir);

        // 橋・渡り廊下など「細長い連結路」を切り離す。壁と屋根に囲まれた細い通路は
        // 露出除外で切り離されないため、2つの家が一つの領域に連結されてしまう。
        // 切り離し後、種の所属コンポーネントだけを残す (天井スライスのAABBが
        // 相手側の家まで広がるのを防ぐ)。
        LongOpenHashSet severed = detectLongThinChains(visitedAir, s.blockMap);
        boolean regionReduced = false;
        if (!severed.isEmpty()) {
            visitedAir.removeAll(severed);
            regionReduced = true;
        }
        if (visitedAir.contains(startLong)) {
            int before = visitedAir.size();
            reduceToSeedComponent(visitedAir, startLong);
            regionReduced = regionReduced || visitedAir.size() != before;
        } else if (severed.contains(startLong)) {
            // 種が分断された連結路上にある (橋の上など): その連結路を空間とする
            visitedAir.clear();
            collectChain(severed, startLong, visitedAir);
            regionReduced = true;
        }
        if (regionReduced) {
            // 縮約後の領域でAABBを張り直す (天井スライスの範囲に直結する)
            minX = Integer.MAX_VALUE;
            minY = Integer.MAX_VALUE;
            minZ = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxY = Integer.MIN_VALUE;
            maxZ = Integer.MIN_VALUE;
            for (long cell : visitedAir) {
                int cx = BlockPos.getX(cell);
                int cy = BlockPos.getY(cell);
                int cz = BlockPos.getZ(cell);
                if (cx < minX) minX = cx;
                if (cy < minY) minY = cy;
                if (cz < minZ) minZ = cz;
                if (cx > maxX) maxX = cx;
                if (cy > maxY) maxY = cy;
                if (cz > maxZ) maxZ = cz;
            }
        }

        int wallFaces = 0;
        int openFaces = 0;
        for (LongIterator airIt = visitedAir.iterator(); airIt.hasNext(); ) {
            long airLong = airIt.nextLong();
            int ax = BlockPos.getX(airLong);
            int ay = BlockPos.getY(airLong);
            int az = BlockPos.getZ(airLong);
            for (Direction dir : DIRECTIONS) {
                if (dir.getStepY() != 0) {
                    continue;
                }
                int nx = ax + dir.getStepX();
                int nz = az + dir.getStepZ();
                if (s.blockMap.isSolid(nx, ay, nz)) {
                    wallFaces++;
                } else if (!visitedAir.contains(BlockPos.asLong(nx, ay, nz))) {
                    openFaces++;
                }
            }
        }

        // 屋内 = ①種のセルが領域に残存すること (露出除外で落ちた = 開口部や軒の際に
        // 直接立っている → 屋外)、②水平境界が壁面支配 (開放面が壁面の
        // MAX_OPEN_FACE_PERCENT% 以下)、③足元断面の局所検定 (軒下リング / 巨大天蓋)
        // を通ること。②だけだと家の内壁面が外周の開放面を希釈し、屋外の軒下で
        // 屋内判定になるため①③を併用する。
        boolean enclosed = visitedAir.contains(startLong) && wallFaces > 0
                && openFaces * 100 <= wallFaces * MAX_OPEN_FACE_PERCENT
                && !hasLocalOpening(s, startPos, visitedAir);
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);

        return new Result(enclosed, visitedAir, visitedShell, min, max, startPos);
    }

    /**
     * 起点位置が通過可能か検証し、埋まっている場合は頭上など適切な空きスペースを返します。
     */
    private static BlockPos findValidSeed(BlockMap blockMap, BlockPos seed) {
        if (!blockMap.isSolid(seed.getX(), seed.getY(), seed.getZ())) {
            return seed;
        }
        // 足元が埋まっている場合、頭上(+1), +2 を試行
        for (int dy = 1; dy <= 2; dy++) {
            if (!blockMap.isSolid(seed.getX(), seed.getY() + dy, seed.getZ())) {
                return seed.above(dy);
            }
        }
        return null;
    }

    /**
     * 領域内の「外気に開いた細長い連結路」(軒下ストリップ・デッキ下の通り) を検出して返す。
     *
     * <p>各セルについて、X方向とZ方向の領域内連続長を測り、どちらかが2以下のセルを
     * 「細い断面」とする。細い断面のセルを6近傍で連結したチェーンのうち、
     * バウンディングボックスの最大辺が {@link #THIN_SEVER_MIN_EXTENT} 以上で、かつ
     * 外気 (領域外の非固体) に面したセルを含むものだけが対象。
     *
     * <p>切断はチェーン全体ではなく {@link #SEVER_CUT_DEPTH} 内の外気側の縁に限る。
     * これにより通り沿いのストリップは除去されるが、チェーンで繋がった室内側の
     * 細い部屋・階段・廊下は領域に残る (露天の縁だけを切る)。
     * 完全に屋根で覆われた3幅以上の通路は検出しない (「部屋」とみなす)。
     */
    private static LongOpenHashSet detectLongThinChains(LongOpenHashSet region, BlockMap blockMap) {
        final LongOpenHashSet thin = new LongOpenHashSet(region.size());
        for (long cell : region) {
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            if (axisRunLength(region, x, y, z, Direction.Axis.X) <= 2
                    || axisRunLength(region, x, y, z, Direction.Axis.Z) <= 2) {
                thin.add(cell);
            }
        }
        if (thin.isEmpty()) {
            return new LongOpenHashSet();
        }
        final LongOpenHashSet severed = new LongOpenHashSet();
        final LongOpenHashSet visited = new LongOpenHashSet(thin.size());
        final LongOpenHashSet openSeeds = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();
        for (long cell : thin) {
            if (!visited.add(cell)) {
                continue;
            }
            queue.clear();
            openSeeds.clear();
            queue.add(cell);
            int minX = BlockPos.getX(cell);
            int maxX = minX;
            int minY = BlockPos.getY(cell);
            int maxY = minY;
            int minZ = BlockPos.getZ(cell);
            int maxZ = minZ;
            int head = 0;
            while (head < queue.size()) {
                long cur = queue.getLong(head++);
                int cx = BlockPos.getX(cur);
                int cy = BlockPos.getY(cur);
                int cz = BlockPos.getZ(cur);
                if (cx < minX) minX = cx;
                if (cy < minY) minY = cy;
                if (cz < minZ) minZ = cz;
                if (cx > maxX) maxX = cx;
                if (cy > maxY) maxY = cy;
                if (cz > maxZ) maxZ = cz;
                if (facesOpenAir(region, blockMap, cx, cy, cz)) {
                    openSeeds.add(cur);
                }
                for (Direction dir : DIRECTIONS) {
                    long n = BlockPos.asLong(cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                    if (thin.contains(n) && visited.add(n)) {
                        queue.add(n);
                    }
                }
            }
            int extent = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ) + 1;
            if (extent < THIN_SEVER_MIN_EXTENT || openSeeds.isEmpty()) {
                continue;
            }
            // 外気側の縁から SEVER_CUT_DEPTH 内だけを切り離す
            severed.addAll(openSeeds);
            LongOpenHashSet frontier = openSeeds;
            for (int depth = 0; depth < SEVER_CUT_DEPTH; depth++) {
                final LongOpenHashSet next = new LongOpenHashSet();
                for (long cur : frontier) {
                    int cx = BlockPos.getX(cur);
                    int cy = BlockPos.getY(cur);
                    int cz = BlockPos.getZ(cur);
                    for (Direction dir : DIRECTIONS) {
                        long n = BlockPos.asLong(cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                        if (thin.contains(n) && !severed.contains(n) && next.add(n)) {
                            severed.add(n);
                        }
                    }
                }
                frontier = next;
            }
        }
        return severed;
    }

    /**
     * セルが外気 (領域外の非固体 = 覆いのない空気・露出除外済みセル) に水平で面しているか。
     * 固体の隣接は開気ではない。
     */
    private static boolean facesOpenAir(LongSet region, BlockMap blockMap, int x, int y, int z) {
        for (Direction dir : DIRECTIONS) {
            if (dir.getStepY() != 0) {
                continue;
            }
            int nx = x + dir.getStepX();
            int nz = z + dir.getStepZ();
            if (!region.contains(BlockPos.asLong(nx, y, nz)) && !blockMap.isSolid(nx, y, nz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定軸方向の領域内連続長を測る。両方向合わせて3セルを超えた時点で打ち切る
     * (しきい値「2以下」の判定に十分なため)。
     */
    private static int axisRunLength(LongOpenHashSet region, int x, int y, int z, Direction.Axis axis) {
        int dx = axis == Direction.Axis.X ? 1 : 0;
        int dz = axis == Direction.Axis.Z ? 1 : 0;
        int len = 1;
        for (int step = 1; step <= 2; step++) {
            if (region.contains(BlockPos.asLong(x + dx * step, y, z + dz * step))) {
                len++;
            } else {
                break;
            }
        }
        for (int step = 1; step <= 2; step++) {
            if (region.contains(BlockPos.asLong(x - dx * step, y, z - dz * step))) {
                len++;
            } else {
                break;
            }
        }
        return len;
    }

    /**
     * 種の所属する連結コンポーネント以外を領域から取り除く。
     * 分断された領域 (橋で切り離された相手側の家) が天井スライスのAABBに
     * 取り込まれるのを防ぐ。
     */
    private static void reduceToSeedComponent(LongOpenHashSet region, long startLong) {
        LongOpenHashSet keep = new LongOpenHashSet(region.size());
        collectChain(region, startLong, keep);
        if (keep.size() < region.size()) {
            region.retainAll(keep);
        }
    }

    /** {@code from} から6近傍BFSで到達できる {@code source} 内のセルを {@code out} に集める。 */
    private static void collectChain(LongSet source, long from, LongOpenHashSet out) {
        LongArrayList queue = new LongArrayList();
        out.add(from);
        queue.add(from);
        int head = 0;
        while (head < queue.size()) {
            long cur = queue.getLong(head++);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : DIRECTIONS) {
                long n = BlockPos.asLong(cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                if (source.contains(n) && out.add(n)) {
                    queue.add(n);
                }
            }
        }
    }

    /**
     * 指定位置の直上 {@link #CEILING_SCAN_HEIGHT} 以内に固体の覆い(天井)があるか判定する。
     * 空気・葉・液体は覆いとみなさない(透過)。見つからなかったセルは記録して再走査を避ける。
     */
    private static boolean isCovered(Scratch s, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (s.noCover.contains(key)) {
            return false;
        }
        final int limit = y + CEILING_SCAN_HEIGHT;
        for (int yy = y + 1; yy <= limit; yy++) {
            if (s.blockMap.isCover(x, yy, z)) {
                return true;
            }
        }
        s.noCover.add(key);
        return false;
    }

    /**
     * 種の足元Y帯の断面が外に開いているかを2手法で直検する。
     *
     * <p>全域の開放率判定は領域内の壁面総数で希釈されるため、次の2つを併用する:
     * <ol>
     *   <li><b>断面BFS</b> — 種から足元+頭の2層を辿り (扉・ゲートは壁として扱い辿らない)、
     *       辿った範囲が「領域外の非固体」(開けた空気・露出除外済みセル) に面する割合が
     *       閾値を超えれば開放。軒下リングに立っているケースを検出する。</li>
     *   <li><b>巨大天蓋サンプル</b> — 領域セルの直上の覆いまでの垂直距離が人間スケールを
     *       超える割合が高ければ、巨大な覆いの下とみなす。</li>
     * </ol>
     */
    private static boolean hasLocalOpening(Scratch s, BlockPos seed, LongSet region) {
        return isLocalCrossSectionOpen(s, seed, region) || isGiantCanopyNearby(s, seed, region);
    }

    /**
     * 種を中心に足元+頭の2層で断面BFSを行い、外向きの開放面が壁面に対して優過ぎないか検査する。
     *
     * <p>領域外の非固体セル = 開けた空気 (露出除外で切り離された軒下の外周を含む)。
     * 半径打ち切りの面は、外が領域の継続なら開放面に数えない。扉・フェンスゲート
     * (連結部) は壁として扱い、辿らない。これにより屋内側の壁面が開放面を希釈するのを防ぐ。
     */
    private static boolean isLocalCrossSectionOpen(Scratch s, BlockPos seed, LongSet region) {
        final int radius = LOCAL_SECTION_RADIUS;
        final int minY = seed.getY();
        final int maxY = minY + LOCAL_SECTION_HEIGHT;
        final int seedX = seed.getX();
        final int seedZ = seed.getZ();

        long startLong = seed.asLong();
        if (!region.contains(startLong)) {
            return false;
        }
        final LongOpenHashSet local = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();
        local.add(startLong);
        queue.add(startLong);

        int wallFaces = 0;
        int openFaces = 0;
        int head = 0;
        while (head < queue.size()) {
            long cur = queue.getLong(head++);
            int cx = BlockPos.getX(cur);
            int cy = BlockPos.getY(cur);
            int cz = BlockPos.getZ(cur);
            for (Direction dir : DIRECTIONS) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();
                if (ny < minY || ny >= maxY) {
                    // 帯外 (上下階) は別断面のため中立
                    continue;
                }
                if (Math.abs(nx - seedX) > radius || Math.abs(nz - seedZ) > radius) {
                    // 半径打ち切り。外が領域の継続なら開放面ではない
                    continue;
                }
                long nlong = BlockPos.asLong(nx, ny, nz);
                if (s.blockMap.isSolid(nx, ny, nz) || s.blockMap.isConnector(nx, ny, nz)) {
                    wallFaces++;
                    continue;
                }
                if (region.contains(nlong)) {
                    if (local.add(nlong)) {
                        queue.add(nlong);
                    }
                    continue;
                }
                // 領域外の非固体 = 開けた空気 (露出除外セルを含む)
                openFaces++;
            }
        }
        if (local.size() < LOCAL_MIN_SAMPLES) {
            return false;
        }
        return openFaces * 100 > wallFaces * MAX_OPEN_FACE_PERCENT;
    }

    /**
     * 種の足元Y帯の断面サンプルで「非人間スケールの天蓋」を検出する。
     *
     * <p>巨大な覆い (祠・巨大菌の傘・岩屋根) の下では全空気が covered air として氾濫し、
     * 全域の開放率判定は地形や柱による壁面の希釈で屋内側に倒れる。種の足元近傍にある
     * 領域セルだけをサンプルし、各セルの直上の覆いまでの垂直距離を測る。
     * 覆いが {@link #LOCAL_MAX_COVER_HEIGHT} より遠い (または無い) セルが多くを占める
     * 断面は巨大天蓋の下とみなし、屋内判定を落とす。領域外のセルは母集団から除外するため、
     * 窓・扉際の外気はサンプルに混入しない。
     */
    private static boolean isGiantCanopyNearby(Scratch s, BlockPos seed, LongSet region) {
        final int radius = LOCAL_SECTION_RADIUS;
        final int minY = seed.getY();
        final int maxY = minY + LOCAL_SECTION_HEIGHT;
        final int seedX = seed.getX();
        final int seedZ = seed.getZ();

        int near = 0;
        int far = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y < maxY; y++) {
                    long pos = BlockPos.asLong(seedX + dx, y, seedZ + dz);
                    if (!region.contains(pos)) {
                        continue;
                    }
                    if (s.blockMap.isSolid(seedX + dx, y, seedZ + dz)) {
                        continue;
                    }
                    if (firstCoverDistance(s, seedX + dx, y, seedZ + dz)
                            <= LOCAL_MAX_COVER_HEIGHT) {
                        near++;
                    } else {
                        far++;
                    }
                }
            }
        }
        int total = near + far;
        if (total < LOCAL_MIN_SAMPLES) {
            return false;
        }
        return near * 100 < total * LOCAL_MIN_COVERED_PERCENT;
    }

    /**
     * 直上の最初の覆いまでの垂直距離を返す。覆いがない場合は {@code Integer.MAX_VALUE}。
     * 空気・葉・液体は透過する。覆いが全くないセルは {@code Scratch.noCover} でメモ化する。
     */
    private static int firstCoverDistance(Scratch s, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (s.noCover.contains(key)) {
            return Integer.MAX_VALUE;
        }
        final int limit = y + CEILING_SCAN_HEIGHT;
        for (int yy = y + 1; yy <= limit; yy++) {
            if (s.blockMap.isCover(x, yy, z)) {
                return yy - y;
            }
        }
        s.noCover.add(key);
        return Integer.MAX_VALUE;
    }

    /**
     * 部屋空間探索結果オブジェクト。
     */
    public static final class Result {

        public static final Result EMPTY = new Result(
                false,
                LongSets.EMPTY_SET,
                LongSets.EMPTY_SET,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO
        );

        private final boolean enclosed;
        private final LongSet airCells;
        private final LongSet shellCells;
        private final BlockPos minPos;
        private final BlockPos maxPos;
        private final BlockPos seed;

        public Result(boolean enclosed, LongSet airCells, LongSet shellCells,
                      BlockPos minPos, BlockPos maxPos, BlockPos seed) {
            this.enclosed = enclosed;
            this.airCells = LongSets.unmodifiable(Objects.requireNonNull(airCells));
            this.shellCells = LongSets.unmodifiable(Objects.requireNonNull(shellCells));
            this.minPos = minPos.immutable();
            this.maxPos = maxPos.immutable();
            this.seed = seed.immutable();
        }

        /** 屋内閉空間と判定されたか */
        public boolean isEnclosed() {
            return enclosed;
        }

        /** 部屋内部の空気セル集合 (packed long) */
        public LongSet getAirCells() {
            return airCells;
        }

        /** 部屋を囲む壁殻セル集合 (packed long) */
        public LongSet getShellCells() {
            return shellCells;
        }

        /** 部屋のバウンディングボックス最小座標 */
        public BlockPos getMinPos() {
            return minPos;
        }

        /** 部屋のバウンディングボックス最大座標 */
        public BlockPos getMaxPos() {
            return maxPos;
        }

        /** 探索の起点座標 */
        public BlockPos getSeed() {
            return seed;
        }

        /** 天井が存在する最大Y座標 (AABBのmaxY) */
        public int getCeilingY() {
            return enclosed ? maxPos.getY() : SpaceProbe.NO_CEILING;
        }
    }
}
