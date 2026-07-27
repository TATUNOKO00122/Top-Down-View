package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * バニラ用 ItemBlockRenderTypes Mixin
 * 液体半透明化が有効な場合、流体（溶岩など）のRenderTypeをtranslucentへ変更する
 */
@Mixin(ItemBlockRenderTypes.class)
public class ItemBlockRenderTypesMixin {

    @Inject(method = "getRenderLayer(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void onGetRenderLayer(FluidState state, CallbackInfoReturnable<RenderType> cir) {
        if (!ModState.STATUS.isEnabled() || !Config.isTranslucentFluid()) {
            return;
        }

        cir.setReturnValue(RenderType.translucent());
    }
}
