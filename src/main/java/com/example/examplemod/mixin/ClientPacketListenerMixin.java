package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V", at = @At("HEAD"))
    private void onHandleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 soundPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            double distance = Minecraft.getInstance().player.position().distanceTo(soundPos);
            System.out.println("[ClientPacketListenerMixin] handleSoundEvent packet received - Sound: " + 
                packet.getSound().value().getLocation() + 
                ", Pos: (" + packet.getX() + ", " + packet.getY() + ", " + packet.getZ() + ")" +
                ", Distance to player: " + String.format("%.2f", distance));
        }
    }

    @Inject(method = "handleSoundEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;)V", at = @At("HEAD"))
    private void onHandleSoundEntityEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            System.out.println("[ClientPacketListenerMixin] handleSoundEntityEvent packet received - Sound: " + 
                packet.getSound().value().getLocation() + 
                ", EntityId: " + packet.getId());
        }
    }
}
