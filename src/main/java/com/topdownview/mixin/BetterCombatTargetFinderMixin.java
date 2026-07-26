package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BetterCombat の TargetFinder に対する互換 Mixin。
 *
 * 問題①：Pitch が地下方向（-70°〜-90°）へ突き刺さり OBB が Mob に当たらない。
 *         → TopDownView 有効時は 0°（水平）に強制。
 *
 * 問題②：移動入力中は player.getYRot() が bodyYaw（移動方向）になり、
 *         マウスとは最大 ±45° ずれた向きで OBB が生成されて Mob を外す。
 *         → headYaw（マウスが向いている方向）にリダイレクト。
 */
@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
public abstract class BetterCombatTargetFinderMixin {

    /**
     * OBB 生成時の Pitch を 0°（水平）に補正。
     * Forge リマップ後のターゲット: getXRot()
     */
    @Redirect(
        method = "findAttackTargetResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getXRot()F"
        ),
        require = 0
    )
    private static float topdownview$redirectGetXRot(Player player) {
        if (ModState.STATUS.isEnabled()) {
            return 0.0f; // 水平方向で OBB を生成
        }
        return player.getXRot();
    }

    /**
     * OBB 生成時の Yaw をマウス方向（headYaw）にリダイレクト。
     * bodyYaw（移動方向）を使うと移動中に最大 ±45° のズレが生じて Mob を外す。
     * Forge リマップ後のターゲット: getYRot()
     */
    @Redirect(
        method = "findAttackTargetResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"
        ),
        require = 0
    )
    private static float topdownview$redirectGetYRot(Player player) {
        if (ModState.STATUS.isEnabled()) {
            // bodyYaw (yRot) の代わりに headYaw（マウスが向いている方向）を返す
            return player.getYHeadRot();
        }
        return player.getYRot();
    }
}
