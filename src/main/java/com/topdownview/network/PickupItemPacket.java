package com.topdownview.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PickupItemPacket(int entityId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static PickupItemPacket decode(FriendlyByteBuf buf) {
        return new PickupItemPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                com.topdownview.server.ServerPickupHandler.onPickupRequest(context.getSender(), entityId));
        context.setPacketHandled(true);
    }
}
