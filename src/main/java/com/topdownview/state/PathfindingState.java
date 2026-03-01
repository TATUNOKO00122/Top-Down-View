package com.topdownview.state;

// import com.topdownview.Config;
import com.topdownview.pathfinding.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PathfindingState {

    public static final PathfindingState INSTANCE = new PathfindingState();
    private static final int DEFAULT_RECALC_COOLDOWN = 20; // Hardcoded since config is disabled

    private Path currentPath = null;
    private Vec3 avoidanceVector = Vec3.ZERO;
    private List<Entity> nearbyEntities = List.of();
    private int recalcCooldown = 0;
    private int pathIndex = 0;
    private boolean pathRequested = false;

    private PathfindingState() {}

    @Nullable
    public Path getCurrentPath() {
        return currentPath;
    }

    public boolean hasPath() {
        return currentPath != null && !currentPath.isEmpty();
    }

    public boolean isPathFinished() {
        return currentPath == null || currentPath.isFinished();
    }

    public Vec3 getAvoidanceVector() {
        return avoidanceVector;
    }

    public List<Entity> getNearbyEntities() {
        return nearbyEntities;
    }

    public boolean canRecalculate() {
        return recalcCooldown <= 0;
    }

    public boolean isPathRequested() {
        return pathRequested;
    }

    public void setCurrentPath(@Nullable Path path) {
        this.currentPath = path;
        this.pathIndex = 0;
        this.pathRequested = false;
    }

    public void setAvoidanceVector(Vec3 vector) {
        if (vector != null && (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z))) {
            this.avoidanceVector = Vec3.ZERO;
            return;
        }
        this.avoidanceVector = vector != null ? vector : Vec3.ZERO;
    }

    public void setNearbyEntities(List<Entity> entities) {
        this.nearbyEntities = entities != null ? entities : List.of();
    }

    public void setPathRequested(boolean requested) {
        this.pathRequested = requested;
    }

    public void tickCooldown() {
        if (recalcCooldown > 0) {
            recalcCooldown--;
        }
    }

    public void startRecalcCooldown() {
        this.recalcCooldown = DEFAULT_RECALC_COOLDOWN; // Config.pathRecalcCooldown;
    }

    public void advancePath() {
        if (currentPath != null) {
            currentPath.advance();
        }
    }

    @Nullable
    public Vec3 getCurrentWaypoint() {
        if (currentPath == null || currentPath.isFinished()) {
            return null;
        }
        return currentPath.getCurrentNodePosition();
    }

    @Nullable
    public Vec3 getNextWaypoint() {
        if (currentPath == null) {
            return null;
        }
        int nextIndex = currentPath.getCurrentNodeIndex() + 1;
        return currentPath.getNodePosition(nextIndex);
    }

    public void reset() {
        currentPath = null;
        avoidanceVector = Vec3.ZERO;
        nearbyEntities = List.of();
        recalcCooldown = 0;
        pathIndex = 0;
        pathRequested = false;
    }

    public void clearPath() {
        currentPath = null;
        pathIndex = 0;
        pathRequested = false;
    }
}
