package com.topdownview.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ReachTogglePacket(boolean enabled) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    public static ReachTogglePacket decode(FriendlyByteBuf buf) {
        return new ReachTogglePacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.topdownview.server.ServerReachHandler.onToggleReceived(
                    context.getSender(), enabled);
        });
        context.setPacketHandled(true);
    }
}