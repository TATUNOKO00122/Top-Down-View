package com.topdownview.network;

import com.topdownview.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ReachSyncPacket(double reachDistance) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(reachDistance);
    }

    public static ReachSyncPacket decode(FriendlyByteBuf buf) {
        return new ReachSyncPacket(buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.isSingleplayer()) {
                    return;
                }
                Config.setSyncedServerReach(reachDistance);
            });
        }
        context.setPacketHandled(true);
    }
}