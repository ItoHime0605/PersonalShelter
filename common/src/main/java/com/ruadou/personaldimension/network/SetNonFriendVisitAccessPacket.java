package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.platform.Platform;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Owner-only switch for allowing non-friends to visit a personal shelter. */
public record SetNonFriendVisitAccessPacket(boolean open) implements CustomPacketPayload {
    public static final Type<SetNonFriendVisitAccessPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_non_friend_visit_access"));

    public static final StreamCodec<ByteBuf, SetNonFriendVisitAccessPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL,
            SetNonFriendVisitAccessPacket::open, SetNonFriendVisitAccessPacket::new);

    public void handle(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Platform.levelKeyFor(player.getUUID()))) {
            player.displayClientMessage(Component.literal("請先進入自己的私人土地，再設定非好友參觀權限。"), true);
            return;
        }

        PersonalDimensionData data =
            level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        data.setOpenToNonFriends(this.open);

        // 關閉公開參觀時，立即移除已在場且沒有好友 ENTER 權限的訪客。
        if (!this.open) {
            PersonalDimensionService.evictUnauthorizedVisitors(player.getServer(), player.getUUID());
        }

        PersonalDimensionService.sendDimensionInfoToPlayer(player);
        player.displayClientMessage(
            Component.literal("非好友參觀：" + (this.open ? "開啟" : "關閉")), true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
