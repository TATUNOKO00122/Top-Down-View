package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class GameOptionsMixin {

    private static final OptionInstance<Boolean> FORCE_AUTO_JUMP = OptionInstance.createBoolean("options.autoJump", true);

    @Inject(method = "autoJump", at = @At("HEAD"), cancellable = true)
    public void topdownview$getAutoJump(CallbackInfoReturnable<OptionInstance<Boolean>> cir) {
        if (ModState.STATUS.isEnabled() && com.topdownview.Config.isForceAutoJump() && ModState.CLICK_TO_MOVE.isMoving()) {
            cir.setReturnValue(FORCE_AUTO_JUMP);
        }
    }

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void topdownview$onSetCameraType(CameraType type, CallbackInfo ci) {
        if (ModState.STATUS.isInternalCameraChange()) {
            return;
        }

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        // トップダウン有効時は外部からのカメラタイプ変更（F5、Waystones、ベッド起床等）を全て吸収
        // 無効化せずキャンセルのみ行う。F5での終了はInputHandlerで個別に処理
        ci.cancel();
    }

    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    public void topdownview$getCameraType(CallbackInfoReturnable<CameraType> cir) {
        if (ModState.STATUS.isEnabled() && !ModState.STATUS.isInternalCameraChange()) {
            // TACZ等のMODがフィールドを直接書き換えて一人称視点に強制した場合でも、
            // トップダウン視点中は三人称後方として振る舞うようにしてプレイヤーの描画消失を防ぐ
            cir.setReturnValue(CameraType.THIRD_PERSON_BACK);
        }
    }
}