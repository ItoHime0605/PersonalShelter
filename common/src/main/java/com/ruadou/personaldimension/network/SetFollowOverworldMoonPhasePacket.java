package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 设置个人维度月相跟随主世界开关包（客户端 -> 服务端）。
 * 由控制器"跟随主世界"开关触发，开启后本维度月相回退到原版计算（基于主世界 dayTime），
 * 优先级高于月相循环与手动锁定。仅能影响发送者本人的维度（按 UUID 定位）。
 *
 * <p>月相仅影响服务端逻辑（怪物生成、滑冰、区域性难度等），客户端月亮纹理按 dayTime 渲染，
 * 故切换后无需额外广播。
 *
 * @param enabled true 月相跟随主世界，false 恢复独立月相
 */
public record SetFollowOverworldMoonPhasePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFollowOverworldMoonPhasePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_follow_overworld_moon_phase"));

    public static final StreamCodec<ByteBuf, SetFollowOverworldMoonPhasePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetFollowOverworldMoonPhasePacket::enabled,
            SetFollowOverworldMoonPhasePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setFollowOverworldMoonPhase(this.enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
