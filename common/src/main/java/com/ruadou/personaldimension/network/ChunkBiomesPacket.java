package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientChunkBiomes;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 当前区块群系列表包（服务端 -&gt; 客户端）。
 * 群系收录器界面据此展示可勾选收录的待选群系，写入客户端缓存 {@link ClientChunkBiomes}。
 */
public record ChunkBiomesPacket(List<ResourceLocation> biomes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChunkBiomesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "chunk_biomes"));

    public static final StreamCodec<ByteBuf, ChunkBiomesPacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), ChunkBiomesPacket::biomes,
            ChunkBiomesPacket::new);

    public void handle() {
        ClientChunkBiomes.update(this.biomes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
