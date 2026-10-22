package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientCollectedBiomes;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 已收录群系列表包（服务端 -&gt; 客户端）。
 * 群系改造器界面据此展示玩家可用于覆盖的群系候选，写入客户端缓存 {@link ClientCollectedBiomes}。
 */
public record CollectedBiomesPacket(List<ResourceLocation> biomes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectedBiomesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "collected_biomes"));

    public static final StreamCodec<ByteBuf, CollectedBiomesPacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), CollectedBiomesPacket::biomes,
            CollectedBiomesPacket::new);

    public void handle() {
        ClientCollectedBiomes.update(this.biomes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
