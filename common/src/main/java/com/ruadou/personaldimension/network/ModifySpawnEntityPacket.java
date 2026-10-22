package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.spawn.SpawnRuleData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



/**
 * 增删生物生成名单中的一个实体（客户端 -&gt; 服务端）。
 * 由生物生成控制界面点击实体行触发，仅影响发送者本人维度（按 UUID 定位），无越权风险。
 * 服务端改后立即用 {@link SpawnRulesPacket} 回传最新规则以刷新界面选中态。
 *
 * @param entity 目标实体注册 id
 * @param add    true=加入名单，false=移出名单
 */
public record ModifySpawnEntityPacket(ResourceLocation entity, boolean add)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModifySpawnEntityPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "modify_spawn_entity"));

    public static final StreamCodec<ByteBuf, ModifySpawnEntityPacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModifySpawnEntityPacket::entity,
            ByteBufCodecs.BOOL, ModifySpawnEntityPacket::add,
            ModifySpawnEntityPacket::new);

    public void handle(ServerPlayer player) {
        ServerLevel level = PersonalDimensionService.getOwnLevel(player);
        if (level == null) {
            return;
        }
        SpawnRuleData data = SpawnRuleData.get(level);
        data.setEntity(this.entity, this.add);
        Platform.sendToPlayer(player, SpawnRulesPacket.from(data));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
