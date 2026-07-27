package com.topdownview.mixin.compat.mobends;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MoBends (Forge 1.20.1 5.1.7+) の {@code ModCompatManager} 用 Mixin。
 *
 * MoBends は {@code shouldDeferAnimation(LivingEntity)} が {@code true} を返すと
 * 対象エンティティの MoBends アニメーション処理（下向き80度傾斜を含む）をすべてスキップする。
 *
 * ローカルプレイヤーが水中にいる場合（isInWater, isEyeInFluid, isSwimming）、
 * {@code true} を返して MoBends を完全にキャンセルする。
 */
@Pseudo
@Mixin(targets = "goblinbob.mobends.forge.compat.ModCompatManager", remap = false)
public class MoBendsModCompatManagerMixin {

    @Inject(method = "shouldDeferAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void topdownview$deferMoBendsInWater(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!Config.isWaterMovementControlEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return;

        Player player = mc.player;
        if (player.isInWater() || player.isEyeInFluid(FluidTags.WATER) || player.isSwimming()) {
            cir.setReturnValue(true);
        }
    }
}

