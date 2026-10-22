package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.TeleporterService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 重命名传送器请求包（客户端 -> 服务端）。
 * 携带传送器在列表中的索引和新的 name / note。
 */
public record RenameTeleporterPacket(int index, String name, String note) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameTeleporterPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "rename_teleporter"));

    public static final StreamCodec<ByteBuf, RenameTeleporterPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, RenameTeleporterPacket::index,
            ByteBufCodecs.stringUtf8(100), RenameTeleporterPacket::name,
            ByteBufCodecs.stringUtf8(50), RenameTeleporterPacket::note,
            RenameTeleporterPacket::new);

    public void handle(ServerPlayer player) {
        TeleporterService.rename(player.getServer(), player.getUUID(), this.index, this.name, this.note);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
