package com.topdownview.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class Path {
    private final List<PathNode> nodes;
    private int currentNodeIndex;
    private final BlockPos target;
    private final boolean reachesTarget;
    
    public Path(List<PathNode> nodes, BlockPos target, boolean reachesTarget) {
        this.nodes = new ArrayList<>(nodes);
        this.target = target;
        this.reachesTarget = reachesTarget;
        this.currentNodeIndex = 0;
    }
    
    public static Path empty() {
        return new Path(Collections.emptyList(), BlockPos.ZERO, false);
    }
    
    public boolean isEmpty() {
        return nodes.isEmpty();
    }
    
    public boolean isFinished() {
        return currentNodeIndex >= nodes.size();
    }
    
    public int getLength() {
        return nodes.size();
    }
    
    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }
    
    public void advance() {
        if (currentNodeIndex < nodes.size()) {
            currentNodeIndex++;
        }
    }
    
    @Nullable
    public PathNode getCurrentNode() {
        if (currentNodeIndex >= 0 && currentNodeIndex < nodes.size()) {
            return nodes.get(currentNodeIndex);
        }
        return null;
    }
    
    @Nullable
    public PathNode getNextNode() {
        int nextIndex = currentNodeIndex + 1;
        if (nextIndex >= 0 && nextIndex < nodes.size()) {
            return nodes.get(nextIndex);
        }
        return null;
    }
    
    @Nullable
    public PathNode getEndNode() {
        if (nodes.isEmpty()) return null;
        return nodes.get(nodes.size() - 1);
    }
    
    public BlockPos getTarget() {
        return target;
    }
    
    public boolean reachesTarget() {
        return reachesTarget;
    }
    
    public Vec3 getCurrentNodePosition() {
        PathNode node = getCurrentNode();
        if (node == null) return null;
        return new Vec3(node.x + 0.5, node.y, node.z + 0.5);
    }
    
    public Vec3 getNodePosition(int index) {
        if (index < 0 || index >= nodes.size()) return null;
        PathNode node = nodes.get(index);
        return new Vec3(node.x + 0.5, node.y, node.z + 0.5);
    }
    
    public List<BlockPos> getNodePositions() {
        List<BlockPos> positions = new ArrayList<>(nodes.size());
        for (PathNode node : nodes) {
            positions.add(node.getBlockPos());
        }
        return positions;
    }
    
    public void setCurrentNodeIndex(int index) {
        this.currentNodeIndex = Math.max(0, Math.min(index, nodes.size() - 1));
    }
    
    public float getTotalLength() {
        if (nodes.size() < 2) return 0;
        float total = 0;
        for (int i = 1; i < nodes.size(); i++) {
            total += nodes.get(i - 1).getDistanceTo(nodes.get(i));
        }
        return total;
    }
}
