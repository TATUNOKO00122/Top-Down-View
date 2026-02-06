package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
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
        if (!ClientForgeEvents.isTopDownView()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float forward = event.getInput().forwardImpulse;
        float strafe = event.getInput().leftImpulse;

        float playerYaw = mc.player.getYRot();
        float rad = (float) Math.toRadians(playerYaw);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        float newForward = -strafe * sin + forward * cos;
        float newStrafe = strafe * cos + forward * sin;

        event.getInput().forwardImpulse = newForward;
        event.getInput().leftImpulse = newStrafe;
    }
}
