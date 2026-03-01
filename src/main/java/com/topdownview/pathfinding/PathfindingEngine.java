package com.topdownview.pathfinding;

import com.topdownview.Config;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class PathfindingEngine {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int[] NEIGHBOR_OFFSETS = {
        1, 0, 0,
        -1, 0, 0,
        0, 0, 1,
        0, 0, -1,
        1, 0, 1,
        1, 0, -1,
        -1, 0, 1,
        -1, 0, -1,
        1, 1, 0,
        -1, 1, 0,
        0, 1, 1,
        0, 1, -1,
        1, -1, 0,
        -1, -1, 0,
        0, -1, 1,
        0, -1, -1
    };
    
    private static final float CARDINAL_COST = 1.0f;
    private static final float DIAGONAL_COST = 1.414f;
    private static final float JUMP_COST = 1.5f;
    private static final float DROP_COST = 0.8f;
    
    private PathfindingEngine() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    @Nullable
    public static Path findPath(BlockGetter level, Vec3 startPos, Vec3 targetPos, int maxRange) {
        BlockPos startBlockPos = BlockPos.containing(startPos.x, startPos.y, startPos.z);
        BlockPos targetBlockPos = BlockPos.containing(targetPos.x, targetPos.y, targetPos.z);
        
        LOGGER.info("[Pathfinding] 開始: {} -> {}", startBlockPos, targetBlockPos);
        
        BlockPos adjustedStart = findNearestWalkable(level, startBlockPos, 3);
        BlockPos adjustedTarget = findNearestWalkable(level, targetBlockPos, 5);
        
        if (adjustedStart == null) {
            LOGGER.warn("[Pathfinding] 開始地点が歩行不可能: {}", startBlockPos);
            return null;
        }
        if (adjustedTarget == null) {
            LOGGER.warn("[Pathfinding] 目的地が歩行不可能: {}", targetBlockPos);
            return null;
        }
        
        LOGGER.info("[Pathfinding] 調整後: {} -> {}", adjustedStart, adjustedTarget);
        
        Path path = findPathToBlock(level, adjustedStart, adjustedTarget, maxRange);
        
        if (path != null && !path.isEmpty()) {
            LOGGER.info("[Pathfinding] パス生成成功: {}ノード, 到達可能={}", 
                path.getLength(), path.reachesTarget());
        } else {
            LOGGER.warn("[Pathfinding] パス生成失敗");
        }
        
        return path;
    }
    
    @Nullable
    private static Path findPathToBlock(BlockGetter level, BlockPos start, BlockPos target, int maxRange) {
        if (start.distManhattan(target) > maxRange) {
            return null;
        }
        
        Long2ObjectOpenHashMap<PathNode> allNodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(
            Comparator.comparingDouble(n -> n.fCost)
        );
        
        PathNode startNode = getOrCreateNode(allNodes, start.getX(), start.getY(), start.getZ());
        startNode.gCost = 0;
        startNode.hCost = heuristic(startNode, target);
        startNode.calculateFCost();
        openSet.add(startNode);
        
        PathNode targetNode = getOrCreateNode(allNodes, target.getX(), target.getY(), target.getZ());
        
        int maxIterations = maxRange * maxRange * 4;
        int iterations = 0;
        
        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            
            PathNode current = openSet.poll();
            current.closed = true;
            
            if (current.equals(targetNode)) {
                return reconstructPath(current, target, true);
            }
            
            if (current.getManhattanDistanceTo(targetNode) <= 1) {
                return reconstructPath(current, target, true);
            }
            
            List<PathNode> neighbors = getNeighbors(level, allNodes, current, maxRange, start);
            for (PathNode neighbor : neighbors) {
                if (neighbor.closed) continue;
                
                float tentativeG = current.gCost + getMovementCost(current, neighbor);
                
                if (tentativeG < neighbor.gCost) {
                    neighbor.parent = current;
                    neighbor.gCost = tentativeG;
                    neighbor.hCost = heuristic(neighbor, target);
                    neighbor.calculateFCost();
                    
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        
        PathNode closestNode = findClosestNode(allNodes, target);
        if (closestNode != null && closestNode.parent != null) {
            return reconstructPath(closestNode, target, false);
        }
        
        return null;
    }
    
    private static PathNode getOrCreateNode(Long2ObjectOpenHashMap<PathNode> nodes, int x, int y, int z) {
        long key = encodePos(x, y, z);
        PathNode node = nodes.get(key);
        if (node == null) {
            node = new PathNode(x, y, z);
            node.gCost = Float.MAX_VALUE;
            nodes.put(key, node);
        }
        return node;
    }
    
    private static long encodePos(int x, int y, int z) {
        return ((long) x & 0xFFFFFFL) | (((long) y & 0xFFFFL) << 24) | (((long) z & 0xFFFFFFL) << 40);
    }
    
    private static float heuristic(PathNode node, BlockPos target) {
        int dx = Math.abs(node.x - target.getX());
        int dy = Math.abs(node.y - target.getY());
        int dz = Math.abs(node.z - target.getZ());
        
        int min = Math.min(Math.min(dx, dy), dz);
        int max = Math.max(Math.max(dx, dy), dz);
        int mid = dx + dy + dz - min - max;
        
        return DIAGONAL_COST * min + CARDINAL_COST * (mid - min) + max;
    }
    
    private static List<PathNode> getNeighbors(BlockGetter level, Long2ObjectOpenHashMap<PathNode> nodes, 
                                                PathNode current, int maxRange, BlockPos start) {
        List<PathNode> neighbors = new ArrayList<>(16);
        
        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i += 3) {
            int dx = NEIGHBOR_OFFSETS[i];
            int dy = NEIGHBOR_OFFSETS[i + 1];
            int dz = NEIGHBOR_OFFSETS[i + 2];
            
            int nx = current.x + dx;
            int ny = current.y + dy;
            int nz = current.z + dz;
            
            if (Math.abs(nx - start.getX()) > maxRange || 
                Math.abs(nz - start.getZ()) > maxRange) {
                continue;
            }
            
            BlockPos neighborPos = new BlockPos(nx, ny, nz);
            
            if (!isWalkableNeighbor(level, current, nx, ny, nz)) {
                continue;
            }
            
            neighbors.add(getOrCreateNode(nodes, nx, ny, nz));
        }
        
        return neighbors;
    }
    
    private static boolean isWalkableNeighbor(BlockGetter level, PathNode current, int nx, int ny, int nz) {
        BlockPos feetPos = new BlockPos(nx, ny, nz);
        BlockPos groundPos = feetPos.below();
        
        int dy = ny - current.y;
        
        if (dy > 1 || dy < -3) {
            return false;
        }
        
        if (dy > 0) {
            BlockPos jumpFromPos = new BlockPos(current.x, current.y, current.z);
            BlockPos aboveJumpPos = jumpFromPos.above();
            if (!WalkableChecker.isPassable(level.getBlockState(aboveJumpPos), level, aboveJumpPos)) {
                return false;
            }
        }
        
        if (!WalkableChecker.isWalkable(level, feetPos)) {
            if (!WalkableChecker.canStandAt(level, groundPos)) {
                BlockPos belowGround = groundPos.below();
                if (!WalkableChecker.canStandAt(level, belowGround)) {
                    return false;
                }
                if (!WalkableChecker.isWalkable(level, feetPos.below())) {
                    return false;
                }
            } else if (!WalkableChecker.isWalkable(level, feetPos)) {
                return false;
            }
        }
        
        return true;
    }
    
    private static float getMovementCost(PathNode from, PathNode to) {
        int dx = Math.abs(to.x - from.x);
        int dy = Math.abs(to.y - from.y);
        int dz = Math.abs(to.z - from.z);
        
        float cost = CARDINAL_COST;
        
        if (dx + dz > 1) {
            cost = DIAGONAL_COST;
        }
        
        if (dy > 0) {
            cost += JUMP_COST * dy;
        } else if (dy < 0) {
            cost += DROP_COST * Math.abs(dy);
        }
        
        return cost;
    }
    
    private static Path reconstructPath(PathNode endNode, BlockPos target, boolean reachesTarget) {
        List<PathNode> nodes = new ArrayList<>();
        PathNode current = endNode;
        
        while (current != null) {
            nodes.add(0, current);
            current = current.parent;
        }
        
        return new Path(nodes, target, reachesTarget);
    }
    
    @Nullable
    private static PathNode findClosestNode(Long2ObjectOpenHashMap<PathNode> nodes, BlockPos target) {
        PathNode closest = null;
        float closestDist = Float.MAX_VALUE;
        
        for (PathNode node : nodes.values()) {
            if (node.parent == null) continue;
            
            float dist = node.getManhattanDistanceTo(new PathNode(target.getX(), target.getY(), target.getZ()));
            if (dist < closestDist) {
                closestDist = dist;
                closest = node;
            }
        }
        
        return closest;
    }
    
    @Nullable
    private static BlockPos findNearestWalkable(BlockGetter level, BlockPos pos, int searchRadius) {
        if (WalkableChecker.isWalkable(level, pos)) {
            return pos;
        }
        
        for (int y = 0; y <= searchRadius; y++) {
            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    if (WalkableChecker.isWalkable(level, checkPos)) {
                        return checkPos;
                    }
                    
                    checkPos = pos.offset(x, -y, z);
                    if (y > 0 && WalkableChecker.isWalkable(level, checkPos)) {
                        return checkPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    public static BlockPos findNearestWalkableToTarget(BlockGetter level, BlockPos pos, BlockPos target, int searchRadius) {
        if (WalkableChecker.isWalkable(level, pos)) {
            return pos;
        }
        
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        
        for (int y = -searchRadius; y <= searchRadius; y++) {
            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    if (WalkableChecker.isWalkable(level, checkPos)) {
                        double dist = checkPos.distSqr(target);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = checkPos;
                        }
                    }
                }
            }
        }
        
        return best;
    }
}
