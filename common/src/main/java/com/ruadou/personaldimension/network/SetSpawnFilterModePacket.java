package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.spawn.SpawnFilterMode;
import com.ruadou.personaldimension.spawn.SpawnRuleData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



/**
 * 设置生物生成名单模式（客户端 -&gt; 服务端）。
 * 由生物生成控制界面切换黑/白名单触发，仅影响发送者本人维度（按 UUID 定位），无越权风险。
 * 服务端改后立即用 {@link SpawnRulesPacket} 回传最新规则以刷新界面。
 *
 * @param mode 目标模式 ordinal（{@link SpawnFilterMode}）
 */
public record SetSpawnFilterModePacket(int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSpawnFilterModePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_spawn_filter_mode"));

    public static final StreamCodec<ByteBuf, SetSpawnFilterModePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSpawnFilterModePacket::mode,
            SetSpawnFilterModePacket::new);

    public void handle(ServerPlayer player) {
        SpawnFilterMode[] values = SpawnFilterMode.values();
        if (this.mode < 0 || this.mode >= values.length) {
            return; // 非法 ordinal，忽略
        }
        ServerLevel level = PersonalDimensionService.getOwnLevel(player);
        if (level == null) {
            return;
        }
        SpawnRuleData data = SpawnRuleData.get(level);
        data.setMode(values[this.mode]);
        Platform.sendToPlayer(player, SpawnRulesPacket.from(data));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
