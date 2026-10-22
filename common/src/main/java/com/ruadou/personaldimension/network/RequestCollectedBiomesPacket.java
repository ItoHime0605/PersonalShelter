package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.biome.CollectedBiomeData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



/**
 * 请求本人已收录群系列表（客户端 -&gt; 服务端）。
 * 群系改造器界面打开与周期刷新时发送，服务端以发送者 UUID 查询后用 {@link CollectedBiomesPacket} 回传。
 */
public record RequestCollectedBiomesPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCollectedBiomesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_collected_biomes"));

    public static final StreamCodec<ByteBuf, RequestCollectedBiomesPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestCollectedBiomesPacket());

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        List<ResourceLocation> biomes =
            List.copyOf(CollectedBiomeData.get(server).getBiomes(player.getUUID()));
        Platform.sendToPlayer(player, new CollectedBiomesPacket(biomes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
