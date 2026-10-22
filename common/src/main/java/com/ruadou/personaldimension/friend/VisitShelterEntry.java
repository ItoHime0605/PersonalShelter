package com.ruadou.personaldimension.friend;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.List;
import java.util.UUID;

/** Owner identity and player-visible shelter title for the visit list. */
public record VisitShelterEntry(UUID id, String ownerName, String shelterName) {
    public static final StreamCodec<ByteBuf, VisitShelterEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override public VisitShelterEntry decode(ByteBuf buf) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            return new VisitShelterEntry(fb.readUUID(), fb.readUtf(64), fb.readUtf(64));
        }
        @Override public void encode(ByteBuf buf, VisitShelterEntry entry) {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            fb.writeUUID(entry.id());
            fb.writeUtf(entry.ownerName(), 64);
            fb.writeUtf(entry.shelterName(), 64);
        }
    };
    public static final StreamCodec<ByteBuf, List<VisitShelterEntry>> LIST_STREAM_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list());
}
