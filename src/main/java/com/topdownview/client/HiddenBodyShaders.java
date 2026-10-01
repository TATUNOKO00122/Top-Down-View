package com.topdownview.client;

import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;

/**
 * シルエット描画用カスタムコアシェーダーの保持クラス。
 * {@code RegisterShadersEvent} で登録されたインスタンスを保持する
 * （リソース再読み込み時は再登録され上書きされる）。
 */
public final class HiddenBodyShaders {

    private HiddenBodyShaders() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @Nullable
    private static volatile ShaderInstance silhouetteShader;

    @Nullable
    public static ShaderInstance silhouette() {
        return silhouetteShader;
    }

    static void setSilhouette(ShaderInstance shader) {
        silhouetteShader = shader;
    }
}
