package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BetterCombat の MinecraftClientInject に対する互換 Mixin。
 * トップダウン視点時に地面・壁ブロックがカーソル下にあっても、攻撃可能領域内にエネミーがいる場合は
 * isTargetingMineableBlock を false に補正し、採掘ではなく武器攻撃を優先させます。
 */
@Pseudo
@Mixin(targets = "net.bettercombat.mixin.client.MinecraftClientInject", remap = false)
public abstract class BetterCombatClientInjectMixin {

    @Inject(method = "isTargetingMineableBlock", at = @At("RETURN"), cancellable = true, require = 0)
    private void topdownview$onIsTargetingMineableBlock(CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!cir.getReturnValueZ()) return; // 既に false（攻撃優先）なら何もしない

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // ロックオンターゲットが存在する場合、採掘をキャンセルして攻撃を優先
        if (ModState.TARGET_LOCK.isLocked()) {
            cir.setReturnValue(false);
            return;
        }

        // プレイヤー周辺の攻撃可能領域内に敵対的モブまたは生きているMobが存在するか判定
        double reach = Config.getEffectiveReachDistance();
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        AABB searchBox = mc.player.getBoundingBox().inflate(reach);

        boolean hasHostileNearby = mc.level.getEntities(mc.player, searchBox, entity ->
            entity != null && !entity.isSpectator() && entity.isPickable() && entity.isAlive()
            && (entity instanceof Monster || entity instanceof LivingEntity)
        ).stream().anyMatch(entity -> {
            double distSq = eyePos.distanceToSqr(entity.getBoundingBox().getCenter());
            return distSq <= (reach * reach);
        });

        if (hasHostileNearby) {
            cir.setReturnValue(false);
        }
    }
}
