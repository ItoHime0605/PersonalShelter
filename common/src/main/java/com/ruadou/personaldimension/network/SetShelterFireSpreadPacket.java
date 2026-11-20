package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Only the owner, physically in their own land, may change the rule. */
public record SetShelterFireSpreadPacket(boolean disabled) implements CustomPacketPayload {
    public static final Type<SetShelterFireSpreadPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_shelter_fire_spread"));
    public static final StreamCodec<ByteBuf, SetShelterFireSpreadPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, SetShelterFireSpreadPacket::disabled, SetShelterFireSpreadPacket::new);
    public void handle(ServerPlayer player) {
        var level = player.serverLevel();
        if (!level.dimension().equals(Platform.levelKeyFor(player.getUUID()))) {
            player.displayClientMessage(Component.literal("請先進入自己的私人土地。"), true);
            return;
        }
        level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID).setFireSpreadDisabled(disabled);
        new RequestDimensionInfoPacket().handle(player);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
