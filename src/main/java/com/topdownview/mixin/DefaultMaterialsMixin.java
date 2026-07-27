package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium用 DefaultMaterials Mixin
 * 液体半透明化が有効な場合、流体（溶岩など）をTRANSLUCENTパスへ変更する
 */
@Mixin(value = DefaultMaterials.class, remap = false)
public class DefaultMaterialsMixin {

    @Inject(method = "forFluidState", at = @At("HEAD"), cancellable = true)
    private static void onForFluidState(FluidState state, CallbackInfoReturnable<Material> cir) {
        if (!ModState.STATUS.isEnabled() || !Config.isTranslucentFluid()) {
            return;
        }

        // 液体半透明表示が有効な場合、流体をTRANSLUCENTマテリアルで描画
        cir.setReturnValue(DefaultMaterials.TRANSLUCENT);
    }
}
