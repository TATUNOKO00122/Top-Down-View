package com.topdownview.mixin;

import com.topdownview.client.PlayerRotationController;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft Mixin
 * 
 * ターゲット発光制御（EpicFight式）
 * shouldEntityAppearGlowingを上書きしてターゲットのみ発光させる
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(
        method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) return;
        
        // TargetHighlightStateが管理するターゲットのみ発光
        if (ModState.TARGET_HIGHLIGHT.shouldHighlight(entity)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        PlayerRotationController.onClientTick(mc);
    }
}
