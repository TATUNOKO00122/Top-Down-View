package com.topdownview.client;

import com.topdownview.util.RayAabb;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * カーソル下のドロップアイテムをマウスレイから探す共通処理。
 * ラベル表示とクリック取得の両方から利用する。
 */
public final class ItemPickupHelper {

    private static final double HOVER_REACH = 64.0D;
    private static final double HITBOX_INFLATE = 0.05D;
    private static final double[] RAY_RANGE = new double[2];

    private ItemPickupHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** カーソル下にある最も手前のドロップアイテムを返す。無ければ null。 */
    public static ItemEntity findHoveredItem(Minecraft mc, Vec3 cameraPos) {
        if (mc.level == null) {
            return null;
        }

        Vec3 direction = MouseRaycast.INSTANCE.getMouseRayDirection(mc, mc.getFrameTime());
        if (direction == null) {
            return null;
        }

        // カーソル下のブロックより奥にあるアイテムは遮蔽されているものとして除外する
        double maxDistance = HOVER_REACH;
        HitResult hit = MouseRaycast.INSTANCE.getLastHitResult();
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            double blockDistance = cameraPos.distanceTo(hit.getLocation());
            if (blockDistance < maxDistance) {
                maxDistance = blockDistance;
            }
        }

        Vec3 end = cameraPos.add(direction.scale(maxDistance));
        AABB searchBox = new AABB(cameraPos, end).inflate(1.0D);

        ItemEntity closest = null;
        double closestDistance = maxDistance;
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
            if (item.getItem().isEmpty()) {
                continue;
            }
            double t = rayIntersect(cameraPos, direction, item.getBoundingBox().inflate(HITBOX_INFLATE));
            if (t < 0.0D || t >= closestDistance) {
                continue;
            }
            closestDistance = t;
            closest = item;
        }
        return closest;
    }

    private static double rayIntersect(Vec3 origin, Vec3 direction, AABB box) {
        double[] range = RAY_RANGE;
        range[0] = 0.0D;
        range[1] = Double.MAX_VALUE;
        if (!RayAabb.clip(origin.x, direction.x, box.minX, box.maxX, range)) {
            return -1.0D;
        }
        if (!RayAabb.clip(origin.y, direction.y, box.minY, box.maxY, range)) {
            return -1.0D;
        }
        if (!RayAabb.clip(origin.z, direction.z, box.minZ, box.maxZ, range)) {
            return -1.0D;
        }
        return range[0] >= 0.0D ? range[0] : range[1];
    }
}
