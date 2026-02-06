package com.example.examplemod.culling;

import net.minecraft.core.BlockPos;

/**
 * シンプルなカリングインターフェース
 * プレイヤーとカメラの間にあるブロックを非表示にする
 */
public interface Culler {

    /**
     * ブロックがカリング対象か判定
     *
     * @param pos ブロック位置
     * @return カリング対象ならtrue
     */
    boolean isCulled(BlockPos pos);

    /**
     * カリング状態を更新
     * プレイヤーまたはカメラが移動した時に呼び出す
     */
    void update();

    /**
     * カリングをリセット
     */
    void reset();

    /**
     * 更新頻度を取得
     * 1 = 毎tick更新、5 = 5tickごとに更新
     * @return 更新頻度（tick単位）
     */
    default int getFrequency() {
        return 1;
    }
}
