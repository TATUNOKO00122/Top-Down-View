package com.topdownview.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/**
 * 鉱石ブロックの判定ユーティリティ
 * forge:ores タグを使用してMOD追加鉱石も自動対応
 */
public final class OreBlocks {

    private OreBlocks() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 指定されたブロックが鉱石かどうかを判定
     * forge:ores タグで判定するため、MOD追加鉱石も自動的に含まれる
     *
     * @param state 判定対象のブロック状態
     * @return 鉱石の場合true
     */
    public static boolean isOre(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.is(Tags.Blocks.ORES);
    }
}
