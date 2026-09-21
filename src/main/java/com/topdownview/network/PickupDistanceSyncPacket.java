package com.topdownview.network;

import com.topdownview.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PickupDistanceSyncPacket(double distance) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(distance);
    }

    public static PickupDistanceSyncPacket decode(FriendlyByteBuf buf) {
        return new PickupDistanceSyncPacket(buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.isSingleplayer()) {
                    return;
                }
                Config.setSyncedManualPickupDistance(distance);
            });
        }
        context.setPacketHandled(true);
    }
}
