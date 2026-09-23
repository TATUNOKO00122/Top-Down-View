package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * トップダウン視点中に step height を上げ、フルブロックの段差をジャンプせず歩いて登れるようにする。
 * auto-jump と違い縦方向の跳ねが発生しない。
 */
public final class StepAssistController {

    private static final float VANILLA_STEP_HEIGHT = 0.6F;
    private static final float ASSISTED_STEP_HEIGHT = 1.0F;

    private StepAssistController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        boolean active = ModState.STATUS.isEnabled() && Config.isStepAssistEnabled();
        float target = active ? ASSISTED_STEP_HEIGHT : VANILLA_STEP_HEIGHT;

        // リスポーンやディメンション移動でバニラ値に戻されても維持できるよう毎tick上書きする
        if (player.maxUpStep() != target) {
            player.setMaxUpStep(target);
        }
    }
}
