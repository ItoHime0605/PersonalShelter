package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientSpawnRules;
import com.ruadou.personaldimension.spawn.SpawnRuleData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 生物生成规则快照包（服务端 -&gt; 客户端）。
 * 携带玩家<b>自己个人维度</b>的名单模式 / 来源范围 / 名单实体，写入客户端缓存
 * {@link ClientSpawnRules}，供生物生成控制界面展示。
 *
 * <p>枚举字段以 ordinal（VAR_INT）传输（临时线上表示，不落盘，故不涉及持久化 ordinal 隐患）。
 *
 * @param mode     名单模式 ordinal（{@link com.ruadou.personaldimension.spawn.SpawnFilterMode}）
 * @param scope    来源范围 ordinal（{@link com.ruadou.personaldimension.spawn.SpawnSourceScope}）
 * @param entities 名单中的实体注册 id 列表
 */
public record SpawnRulesPacket(int mode, int scope, List<ResourceLocation> entities)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpawnRulesPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "spawn_rules"));

    public static final StreamCodec<ByteBuf, SpawnRulesPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpawnRulesPacket::mode,
            ByteBufCodecs.VAR_INT, SpawnRulesPacket::scope,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), SpawnRulesPacket::entities,
            SpawnRulesPacket::new);

    /** 由服务端规则数据构造回传包。 */
    public static SpawnRulesPacket from(SpawnRuleData data) {
        return new SpawnRulesPacket(
            data.getMode().ordinal(), data.getScope().ordinal(), List.copyOf(data.getEntities()));
    }

    public void handle() {
        ClientSpawnRules.update(this.mode, this.scope, this.entities);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
