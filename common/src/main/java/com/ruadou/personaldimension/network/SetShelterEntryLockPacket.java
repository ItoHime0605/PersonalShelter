package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Owner-only server-validated entry-point lock toggle. */
public record SetShelterEntryLockPacket(boolean locked) implements CustomPacketPayload {
    public static final Type<SetShelterEntryLockPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_shelter_entry_lock"));
    public static final StreamCodec<ByteBuf, SetShelterEntryLockPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, SetShelterEntryLockPacket::locked, SetShelterEntryLockPacket::new);

    public void handle(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Platform.levelKeyFor(player.getUUID()))) {
            player.displayClientMessage(Component.literal("請先進入自己的私人土地，再設定進入點。"), true);
            return;
        }
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        if (!locked) {
            data.unlockShelterEntry();
            player.displayClientMessage(Component.literal("進入點鎖定：否"), true);
            return;
        }
        var feet = player.blockPosition();
        if (!com.ruadou.personaldimension.shelter.ShelterUpgradeRules.inside(data.getShelterLevel(), feet.getX(), feet.getZ())
            || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            || level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
            player.displayClientMessage(Component.literal("此處不是安全的進入點，請站在穩固地面上。"), true);
            return;
        }
        data.lockShelterEntry(feet, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.literal("進入點鎖定：是（已記錄目前位置與面向）"), true);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
