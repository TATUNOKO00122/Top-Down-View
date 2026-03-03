package com.topdownview.mixin;

import com.topdownview.client.ClickActionHandler;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public HitResult hitResult;

    @Shadow
    protected int missTime;

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled())
            return;

        Minecraft mc = Minecraft.class.cast(this);
        if (mc.player == null || mc.gameMode == null)
            return;

        // Baritoneの自動採掘や移動操作中、またはクリック移動が進行中の場合はバニラの処理に任せる
        if (ModState.CLICK_TO_MOVE.isMoving() || com.topdownview.baritone.BaritoneIntegration.isPathing()) {
            return;
        }

        // GLFWやVanillaのGUI判定に邪魔されず、マウスの左クリック状態を直接見る
        if (ClickActionHandler.isLeftClickDown) {
            this.missTime = 0;

            if (mc.hitResult != null) {
                if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                    BlockPos pos = blockHit.getBlockPos();
                    if (!mc.level.getBlockState(pos).isAir()) {
                        Direction dir = blockHit.getDirection();
                        if (mc.gameMode.continueDestroyBlock(pos, dir)) {
                            mc.particleEngine.crack(pos, dir);
                            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        }
                    }
                } else if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
                    if (mc.player.isUsingItem()) {
                        mc.player.stopUsingItem();
                    }
                }
            }
            ci.cancel(); // Vanillaの不要なフラグチェック処理をスキップ
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled())
            return;
        this.missTime = 0;
    }
}
