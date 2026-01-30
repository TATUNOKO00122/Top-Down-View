package com.topdownview.network;

import com.topdownview.TopDownViewMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TopDownViewMod.MODID, "reach"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    private static int nextId = 0;

    private PacketHandler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++,
                ReachTogglePacket.class,
                ReachTogglePacket::encode,
                ReachTogglePacket::decode,
                ReachTogglePacket::handle);

        CHANNEL.registerMessage(nextId++,
                ReachSyncPacket.class,
                ReachSyncPacket::encode,
                ReachSyncPacket::decode,
                ReachSyncPacket::handle);

        CHANNEL.registerMessage(nextId++,
                PlacementRotationPacket.class,
                PlacementRotationPacket::encode,
                PlacementRotationPacket::decode,
                PlacementRotationPacket::handle);
    }
}