package com.topdownview.pathfinding;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * ローカル回避エンジン
 * 現在は無効化されている機能
 */
public final class LocalAvoidanceEngine {
    
    private LocalAvoidanceEngine() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    /**
     * 回避ベクトルを計算
     * 現在は無効化 - 常にdesiredVelocityをそのまま返す
     */
    public static Vec3 calculateAvoidanceVector(Vec3 playerPos, Vec3 desiredVelocity, List<Entity> nearbyEntities) {
        return desiredVelocity;
    }
    
    /**
     * シンプルな回避ベクトル計算
     * 現在は無効化 - 常にtargetPos方向の正規化ベクトルを返す
     */
    public static Vec3 calculateAvoidanceVectorSimple(Vec3 playerPos, Vec3 targetPos, List<Entity> nearbyEntities) {
        return targetPos.subtract(playerPos).normalize();
    }
    
    /**
     * 回避が必要かどうかを判定
     * 現在は無効化 - 常にfalseを返す
     */
    public static boolean needsAvoidance(Vec3 playerPos, Vec3 moveDir, List<Entity> nearbyEntities) {
        return false;
    }
}
