package com.topdownview.pathfinding;

import net.minecraft.core.BlockPos;

public final class PathNode {
    public final int x;
    public final int y;
    public final int z;
    
    public float gCost;
    public float hCost;
    public float fCost;
    
    public PathNode parent;
    public boolean closed;
    
    public PathNode(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public BlockPos getBlockPos() {
        return new BlockPos(x, y, z);
    }
    
    public void calculateFCost() {
        fCost = gCost + hCost;
    }
    
    public float getDistanceTo(PathNode other) {
        float dx = other.x - x;
        float dy = other.y - y;
        float dz = other.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    public float getManhattanDistanceTo(PathNode other) {
        return Math.abs(other.x - x) + Math.abs(other.y - y) + Math.abs(other.z - z);
    }
    
    public int getHeapWeight() {
        return (int) (fCost * 1000);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PathNode other)) return false;
        return x == other.x && y == other.y && z == other.z;
    }
    
    @Override
    public int hashCode() {
        return x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }
    
    @Override
    public String toString() {
        return String.format("PathNode(%d, %d, %d)", x, y, z);
    }
}
