package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.client.ClickActionHandler;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private boolean mouseGrabbed;

    @Inject(method = "grabMouse", at = @At("TAIL"))
    private void onGrabMouse(CallbackInfo ci) {
        if (ModState.STATUS.isEnabled() && mouseGrabbed) {
            Minecraft mc = Minecraft.getInstance();
            org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !ModState.STATUS.isEnabled()) return;

        // 右クリック(useButton)は一切介入せずバニラへ素通し。
        // 理由: 弓・クロスボウ・盾・食べ物等の「長押しチャージ」はバニラが keyUse.isDown() を
        // 毎 tick 参照して維持する仕組みに依存する。ここで ci.cancel() すると keyUse.isDown() が
        // 更新されず、チャージ維持ループが回らなくなって弓が引けなくなる。
        int attackButton = mc.options.keyAttack.getKey().getValue();
        if (button != attackButton) return;

        ClickActionHandler.onInput(button, action, mc);

        if (!Config.isClickToMoveEnabled()) return;

        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            ci.cancel();
        }
    }
}
