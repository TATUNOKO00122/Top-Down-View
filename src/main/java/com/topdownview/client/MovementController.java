package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class MovementController {

    private MovementController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientForgeEvents.isTopDownView()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float originalForward = event.getInput().forwardImpulse;
        float originalStrafe = event.getInput().leftImpulse;
        boolean hasManualInput = Math.abs(originalForward) > 0.01f || Math.abs(originalStrafe) > 0.01f;

        if (Config.clickToMoveEnabled && ModState.CLICK_TO_MOVE.isMoving()) {
            if (hasManualInput) {
                ClickToMoveController.stop();
            } else {
                float[] moveInput = ClickToMoveController.calculateMovementInput(mc);
                if (moveInput != null) {
                    float forward = moveInput[0];
                    float strafe = moveInput[1];

                    float cameraYaw = ModState.CAMERA.getYaw();
                    float playerYaw = mc.player.getYRot();
                    float diffYaw = cameraYaw - playerYaw;
                    float rad = (float) Math.toRadians(diffYaw);
                    float cos = Mth.cos(rad);
                    float sin = Mth.sin(rad);

                    float newForward = forward * cos + strafe * sin;
                    float newStrafe = strafe * cos - forward * sin;

                    event.getInput().forwardImpulse = newForward;
                    event.getInput().leftImpulse = newStrafe;
                    return;
                }
            }
        }

        float cameraYaw = ModState.CAMERA.getYaw();
        float playerYaw = mc.player.getYRot();
        float diffYaw = cameraYaw - playerYaw;
        float rad = (float) Math.toRadians(diffYaw);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        float newForward = originalForward * cos + originalStrafe * sin;
        float newStrafe = originalStrafe * cos - originalForward * sin;

        event.getInput().forwardImpulse = newForward;
        event.getInput().leftImpulse = newStrafe;
    }
}
