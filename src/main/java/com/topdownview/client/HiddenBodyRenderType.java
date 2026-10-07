package com.topdownview.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 「隠れた体の形」表示用の特別な描画指定。
 * <p>
 * メインFBOの深度バッファ（＝ワールド描画直後、エンティティ未描画の深度）に
 * 直接描画し、深度関数 GREATER（>) で描画する。ワールドの他の面より奥にある
 * ピクセル（＝障害物に隠れた断片）だけが描画される。エンティティがまだ
 * 描画されていないため、プレイヤー自身による自己遮蔽の誤判定は発生しない。
 * <p>
 * 親クラス {@link RenderType} の静的初期化は JVM が本クラスより先に完了させるため、
 * ここで参照する protected 定数群は必ず利用可能。
 */
public final class HiddenBodyRenderType extends RenderType {

    private HiddenBodyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                 boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    private static RenderType createImpl(String name, ResourceLocation texture) {
        return RenderType.create(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        // カスタムシェーダー：法線の方向光混合・fog・ColorModulatorのみ。
                        // ColorModulatorのアルファで不透明度を制御する
                        .setShaderState(new RenderStateShard.ShaderStateShard(HiddenBodyShaders::silhouette))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        // 60%アルファの重ね描きのため混合を有効化
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        // GL_GREATER(0x0204): 深度バッファより奥のピクセルのみ描画（＝遮蔽された断片）
                        .setDepthTestState(new RenderStateShard.DepthTestStateShard("greater", 0x0204))
                        // 深度は書かず色のみ更新。ワールド深度を壊さない
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false));
    }

    /** 単色白シルエット用。 */
    private static final RenderType WHITE = createImpl(
            "topdown_hidden_body_white",
            new ResourceLocation("minecraft", "textures/misc/white.png"));

    private static final Function<ResourceLocation, RenderType> TEXTURED = Util.memoize(
            texture -> createImpl("topdown_hidden_body_textured", texture));

    /** 単色白シルエット用。 */
    public static RenderType hiddenBody() {
        return WHITE;
    }

    /** テクスチャ付きシルエット用。 */
    public static RenderType hiddenBody(ResourceLocation texture) {
        return TEXTURED.apply(texture);
    }
}
