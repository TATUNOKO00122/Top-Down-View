package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
import com.example.examplemod.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class MovementController {

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientForgeEvents.isTopDownView())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        float forward = event.getInput().forwardImpulse;
        float strafe = event.getInput().leftImpulse;

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
    }
}
