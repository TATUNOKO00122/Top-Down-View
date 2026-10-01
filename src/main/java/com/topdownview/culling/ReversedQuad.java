package com.topdownview.culling;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * カリング断面用に {@link BakedQuad} の頂点順(ワインディング)を反転したコピーを作る。
 *
 * <p>消えるブロックの断面は「穴の内側(カメラ側)」から見えるため、元の面は表裏が逆。Embeddium の
 * 正規の {@code renderQuadList} にこの反転 quad を渡すことで、色・バイオームtint・AO・ライトマップを
 * 正規パイプラインのまま得つつ面だけ裏返せる。
 *
 * <p>独自の {@code BakedQuadView} 実装では {@code List<BakedQuad>} 経由のキャストに耐えないため、
 * 実際の {@link BakedQuad} を組み立て直す。頂点は {@link BakedQuadView} 経由で読み書きする。
 */
public final class ReversedQuad {

    private static final int VERTEX_COUNT = 4;

    private ReversedQuad() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static BakedQuad reverse(BakedQuad quad) {
        BakedQuadView view = (BakedQuadView) quad;
        int size = ModelQuadUtil.VERTEX_SIZE;
        int[] vertices = new int[size * VERTEX_COUNT];

        for (int i = 0; i < VERTEX_COUNT; i++) {
            int offset = i * size;
            vertices[offset] = Float.floatToRawIntBits(view.getX(i));
            vertices[offset + 1] = Float.floatToRawIntBits(view.getY(i));
            vertices[offset + 2] = Float.floatToRawIntBits(view.getZ(i));
            vertices[offset + 3] = view.getColor(i);
            vertices[offset + 4] = Float.floatToRawIntBits(view.getTexU(i));
            vertices[offset + 5] = Float.floatToRawIntBits(view.getTexV(i));
            vertices[offset + 6] = view.getLight(i);
            vertices[offset + 7] = view.getForgeNormal(i);
        }

        int[] reversed = new int[vertices.length];
        for (int i = 0; i < VERTEX_COUNT; i++) {
            System.arraycopy(vertices, i * size, reversed, (VERTEX_COUNT - 1 - i) * size, size);
        }

        return new BakedQuad(reversed, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }
}
