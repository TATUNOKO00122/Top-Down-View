package com.topdownview.client;

import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import com.topdownview.state.CameraState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Objects;

/**
 * トップダウン視点用のマウスレイキャスト処理
 * シングルトンパターンでインスタンス管理
 * パフォーマンス最適化：キャッシュを実装
 */
public final class MouseRaycast {

    // レイキャスト結果を保持するレコード
    private record RaycastResult(Vec3 start, Vec3 end, Vec3 direction, BlockHitResult blockHit) {
    }

    // レイキャスト定数
    private static final double RAYCAST_STEP = 0.5;
    private static final double MIN_RAYCAST_STEP = 0.25;
    private static final double MAX_RAYCAST_STEP = 1.0;
    private static final double FAR_DISTANCE_THRESHOLD = 50.0;
    private static final double MEDIUM_DISTANCE_THRESHOLD = 20.0;

    // カメラ角度定数
    private static final double FIXED_PITCH_DEG = 45.0;

    // スクリーン座標変換定数
    private static final double SCREEN_TO_NDC_FACTOR = 2.0;
    private static final double NDC_OFFSET = 1.0;
    private static final double MIN_SCREEN_DIMENSION = 1.0;

    // リーチ距離計算定数
    private static final double MIN_REACH_DISTANCE = 30.0;
    private static final double REACH_DISTANCE_MULTIPLIER = 1.5;

    // エンティティ検索定数
    private static final double SEARCH_BOX_INFLATE = 4.0;            // 検索ボックスの拡張（ブロック）
    private static final double MAX_ENTITY_DISTANCE = 30.0;          // 最大エンティティ距離（ブロック）
    private static final double LINE_PROXIMITY_THRESHOLD = 2.0;      // 線からの最大距離（ブロック）

    // シングルトンインスタンス
    public static final MouseRaycast INSTANCE = new MouseRaycast();

    // 状態
    private BlockHitResult lastBlockHit = null;
    private EntityHitResult lastEntityHit = null;
    private net.minecraft.world.phys.HitResult lastHitResult = null;

    // キャッシュ
    private Vec3 cachedDirection = null;
    private double lastFov = -1;
    private double lastAspectRatio = -1;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private MouseRaycast() {
    }

    /**
     * カスタムリーチ距離を取得
     */
    public static double getCustomReachDistance() {
        double cameraDistance = CameraState.INSTANCE.getCameraDistance();
        return Math.max(MIN_REACH_DISTANCE, cameraDistance * REACH_DISTANCE_MULTIPLIER);
    }

    /**
     * レイキャストを更新
     * Minecraftメインスレッドのみで呼び出し
     */
    public void update(Minecraft mc, float partialTick, double reachDistance) {
        Objects.requireNonNull(mc, "Minecraft instance cannot be null");

        if (mc.level == null || mc.player == null) {
            clearResults();
            return;
        }

        // 共通のレイキャスト処理を実行
        RaycastResult result = performRaycast(mc, reachDistance);
        if (result == null) {
            clearResults();
            return;
        }

        lastBlockHit = result.blockHit;

        // Entity Hit
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity != null) {
            lastEntityHit = performEntityRaycast(mc, result.start, result.blockHit, result.end, reachDistance);
        } else {
            lastEntityHit = null;
        }

