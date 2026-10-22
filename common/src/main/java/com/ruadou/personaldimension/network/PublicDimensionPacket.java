package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 公共维度操作包（客户端 -> 服务端）。
 *
 * <p>三种动作：
 * <ul>
 *   <li>{@link Action#ENTER} - 进入公共维度（未创建则提示）；</li>
 *   <li>{@link Action#EXIT} - 退出公共维度（返回进入前的 outside 记忆点）；</li>
 *   <li>{@link Action#DELETE} - 删除公共维度（仅 OP 可执行）。</li>
 * </ul>
 * 创建公共维度走 {@link OpenCreateMenuPacket}（与个人维度共用创建容器）。
 */
public record PublicDimensionPacket(Action action) implements CustomPacketPayload {

    public enum Action {
        ENTER, EXIT, DELETE
    }

    public static final CustomPacketPayload.Type<PublicDimensionPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "public_dimension"));

    public static final StreamCodec<FriendlyByteBuf, PublicDimensionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal),
            PublicDimensionPacket::action,
            PublicDimensionPacket::new);

    public void handle(ServerPlayer player) {
        switch (action) {
            case ENTER -> PersonalDimensionService.teleportToPublic(player);
            case EXIT -> PersonalDimensionService.teleportOutOfPublic(player);
            case DELETE -> PersonalDimensionService.deletePublicDimension(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
