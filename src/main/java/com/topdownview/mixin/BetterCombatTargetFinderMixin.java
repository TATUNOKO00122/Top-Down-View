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
 * 問題：Pitch が地下方向（-70°〜-90°）へ突き刺さり OBB が Mob に当たらない。
 *         → TopDownView 有効時は 0°（水平）に強制。
 *
 * Yaw は getYRot() が照準（headYaw）を返すようになったため補正不要。
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
}
