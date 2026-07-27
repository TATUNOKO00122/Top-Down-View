package com.topdownview.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.topdownview.Config;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * バニラ用 LiquidBlockRenderer Mixin
 * カリング対象の流体の描画制御（完全非表示 または アルファ透明度適用）
 */
@Mixin(LiquidBlockRenderer.class)
public class LiquidBlockRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(
        method = "tesselate",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onTessellateHead(
            BlockAndTintGetter level,
            BlockPos pos,
            VertexConsumer buffer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled() || pos == null) {
            return;
        }

        if (CULLER.isBlockCulled(pos, level)) {
            if (!Config.isTranslucentFluid()) {
                // 半透明化が無効な場合は描画をキャンセル（完全非表示）
                ci.cancel();
            }
        }
    }

    @ModifyVariable(
        method = "tesselate",
        at = @At("HEAD"),
        argsOnly = true
    )
    private VertexConsumer modifyBuffer(
            VertexConsumer buffer,
            BlockAndTintGetter level,
            BlockPos pos,
            VertexConsumer originalBuffer,
            BlockState blockState,
            FluidState fluidState) {

        if (!ModState.STATUS.isEnabled() || pos == null || !Config.isTranslucentFluid()) {
            return buffer;
        }

        if (CULLER.isBlockCulled(pos, level)) {
            float alpha = (float) Config.getFluidAlpha();
            return new AlphaVertexConsumerWrapper(buffer, alpha);
        }

        return buffer;
    }

    /**
     * 頂点カラーのアルファ成分を指定された割合に乗算するVertexConsumerラッパー
     */
    private static class AlphaVertexConsumerWrapper implements VertexConsumer {
        private final VertexConsumer parent;
        private final float alphaFactor;

        public AlphaVertexConsumerWrapper(VertexConsumer parent, float alphaFactor) {
            this.parent = parent;
            this.alphaFactor = alphaFactor;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return parent.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            int newAlpha = (int) (alpha * alphaFactor);
            if (newAlpha < 0) newAlpha = 0;
            if (newAlpha > 255) newAlpha = 255;
            return parent.color(red, green, blue, newAlpha);
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return parent.uv(u, v);
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return parent.overlayCoords(u, v);
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return parent.uv2(u, v);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return parent.normal(x, y, z);
        }

        @Override
        public void endVertex() {
            parent.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            parent.defaultColor(r, g, b, (int) (a * alphaFactor));
        }

        @Override
        public void unsetDefaultColor() {
            parent.unsetDefaultColor();
        }
    }
}
