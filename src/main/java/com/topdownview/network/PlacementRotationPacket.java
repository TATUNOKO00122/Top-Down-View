package com.topdownview.network;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * クライアント→サーバー: ブロック配置方向の指定を送信
 *
 * facing が null の場合は「クリア」（Vanilla の視線ベース配置に戻す）
 * サーバー側で ServerPlacementRotationHandler がプレイヤーごとの向きを保持し、
 * BlockItemPlacementMixin が getPlacementState 時に参照する。
 *
 * TCP 順序保証により、向き変更パケット → 右クリックパケットの順で
 * サーバー処理されるため、タイミングのずれは発生しない。
 */
public record PlacementRotationPacket(@Nullable Direction facing) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(facing != null);
        if (facing != null) {
            buf.writeEnum(facing);
        }
    }

    public static PlacementRotationPacket decode(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return new PlacementRotationPacket(buf.readEnum(Direction.class));
        }
        return new PlacementRotationPacket(null);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.topdownview.server.ServerPlacementRotationHandler.onRotationReceived(
                    context.getSender(), facing);
        });
        context.setPacketHandled(true);
    }
}