        // 最も近いヒットを選択
        if (lastEntityHit != null) {
            double blockDist = lastBlockHit.getLocation().distanceToSqr(result.start);
            double entityDist = result.start.distanceToSqr(lastEntityHit.getLocation());
            lastHitResult = (entityDist < blockDist) ? lastEntityHit : lastBlockHit;
        } else {
            lastHitResult = lastBlockHit;
        }
    }

    /**
     * 共通のレイキャスト処理
     * カメラ位置、方向、ブロックヒットを計算
     */
    private RaycastResult performRaycast(Minecraft mc, double reachDistance) {
        Vec3 start = getCameraPosition(mc);
        if (start == null) {
            return null;
        }

        Vec3 direction = getMouseRayDirection(mc);
        if (direction == null) {
            return null;
        }

        Vec3 end = start.add(direction.scale(reachDistance));
        BlockHitResult blockHit = rayTraceBlocks(mc, start, end);

        if (blockHit == null) {
            return null;
        }

        return new RaycastResult(start, end, direction, blockHit);
    }

    /**
     * エンティティレイキャストを実行
     * マウスカーソル位置周辺のエンティティを検出
     * プレイヤーに最も近いエンティティをターゲットする
     */
    private EntityHitResult performEntityRaycast(Minecraft mc, Vec3 start, BlockHitResult blockHit, Vec3 end,
            double reachDistance) {
        if (mc.player == null) {
            return null;
        }

        // マウスカーソル位置を計算
        // 斜めカメラでは、レイと地面（プレイヤーのY高度）との交点を使用
        Vec3 mousePos = calculateGroundIntersection(mc, start, end);
        if (mousePos == null) {
            // 地面と交差しない場合はブロックヒット位置を使用
            mousePos = blockHit.getLocation();
            if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                mousePos = end;
            }
        }

        // プレイヤー位置（目の位置）
        Vec3 playerEyePos = mc.player.getEyePosition(1.0f);

        // 検索ボックス：マウスカーソル位置を中心に一定範囲を拡張
        AABB searchBox = new AABB(mousePos, mousePos).inflate(LINE_PROXIMITY_THRESHOLD);

        // マウスカーソル周辺のエンティティを検索
        var entities = mc.level.getEntities(
                mc.player,
                searchBox,
                (entity) -> entity != null && !entity.isSpectator() && entity.isPickable() && entity != mc.player
        );

        Entity closestEntity = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Vec3 closestHitPoint = null;

        for (Entity entity : entities) {
            // XZ平面（水平方向）でのマウスカーソルからエンティティの距離を計算
            double distanceToMouse = distanceFromPointToPointIgnoringY(
                    entity.getBoundingBox().getCenter(),
                    mousePos
            );

            // マウスカーソルから一定距離内にあるかチェック
            if (distanceToMouse > LINE_PROXIMITY_THRESHOLD) {
                continue;
            }

            // 地形による遮断チェック
            if (!hasLineOfSight(mc, playerEyePos, entity)) {
                continue;
            }

            // プレイヤーからの距離を計算
            double distToPlayerSq = entity.distanceToSqr(playerEyePos);
            if (distToPlayerSq < closestDistanceSq) {
                closestDistanceSq = distToPlayerSq;
                closestEntity = entity;
                closestHitPoint = entity.getBoundingBox().getCenter();
            }
        }

        if (closestEntity != null) {
            return new EntityHitResult(closestEntity, closestHitPoint);
        }

        return null;
    }

    /**
     * 2点間の距離を計算（Y軸無視）
     * XZ平面（水平方向）のみで距離を計算する
     */
    private double distanceFromPointToPointIgnoringY(Vec3 point1, Vec3 point2) {
        // Y座標を無視（XZ平面のみで計算）
        double dx = point1.x - point2.x;
        double dz = point1.z - point2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * レイと地面（プレイヤーのY高度）との交点を計算
     * 斜めカメラで画面端でも正確なカーソル位置を取得するため
     */
    private Vec3 calculateGroundIntersection(Minecraft mc, Vec3 start, Vec3 end) {
        if (mc.player == null) {
            return null;
        }

        // プレイヤーの目の高さを基準にした地面のY座標
        // プレイヤーの足元より少し上（目の高さより下）をターゲット平面とする
        double targetY = mc.player.getEyePosition(1.0f).y - 1.0;

        Vec3 direction = end.subtract(start);
        double dy = direction.y;

        // 水平の場合は交点なし（地面と平行）
        if (Math.abs(dy) < 0.001) {
            return null;
        }

        // レイとtargetY平面との交点を計算
        // start.y + t * dy = targetY  →  t = (targetY - start.y) / dy
        double t = (targetY - start.y) / dy;

        // tが負なら交点はカメラの後ろ
        if (t < 0) {
            return null;
        }

        // 交点座標を計算
        double intersectX = start.x + t * direction.x;
        double intersectY = targetY;
        double intersectZ = start.z + t * direction.z;

        return new Vec3(intersectX, intersectY, intersectZ);
    }

    /**
     * プレイヤーからエンティティまで視線が通っているかチェック
     */
    private boolean hasLineOfSight(Minecraft mc, Vec3 fromPos, Entity target) {
        Vec3 targetPos = target.getBoundingBox().getCenter();

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                fromPos,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        // ブロックにヒットしなかった場合は視線が通っている
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /**
     * ブロックレイキャスト
     * パフォーマンス最適化：適応的ステップサイズ
     */
    private BlockHitResult rayTraceBlocks(Minecraft mc, Vec3 start, Vec3 end) {
        Objects.requireNonNull(mc, "Minecraft cannot be null");
        Objects.requireNonNull(mc.level, "Level cannot be null");
        Objects.requireNonNull(mc.player, "Player cannot be null");

        // トップダウンビューでない場合は通常のレイキャスト
        if (!ClientForgeEvents.isTopDownView()) {
            return mc.level.clip(
                    new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        }

        double distance = start.distanceTo(end);
        if (distance <= 0 || !Double.isFinite(distance)) {
            return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        }

        Vec3 direction = end.subtract(start).normalize();
        double step = calculateAdaptiveStep(distance);
        BlockPos lastCheckedPos = null;

        for (double d = 0; d < distance; d += step) {
            Vec3 currentPos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);

            // 同じブロックは再チェックしない
            if (blockPos.equals(lastCheckedPos)) {
                continue;
            }
            lastCheckedPos = blockPos;

            // カリングされているブロックは無視（キャッシュにない場合は計算）
            if (TopDownCuller.getInstance().isBlockCulled(blockPos, mc.level)) {
                continue;
            }

            // ブロックが実際にコリジョンを持つかチェック
            BlockState state = mc.level.getBlockState(blockPos);
            if (!state.isAir()) {
                var shape = state.getShape(mc.level, blockPos, CollisionContext.of(mc.player));
                if (!shape.isEmpty()) {
                    // ブロックの形状とレイの交差をチェック
                    var clipResult = shape.clip(start, end, blockPos);
                    if (clipResult != null) {
                        return clipResult;
                    }
                }
            }
        }

        // ヒットしなかった場合
        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }

    /**
     * 距離に基づいて適応的なステップサイズを計算
     */
    private double calculateAdaptiveStep(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) {
            return MIN_RAYCAST_STEP;
        }

        if (distance > FAR_DISTANCE_THRESHOLD) {
            return MAX_RAYCAST_STEP;
        } else if (distance > MEDIUM_DISTANCE_THRESHOLD) {
            return RAYCAST_STEP;
        }
        return MIN_RAYCAST_STEP;
    }

    public net.minecraft.world.phys.HitResult getLastHitResult() {
        return lastHitResult;
    }

    public BlockHitResult getLastBlockHit() {
        return lastBlockHit;
    }

    public EntityHitResult getLastEntityHit() {
        return lastEntityHit;
    }

    /**
     * ブロックレイキャスト（後方互換性）
     */
    public static BlockHitResult rayTraceBlock(Minecraft mc, float partialTick, double reachDistance) {
        return INSTANCE.performRayTraceBlock(mc, reachDistance);
    }

    private BlockHitResult performRayTraceBlock(Minecraft mc, double reachDistance) {
        Objects.requireNonNull(mc, "Minecraft cannot be null");

        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }

        RaycastResult result = performRaycast(mc, reachDistance);
        return result != null ? result.blockHit : null;
    }

    /**
     * エンティティレイキャスト（後方互換性）
     */
    public static EntityHitResult rayTraceEntity(Minecraft mc, float partialTick, double reachDistance) {
        return INSTANCE.performRayTraceEntity(mc, reachDistance);
    }

    private EntityHitResult performRayTraceEntity(Minecraft mc, double reachDistance) {
        Objects.requireNonNull(mc, "Minecraft cannot be null");

        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }

        RaycastResult result = performRaycast(mc, reachDistance);
        if (result == null) {
            return null;
        }

        return performEntityRaycast(mc, result.start, result.blockHit, result.end, reachDistance);
    }

    /**
     * ヒット結果を取得（後方互換性）
     */
    public static net.minecraft.world.phys.HitResult getHitResult(Minecraft mc, float partialTick,
            double reachDistance) {
        return INSTANCE.performGetHitResult(mc, reachDistance);
    }

    private net.minecraft.world.phys.HitResult performGetHitResult(Minecraft mc, double reachDistance) {
        Objects.requireNonNull(mc, "Minecraft cannot be null");

        if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }

        RaycastResult result = performRaycast(mc, reachDistance);
        if (result == null) {
            return null;
        }

        EntityHitResult entityHit = performEntityRaycast(mc, result.start, result.blockHit, result.end, reachDistance);
        if (entityHit != null) {
            double blockDist = result.blockHit.getLocation().distanceToSqr(result.start);
            double entityDist = result.start.distanceToSqr(entityHit.getLocation());
            if (entityDist < blockDist) {
                return entityHit;
            }
        }

        return result.blockHit;
    }

    private Vec3 getCameraPosition(Minecraft mc) {
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return null;
        }
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    /**
     * マウスレイの方向を計算
     * キャッシュを使用してパフォーマンスを最適化
     */
    private Vec3 getMouseRayDirection(Minecraft mc) {
        Objects.requireNonNull(mc, "Minecraft cannot be null");
        Objects.requireNonNull(mc.mouseHandler, "MouseHandler cannot be null");
        Objects.requireNonNull(mc.options, "Options cannot be null");
        Objects.requireNonNull(mc.getWindow(), "Window cannot be null");

        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double screenWidth = mc.getWindow().getScreenWidth();
        double screenHeight = mc.getWindow().getScreenHeight();

        // 画面サイズの検証
        if (screenWidth < MIN_SCREEN_DIMENSION || screenHeight < MIN_SCREEN_DIMENSION) {
            return null;
        }

        double fov = mc.options.fov().get();
        double aspectRatio = screenWidth / screenHeight;

        // キャッシュが有効かチェック
        if (cachedDirection != null &&
                mouseX == lastMouseX && mouseY == lastMouseY &&
                fov == lastFov && aspectRatio == lastAspectRatio) {
            return cachedDirection;
        }

        double ndcX = -((SCREEN_TO_NDC_FACTOR * mouseX / screenWidth) - NDC_OFFSET);
        double ndcY = ((SCREEN_TO_NDC_FACTOR * mouseY / screenHeight) - NDC_OFFSET);

        double fovRad = Math.toRadians(fov);
        double tanHalfFov = Math.tan(fovRad / SCREEN_TO_NDC_FACTOR);

        double offsetX = ndcX * tanHalfFov * aspectRatio;
        double offsetY = ndcY * tanHalfFov;

        double pitchRad = Math.toRadians(FIXED_PITCH_DEG);
        double yawRad = Math.toRadians(ModState.CAMERA.getYaw());

        double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double forwardY = -Math.sin(pitchRad);
        double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double rightX = Math.cos(yawRad);
        double rightY = 0;
        double rightZ = Math.sin(yawRad);

        double upX = -Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

        double dirX = forwardX + offsetX * rightX - offsetY * upX;
        double dirY = forwardY + offsetX * rightY - offsetY * upY;
        double dirZ = forwardZ + offsetX * rightZ - offsetY * upZ;

        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0) {
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }

        Vec3 direction = new Vec3(dirX, dirY, dirZ);

        // キャッシュを更新
        cachedDirection = direction;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastFov = fov;
        lastAspectRatio = aspectRatio;

        return direction;
    }

    /**
     * 結果をクリア
     */
    private void clearResults() {
        lastBlockHit = null;
        lastEntityHit = null;
        lastHitResult = null;
    }

    /**
     * キャッシュをクリア
     */
    public void clearCache() {
        cachedDirection = null;
        lastFov = -1;
        lastAspectRatio = -1;
        lastMouseX = -1;
        lastMouseY = -1;
    }
}
