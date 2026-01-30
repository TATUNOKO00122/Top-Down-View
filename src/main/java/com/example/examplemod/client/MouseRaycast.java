package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * トップダウン視点用のマウスレイキャスト処理
 * カメラは固定角度（ピッチ60度、ヨー0度）であることを前提とする
 */
public class MouseRaycast {

    // カスタムリーチ距離（ハイライトと操作用）
    public static final double CUSTOM_REACH_DISTANCE = 100.0D;

    // キャッシュ：最後のブロックヒット結果（ハイライト用）
    private static BlockHitResult lastBlockHit = null;
    private static EntityHitResult lastEntityHit = null;
    private static net.minecraft.world.phys.HitResult lastHitResult = null;

    /**
     * 毎フレーム呼び出して、レイキャスト結果をキャッシュする
     */
    public static void update(Minecraft mc, float partialTick, double reachDistance) {
        if (mc.level == null || mc.player == null) {
            lastBlockHit = null;
            lastEntityHit = null;
            lastHitResult = null;
            return;
        }

        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        // 1. ブロックヒット（通常のレイキャスト）
        lastBlockHit = mc.level
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double blockDist = lastBlockHit.getLocation().distanceToSqr(start);

        // 2. エンティティヒット
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity != null) {
            // ブロックまでの距離を上限にエンティティを探索
            Vec3 entitySearchEnd = lastBlockHit.getLocation();
            // MISSの場合は最大距離まで
            if (lastBlockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                entitySearchEnd = end;
                blockDist = reachDistance * reachDistance;
            }

            AABB searchBox = new AABB(start, entitySearchEnd).inflate(1.0D);
            lastEntityHit = ProjectileUtil.getEntityHitResult(
                    mc.level,
                    cameraEntity,
                    start,
                    entitySearchEnd,
                    searchBox,
                    (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);
        } else {
            lastEntityHit = null;
        }

        // 3. 最終的なベストヒットを決定
        if (lastEntityHit != null) {
            double entityDist = start.distanceToSqr(lastEntityHit.getLocation());
            if (entityDist < blockDist) {
                lastHitResult = lastEntityHit;
            } else {
                lastHitResult = lastBlockHit;
            }
        } else {
            lastHitResult = lastBlockHit;
        }
    }

    public static net.minecraft.world.phys.HitResult getLastHitResult() {
        return lastHitResult;
    }

    public static BlockHitResult getLastBlockHit() {
        return lastBlockHit;
    }

    public static EntityHitResult getLastEntityHit() {
        return lastEntityHit;
    }

    /**
     * マウスカーソル位置からワールド空間へのレイキャストを行い、ヒットしたブロックの結果を返す。
     */
    public static BlockHitResult rayTraceBlock(Minecraft mc, float partialTick, double reachDistance) {
        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    /**
     * マウスカーソル位置からワールド空間へのレイキャストを行い、ヒットしたエンティティの結果を返す。
     */
    public static EntityHitResult rayTraceEntity(Minecraft mc, float partialTick, double reachDistance) {
        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null)
            return null;

        AABB searchBox = new AABB(start, end).inflate(1.0D);

        return ProjectileUtil.getEntityHitResult(
                mc.level,
                cameraEntity,
                start,
                end,
                searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);
    }

    public static net.minecraft.world.phys.HitResult getHitResult(Minecraft mc, float partialTick,
            double reachDistance) {
        Vec3 start = getCameraPosition(mc);
        Vec3 direction = getMouseRayDirection(mc);
        Vec3 end = start.add(direction.scale(reachDistance));

        // 1. ブロックのレイキャスト
        BlockHitResult blockHit = mc.level
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double blockDist = blockHit.getLocation().distanceToSqr(start);

        // 2. エンティティのレイキャスト
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null)
            return blockHit;

        // ブロックまでの距離を上限にエンティティを探索
        Vec3 entitySearchEnd = blockHit.getLocation();
        // ブロックにヒットしていない(MISS)場合は、最大距離まで探索
        if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            entitySearchEnd = end;
            blockDist = reachDistance * reachDistance;
        }

        AABB searchBox = new AABB(start, entitySearchEnd).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.level,
                cameraEntity,
                start,
                entitySearchEnd,
                searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable() && entity != mc.player);

        if (entityHit != null) {
            double entityDist = start.distanceToSqr(entityHit.getLocation());
            if (entityDist < blockDist) {
                return entityHit;
            }
        }

        return blockHit;
    }

    private static Vec3 getCameraPosition(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    /**
     * トップダウン視点用のシンプルなレイ方向計算
     */
    private static Vec3 getMouseRayDirection(Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        // 画面中心からの相対位置（-1.0 ～ 1.0）
        // X軸のみ符号反転（左右の調整）
        // Y軸は反転しない（画面上=mouseY小=ndcY負=前方）
        double ndcX = -((2.0 * mouseX / screenWidth) - 1.0);
        double ndcY = ((2.0 * mouseY / screenHeight) - 1.0);

        // カメラのFOV（視野角）を考慮
        double fov = mc.options.fov().get();
        double aspectRatio = (double) screenWidth / screenHeight;
        double fovRad = Math.toRadians(fov);
        double tanHalfFov = Math.tan(fovRad / 2.0);

        // 視野角に基づいてオフセットを計算
        double offsetX = ndcX * tanHalfFov * aspectRatio;
        double offsetY = ndcY * tanHalfFov;

        // カメラの向き（ピッチ60度、ヨー0度）
        double pitchDeg = 60.0;
        double yawDeg = 0.0;
        double pitch = Math.toRadians(pitchDeg);
        double yaw = Math.toRadians(yawDeg);

        // カメラの前方向（ワールド座標）
        // Minecraftの座標系: Y=上, Z=南(正), X=東(正)
        // ピッチ: 正=下向き, ヨー: 0=南
        double forwardX = -Math.sin(yaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * Math.cos(pitch);

        // カメラの右方向（水平面上）
        double rightX = Math.cos(yaw);
        double rightY = 0;
        double rightZ = Math.sin(yaw);

        // カメラの上方向（カメラローカルの上、ピッチを考慮）
        double upX = -Math.sin(yaw) * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = Math.cos(yaw) * Math.sin(pitch);

        // レイ方向を計算
        // 右方向にoffsetX、「下」方向にoffsetY（画面下がndcY正なので）
        double dirX = forwardX + offsetX * rightX - offsetY * upX;
        double dirY = forwardY + offsetX * rightY - offsetY * upY;
        double dirZ = forwardZ + offsetX * rightZ - offsetY * upZ;

        // 正規化
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0) {
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }

        return new Vec3(dirX, dirY, dirZ);
    }
}
