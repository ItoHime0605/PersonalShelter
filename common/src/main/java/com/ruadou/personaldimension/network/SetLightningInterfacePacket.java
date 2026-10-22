package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.block.DimensionInterfaceService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 配置维度闪电接口方块（客户端 -&gt; 服务端）。
 * 由闪电接口界面切换"自然 / 人造"触发，携带方块坐标与目标标志。服务端
 * {@link DimensionInterfaceService#configureLightning} 复检维度归属、方块存在与交互距离后写入方块实体，
 * 权限完全由服务端裁决，无越权风险。
 *
 * @param pos       闪电接口方块坐标
 * @param natural   是否标记为自然闪电
 */
public record SetLightningInterfacePacket(BlockPos pos, boolean natural) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetLightningInterfacePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_lightning_interface"));

    public static final StreamCodec<ByteBuf, SetLightningInterfacePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLightningInterfacePacket::pos,
            ByteBufCodecs.BOOL, SetLightningInterfacePacket::natural,
            SetLightningInterfacePacket::new);

    public void handle(ServerPlayer player) {
        DimensionInterfaceService.configureLightning(player, this.pos, this.natural);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
