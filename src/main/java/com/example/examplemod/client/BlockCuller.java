package com.example.examplemod.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * カメラとプレイヤーの間にあるブロックをカリングするためのユーティリティクラス
 */
public class BlockCuller {

    // カリング対象のブロック位置をキャッシュ
    private static final Set<BlockPos> culledBlocks = new HashSet<>();

    // カメラ位置とターゲット位置のキャッシュ
    private static Vec3 cameraPos = null;
    private static Vec3 targetPos = null;

    /**
     * カメラ位置とターゲット位置からカリング対象のブロックを計算
     *
     * @param camera  カメラ位置
     * @param target  ターゲット（プレイヤーの目など）位置
     */
    public static void updateCulling(Vec3 camera, Vec3 target) {
        culledBlocks.clear();
        cameraPos = camera;
        targetPos = target;

        // レイ上のブロックをすべて収集
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        // レイの方向と距離
        Vec3 direction = target.subtract(camera);
        double distance = direction.length();

        if (distance <= 0) {
            return;
        }

        // 正規化
        Vec3 normalized = direction.normalize();

        // 少し進んだ位置から開始（カメラ自体がブロックの中にいる場合を避ける）
        Vec3 current = camera.add(normalized.scale(0.1));
        double traveled = 0.1;

        // ターゲットに到達するまでステップ移動
        double stepSize = 0.05; // 小さなステップサイズで精度を確保

        while (traveled < distance) {
            blockPos.set((int) Math.floor(current.x), (int) Math.floor(current.y), (int) Math.floor(current.z));
            culledBlocks.add(blockPos.immutable());

            current = current.add(normalized.scale(stepSize));
            traveled += stepSize;
        }
    }

    /**
     * 指定したブロック位置がカリング対象かどうかを判定
     *
     * @param pos 判定するブロック位置
     * @return カリング対象ならtrue
     */
    public static boolean shouldCull(BlockPos pos) {
        return culledBlocks.contains(pos);
    }

    /**
     * カリング対象のブロックをクリア
     */
    public static void clearCulling() {
        culledBlocks.clear();
        cameraPos = null;
        targetPos = null;
    }

    /**
     * 現在カリングされているブロックの数を取得（デバッグ用）
     */
    public static int getCulledBlockCount() {
        return culledBlocks.size();
    }
}
