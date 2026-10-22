package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.spawn.SpawnRuleData;
import com.ruadou.personaldimension.spawn.SpawnSourceScope;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



/**
 * 设置生物生成拦截来源范围（客户端 -&gt; 服务端）。
 * 由生物生成控制界面切换"全部来源 / 仅自然刷怪"触发，仅影响发送者本人维度，无越权风险。
 * 服务端改后立即用 {@link SpawnRulesPacket} 回传最新规则以刷新界面。
 *
 * @param scope 目标来源范围 ordinal（{@link SpawnSourceScope}）
 */
public record SetSpawnSourceScopePacket(int scope) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSpawnSourceScopePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_spawn_source_scope"));

    public static final StreamCodec<ByteBuf, SetSpawnSourceScopePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSpawnSourceScopePacket::scope,
            SetSpawnSourceScopePacket::new);

    public void handle(ServerPlayer player) {
        SpawnSourceScope[] values = SpawnSourceScope.values();
        if (this.scope < 0 || this.scope >= values.length) {
            return; // 非法 ordinal，忽略
        }
        ServerLevel level = PersonalDimensionService.getOwnLevel(player);
        if (level == null) {
            return;
        }
        SpawnRuleData data = SpawnRuleData.get(level);
        data.setScope(values[this.scope]);
        Platform.sendToPlayer(player, SpawnRulesPacket.from(data));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
