package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.biome.BiomeService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 应用群系改造（客户端 -&gt; 服务端）。
 * 由改造器界面点击"改造"触发，携带改造器方块坐标与选中群系 id。服务端
 * {@link BiomeService#applyBiome} 复检维度归属、方块存在、交互距离与收录状态后覆盖该区块群系，
 * 权限完全由服务端裁决，无越权风险。
 *
 * @param pos   改造器方块坐标（用于定位目标区块并校验方块仍存在）
 * @param biome 选中群系的注册 id
 */
public record ApplyBiomePacket(BlockPos pos, ResourceLocation biome) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ApplyBiomePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "apply_biome"));

    public static final StreamCodec<ByteBuf, ApplyBiomePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ApplyBiomePacket::pos,
            ResourceLocation.STREAM_CODEC, ApplyBiomePacket::biome,
            ApplyBiomePacket::new);

    public void handle(ServerPlayer player) {
        BiomeService.applyBiome(player, this.pos, this.biome);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
