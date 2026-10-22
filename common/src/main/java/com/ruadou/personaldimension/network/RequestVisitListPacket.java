package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.VisitShelterEntry;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



/**
 * 可访问维度列表请求包（客户端 -&gt; 服务端）。
 * 访问界面打开时请求，服务端复算权限后以 {@link VisitListPacket} 回传当前可进入的他人维度列表。
 */
public record RequestVisitListPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestVisitListPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_visit_list"));

    public static final StreamCodec<ByteBuf, RequestVisitListPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestVisitListPacket());

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        java.util.List<VisitShelterEntry> entries = new java.util.ArrayList<>();
        for (var owner : FriendService.visitableFor(server, player.getUUID())) {
            var level = server.getLevel(Platform.levelKeyFor(owner.id()));
            if (level == null) continue;
            var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
            String title = data.getShelterName().isBlank() ? owner.name() + "的私人土地" : data.getShelterName();
            entries.add(new VisitShelterEntry(owner.id(), owner.name(), title));
        }
        Platform.sendToPlayer(player, new VisitListPacket(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
