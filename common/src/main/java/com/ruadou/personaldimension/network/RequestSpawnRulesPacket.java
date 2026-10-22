package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.spawn.SpawnRuleData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



/**
 * 请求本人个人维度的生物生成规则（客户端 -&gt; 服务端）。
 * 生物生成控制界面打开与周期刷新时发送，服务端以发送者 UUID 定位其维度后用
 * {@link SpawnRulesPacket} 回传（维度未创建则静默不回传）。
 */
public record RequestSpawnRulesPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestSpawnRulesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_spawn_rules"));

    public static final StreamCodec<ByteBuf, RequestSpawnRulesPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestSpawnRulesPacket());

    public void handle(ServerPlayer player) {
        ServerLevel level = PersonalDimensionService.getOwnLevel(player);
        if (level == null) {
            return; // 维度未创建，无规则可回传
        }
        Platform.sendToPlayer(player, SpawnRulesPacket.from(SpawnRuleData.get(level)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
