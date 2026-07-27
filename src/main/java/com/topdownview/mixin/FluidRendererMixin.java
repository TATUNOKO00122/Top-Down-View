package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium用流体レンダラMixin
 * カリング対象の流体（水、溶岩）の描画制御
 * translucentFluid有効時は完全非表示にする代わりにアルファ透過度を調整する
 */
@Mixin(value = FluidRenderer.class, remap = false)
public abstract class FluidRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Shadow
    private int[] quadColors;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(
            WorldSlice world,
            net.minecraft.world.level.material.FluidState fluidState,
            BlockPos blockPos,
            BlockPos offset,
            me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers buffers,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled() || blockPos == null) {
            return;
        }

        if (CULLER.isBlockCulled(blockPos, world)) {
            if (!Config.isTranslucentFluid()) {
                // 半透明化が無効の場合は従来通り完全非表示にする
                ci.cancel();
            }
            // 半透明化が有効の場合はキャンセルの代わりに描画を継続
        }
    }

    @Inject(method = "updateQuad", at = @At("TAIL"))
    private void onUpdateQuadTail(
            me.jellysquid.mods.sodium.client.model.quad.ModelQuadView quad,
            WorldSlice world,
            BlockPos pos,
            me.jellysquid.mods.sodium.client.model.light.LightPipeline lighter,
            net.minecraft.core.Direction dir,
            float brightness,
            me.jellysquid.mods.sodium.client.model.color.ColorProvider<net.minecraft.world.level.material.FluidState> colorProvider,
            net.minecraft.world.level.material.FluidState fluidState,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled() || pos == null || !Config.isTranslucentFluid()) {
            return;
        }

        if (CULLER.isBlockCulled(pos, world)) {
            float alphaFactor = (float) Config.getFluidAlpha();

            for (int i = 0; i < 4; i++) {
                int originalColor = this.quadColors[i];
                int originalAlpha = (originalColor >> 24) & 0xFF;
                int newAlpha = (int) (originalAlpha * alphaFactor);
                if (newAlpha < 0) newAlpha = 0;
                if (newAlpha > 255) newAlpha = 255;

                this.quadColors[i] = (originalColor & 0x00FFFFFF) | (newAlpha << 24);
            }
        }
    }
}

