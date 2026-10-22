package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.biome.BiomeService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;



/**
 * 请求收录器方块所在区块内出现的群系列表（客户端 -&gt; 服务端）。
 * 群系收录器界面打开与周期刷新时发送，服务端复检位置/距离后用 {@link ChunkBiomesPacket} 回传。
 *
 * @param pos 收录器方块坐标（用于定位目标区块并校验方块仍存在）
 */
public record RequestChunkBiomesPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestChunkBiomesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_chunk_biomes"));

    public static final StreamCodec<ByteBuf, RequestChunkBiomesPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestChunkBiomesPacket::pos,
            RequestChunkBiomesPacket::new);

    public void handle(ServerPlayer player) {
        List<ResourceLocation> biomes =
            BiomeService.chunkBiomesAt(player, player.serverLevel(), this.pos);
        Platform.sendToPlayer(player, new ChunkBiomesPacket(biomes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
