package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.biome.BiomeService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 收录勾选的群系（客户端 -&gt; 服务端）。
 * 由收录器界面点击"收录"触发，携带收录器方块坐标与勾选的群系 id 列表。服务端
 * {@link BiomeService#collectSelected} 复检方块存在、交互距离，并过滤掉未出现在该区块的群系后收录，
 * 权限完全由服务端裁决，无越权风险。
 *
 * @param pos    收录器方块坐标（用于定位目标区块并校验方块仍存在）
 * @param biomes 勾选的群系注册 id 列表
 */
public record CollectChunkBiomesPacket(BlockPos pos, List<ResourceLocation> biomes)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectChunkBiomesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "collect_chunk_biomes"));

    public static final StreamCodec<ByteBuf, CollectChunkBiomesPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CollectChunkBiomesPacket::pos,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), CollectChunkBiomesPacket::biomes,
            CollectChunkBiomesPacket::new);

    public void handle(ServerPlayer player) {
        BiomeService.collectSelected(player, player.serverLevel(), this.pos, this.biomes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
