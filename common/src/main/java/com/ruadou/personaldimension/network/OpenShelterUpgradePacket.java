package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client asks to open; server verifies ownership and creates the menu. */
public record OpenShelterUpgradePacket() implements CustomPacketPayload {
    public static final Type<OpenShelterUpgradePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "open_shelter_upgrade"));
    public static final StreamCodec<ByteBuf, OpenShelterUpgradePacket> STREAM_CODEC =
            StreamCodec.unit(new OpenShelterUpgradePacket());

    public void handle(ServerPlayer player) {
        Platform.openShelterUpgrade(player);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
