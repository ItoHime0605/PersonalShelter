package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientTeleporterData;
import com.ruadou.personaldimension.dimension.TeleporterEntry;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 传送器列表包（服务端 -> 客户端）。
 * 携带玩家名下所有传送器条目，供控制器传送标签页展示。
 */
public record TeleporterListPacket(List<TeleporterEntry> teleporters) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeleporterListPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "teleporter_list"));

    public static final StreamCodec<ByteBuf, TeleporterListPacket> STREAM_CODEC =
        StreamCodec.composite(
            TeleporterEntry.LIST_STREAM_CODEC, TeleporterListPacket::teleporters,
            TeleporterListPacket::new);

    public void handle() {
        ClientTeleporterData.update(this.teleporters);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
